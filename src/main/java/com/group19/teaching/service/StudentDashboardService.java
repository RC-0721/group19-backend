package com.group19.teaching.service;

import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StudentDashboardService {

    private final JdbcTemplate jdbcTemplate;

    public StudentDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> load(User actor) {
        String studentId = actor.getAccount();
        List<Map<String, Object>> profileRows = studentProfile(studentId);
        if (profileRows.isEmpty()) {
            return dashboard(List.of(), List.of(), defaultPracticeSummary(), defaultProjectSummary(),
                    defaultInterviewSummary(), defaultProfileSummary());
        }

        Map<String, Object> profile = profileRows.get(0);
        String classId = stringValue(profile.get("class_id"));
        List<Map<String, Object>> todos = todoTasks(studentId, classId);
        List<Map<String, Object>> courses = courses(studentId, todos);
        return dashboard(courses, todos, practiceSummary(studentId), projectSummary(studentId),
                interviewSummary(studentId), profileSummary(studentId, profile));
    }

    private Map<String, Object> dashboard(
            List<Map<String, Object>> courses,
            List<Map<String, Object>> todos,
            Map<String, Object> practiceSummary,
            Map<String, Object> projectSummary,
            Map<String, Object> interviewSummary,
            Map<String, Object> profileSummary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courses", courses);
        result.put("todo_tasks", todos);
        result.put("practice_summary", practiceSummary);
        result.put("project_summary", projectSummary);
        result.put("interview_summary", interviewSummary);
        result.put("profile_summary", profileSummary);
        return result;
    }

    private List<Map<String, Object>> studentProfile(String studentId) {
        return jdbcTemplate.queryForList("""
                SELECT sp.student_id, sp.class_id, sp.target_job_id, jd.job_name
                FROM student_profile sp
                LEFT JOIN job_direction jd ON sp.target_job_id = jd.job_id
                WHERE sp.student_id = ?
                LIMIT 1
                """, studentId);
    }

    private List<Map<String, Object>> courses(String studentId, List<Map<String, Object>> todos) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT c.course_id, cc.course_class_id, c.course_name, c.course_type, cc.semester, cc.status
                FROM student_profile sp
                JOIN course_class cc ON sp.class_id = cc.class_id
                JOIN course c ON cc.course_id = c.course_id
                WHERE sp.student_id = ? AND c.status = '已发布'
                ORDER BY c.course_id, cc.course_class_id
                """, studentId);
        List<Map<String, Object>> courses = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> course = new LinkedHashMap<>(row);
            String courseId = stringValue(row.get("course_id"));
            String courseClassId = stringValue(row.get("course_class_id"));
            course.put("progress", progress(studentId, courseId, courseClassId));
            course.put("next_task", nextTaskTitle(courseId, todos));
            courses.add(course);
        }
        return courses;
    }

    private int progress(String studentId, String courseId, String courseClassId) {
        int total = intValue(firstValue(jdbcTemplate.queryForList("""
                SELECT
                  (SELECT COUNT(*) FROM pre_task WHERE course_class_id = ? AND status = '已发布')
                + (SELECT COUNT(*) FROM homework WHERE course_class_id = ? AND status = '已发布')
                + (SELECT COUNT(*) FROM project_task WHERE course_id = ? AND status = '已发布') AS value_count
                """, courseClassId, courseClassId, courseId)));
        if (total == 0) {
            return 0;
        }
        int finished = intValue(firstValue(jdbcTemplate.queryForList("""
                SELECT
                  (SELECT COUNT(DISTINCT pts.pre_task_id)
                   FROM pre_task_submit pts
                   JOIN pre_task pt ON pts.pre_task_id = pt.pre_task_id
                   WHERE pts.student_id = ? AND pt.course_class_id = ? AND pt.status = '已发布')
                + (SELECT COUNT(DISTINCT hs.homework_id)
                   FROM homework_submit hs
                   JOIN homework h ON hs.homework_id = h.homework_id
                   WHERE hs.student_id = ? AND h.course_class_id = ? AND h.status = '已发布')
                + (SELECT COUNT(DISTINCT ps.project_task_id)
                   FROM project_submission ps
                   JOIN project_task pt ON ps.project_task_id = pt.project_task_id
                   WHERE ps.student_id = ? AND pt.course_id = ? AND pt.status = '已发布') AS value_count
                """, studentId, courseClassId, studentId, courseClassId, studentId, courseId)));
        return (int) Math.round(finished * 100.0 / total);
    }

    private String nextTaskTitle(String courseId, List<Map<String, Object>> todos) {
        return todos.stream()
                .filter(todo -> courseId.equals(stringValue(todo.get("course_id"))))
                .map(todo -> stringValue(todo.get("title")))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private List<Map<String, Object>> todoTasks(String studentId, String classId) {
        List<Map<String, Object>> todos = new ArrayList<>();
        todos.addAll(jdbcTemplate.queryForList("""
                SELECT pt.pre_task_id AS task_id, 'PRE_TASK' AS task_type, pt.title, pt.deadline,
                       pt.course_id, pt.course_class_id
                FROM pre_task pt
                LEFT JOIN pre_task_submit pts ON pt.pre_task_id = pts.pre_task_id AND pts.student_id = ?
                WHERE pt.course_class_id IN (
                    SELECT course_class_id FROM course_class WHERE class_id = ?
                )
                  AND pt.status = '已发布' AND pts.submit_id IS NULL
                ORDER BY pt.deadline, pt.pre_task_id
                """, studentId, classId));
        todos.addAll(jdbcTemplate.queryForList("""
                SELECT h.homework_id AS task_id, 'HOMEWORK' AS task_type, h.title, h.deadline,
                       h.course_id, h.course_class_id
                FROM homework h
                LEFT JOIN homework_submit hs ON h.homework_id = hs.homework_id AND hs.student_id = ?
                WHERE h.course_class_id IN (
                    SELECT course_class_id FROM course_class WHERE class_id = ?
                )
                  AND h.status = '已发布' AND hs.submit_id IS NULL
                ORDER BY h.deadline, h.homework_id
                """, studentId, classId));
        todos.addAll(jdbcTemplate.queryForList("""
                SELECT pt.project_task_id AS task_id, 'PROJECT' AS task_type, pt.title, NULL AS deadline,
                       pt.course_id, cc.course_class_id
                FROM project_task pt
                JOIN course_class cc ON pt.course_id = cc.course_id
                LEFT JOIN project_submission ps ON pt.project_task_id = ps.project_task_id AND ps.student_id = ?
                WHERE cc.class_id = ? AND pt.status = '已发布' AND ps.submission_id IS NULL
                ORDER BY pt.project_task_id
                """, studentId, classId));
        todos.sort(Comparator
                .comparing((Map<String, Object> row) -> deadlineValue(row.get("deadline")),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> stringValue(row.get("task_id"))));
        return todos.size() > 10 ? new ArrayList<>(todos.subList(0, 10)) : todos;
    }

    private Map<String, Object> practiceSummary(String studentId) {
        Map<String, Object> stat = firstRow(jdbcTemplate.queryForList("""
                SELECT COUNT(*) AS finished_count,
                       COALESCE(ROUND(AVG(CASE WHEN is_correct THEN 100 ELSE 0 END)), 0) AS accuracy
                FROM practice_record
                WHERE student_id = ?
                """, studentId));
        String weakPoint = "";
        List<Map<String, Object>> weakRows = jdbcTemplate.queryForList("""
                SELECT kp.name AS weak_point
                FROM wrong_book wb
                JOIN question_knowledge_relation qkr ON wb.question_id = qkr.question_id
                JOIN knowledge_point kp ON qkr.knowledge_id = kp.knowledge_id
                WHERE wb.student_id = ?
                ORDER BY wb.last_wrong_time DESC, wb.wrong_id
                LIMIT 1
                """, studentId);
        if (!weakRows.isEmpty()) {
            weakPoint = stringValue(weakRows.get(0).get("weak_point"));
        }
        return Map.of(
                "finished_count", intValue(stat.get("finished_count")),
                "accuracy", intValue(stat.get("accuracy")),
                "weak_point", weakPoint
        );
    }

    private Map<String, Object> defaultPracticeSummary() {
        return Map.of("finished_count", 0, "accuracy", 0, "weak_point", "");
    }

    private Map<String, Object> projectSummary(String studentId) {
        Map<String, Object> stat = firstRow(jdbcTemplate.queryForList("""
                SELECT COUNT(*) AS submitted_count
                FROM project_submission
                WHERE student_id = ?
                """, studentId));
        String latestStatus = "";
        List<Map<String, Object>> latestRows = jdbcTemplate.queryForList("""
                SELECT submit_status
                FROM project_submission
                WHERE student_id = ?
                ORDER BY submit_time DESC, submission_id
                LIMIT 1
                """, studentId);
        if (!latestRows.isEmpty()) {
            latestStatus = stringValue(latestRows.get(0).get("submit_status"));
        }
        return Map.of("submitted_count", intValue(stat.get("submitted_count")), "latest_status", latestStatus);
    }

    private Map<String, Object> defaultProjectSummary() {
        return Map.of("submitted_count", 0, "latest_status", "");
    }

    private Map<String, Object> interviewSummary(String studentId) {
        Map<String, Object> stat = firstRow(jdbcTemplate.queryForList("""
                SELECT COUNT(*) AS completed_count
                FROM ai_session
                WHERE student_id = ? AND status = '已完成'
                """, studentId));
        int latestScore = 0;
        List<Map<String, Object>> latestRows = jdbcTemplate.queryForList("""
                SELECT r.score
                FROM ai_interview_report r
                JOIN ai_session s ON r.session_id = s.session_id
                WHERE s.student_id = ?
                ORDER BY s.created_time DESC, r.report_id
                LIMIT 1
                """, studentId);
        if (!latestRows.isEmpty()) {
            latestScore = intValue(latestRows.get(0).get("score"));
        }
        return Map.of("completed_count", intValue(stat.get("completed_count")), "latest_score", latestScore);
    }

    private Map<String, Object> defaultInterviewSummary() {
        return Map.of("completed_count", 0, "latest_score", 0);
    }

    private Map<String, Object> profileSummary(String studentId, Map<String, Object> profile) {
        String targetJobId = stringValue(profile.get("target_job_id"));
        String targetJob = stringValue(profile.get("job_name"));
        if (!StringUtils.hasText(targetJob)) {
            targetJob = targetJobId;
        }
        String profileStatus = "数据不足";
        List<Map<String, Object>> profileRows = jdbcTemplate.queryForList("""
                SELECT profile_id, profile_status
                FROM ability_profile
                WHERE student_id = ? AND job_id = ?
                ORDER BY updated_time DESC, profile_id
                LIMIT 1
                """, studentId, targetJobId);
        String profileId = "";
        if (!profileRows.isEmpty()) {
            profileId = stringValue(profileRows.get(0).get("profile_id"));
            profileStatus = stringValue(profileRows.get(0).get("profile_status"));
        }

        String recommendation = "暂无学习建议";
        if (StringUtils.hasText(profileId)) {
            List<Map<String, Object>> recommendationRows = jdbcTemplate.queryForList("""
                    SELECT recommend_content
                    FROM learning_recommendation
                    WHERE student_id = ? AND profile_id = ?
                    ORDER BY created_time DESC, recommend_id
                    LIMIT 1
                    """, studentId, profileId);
            if (!recommendationRows.isEmpty()) {
                recommendation = stringValue(recommendationRows.get(0).get("recommend_content"));
            }
        }
        return Map.of("profile_status", profileStatus, "target_job", targetJob, "recommendation", recommendation);
    }

    private Map<String, Object> defaultProfileSummary() {
        return Map.of("profile_status", "数据不足", "target_job", "", "recommendation", "暂无学习建议");
    }

    private Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Object firstValue(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        Map<String, Object> row = rows.get(0);
        return row.isEmpty() ? 0 : row.values().iterator().next();
    }

    private LocalDateTime deadlineValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return (int) Math.round(Double.parseDouble(text.trim()));
            } catch (RuntimeException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
