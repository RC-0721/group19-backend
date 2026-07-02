package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PreTaskService {

    private final JdbcTemplate jdbcTemplate;

    public PreTaskService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(
            String courseClassId,
            String status,
            Integer pageNo,
            Integer pageSize,
            User actor) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (StringUtils.hasText(courseClassId)) {
            requireCourseClassAccess(courseClassId, actor);
        }
        List<Object> params = new ArrayList<>();
        String where = buildListWhere(courseClassId, status, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pre_task pt JOIN course_class cc ON pt.course_class_id = cc.course_class_id " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT pt.pre_task_id, pt.course_class_id, pt.course_id, pt.title, pt.material_id,
                       pt.deadline, pt.status, cc.class_id, cc.teacher_id
                FROM pre_task pt
                JOIN course_class cc ON pt.course_class_id = cc.course_class_id
                """ + where + """
                ORDER BY pt.deadline DESC, pt.pre_task_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of("records", records, "total", total == null ? 0 : total,
                "page_no", pageNo, "page_size", pageSize);
    }

    public Map<String, Object> listSubmits(String preTaskId, Integer pageNo, Integer pageSize, User actor) {
        if (!StringUtils.hasText(preTaskId) || pageNo == null || pageNo < 1
                || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireTeacherPreTask(preTaskId, actor.getAccount());
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pre_task_submit
                WHERE pre_task_id = ?
                """, Integer.class, preTaskId);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT submit_id, pre_task_id, student_id, submit_content, base_score, weak_points, submit_time
                FROM pre_task_submit
                WHERE pre_task_id = ?
                ORDER BY submit_time DESC, submit_id
                LIMIT ? OFFSET ?
                """, preTaskId, pageSize, (pageNo - 1) * pageSize);
        return Map.of("records", records, "total", total == null ? 0 : total,
                "page_no", pageNo, "page_size", pageSize);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request, User actor) {
        String courseClassId = stringValue(request.get("course_class_id"));
        String title = stringValue(request.get("title"));
        String materialId = stringValue(request.get("material_id"));
        String status = stringValue(request.get("status"));
        LocalDateTime deadline = parseDeadline(request.get("deadline"));
        if (!StringUtils.hasText(courseClassId) || !StringUtils.hasText(title)
                || !StringUtils.hasText(materialId) || deadline == null
                || !List.of("草稿", "已发布").contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String courseId = requireTeacherCourse(courseClassId, actor.getAccount());
        String preTaskId = "pre-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pre_task (pre_task_id, task_id, course_class_id, course_id, title, material_id, deadline, task_type, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, preTaskId, preTaskId, courseClassId, courseId, title, materialId, Timestamp.valueOf(deadline), "课前任务", status);
        return Map.of("pre_task_id", preTaskId, "status", status);
    }

    @Transactional
    public Map<String, Object> submit(String preTaskId, Map<String, Object> request, User actor) {
        String submitContent = stringValue(request.get("submit_content"));
        Object answerList = request.get("answer_list");
        if (!StringUtils.hasText(preTaskId) || !StringUtils.hasText(submitContent) || !(answerList instanceof List<?>)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT pre_task_id, status, deadline
                FROM pre_task
                WHERE pre_task_id = ?
                LIMIT 1
                """, preTaskId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> task = rows.get(0);
        if (!"已发布".equals(stringValue(task.get("status"))) || LocalDateTime.now().isAfter(toLocalDateTime(task.get("deadline")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }

        String submitId = "pre-submit-" + UUID.randomUUID();
        double baseScore = 60.0;
        String weakPoints = "";
        LocalDateTime submitTime = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO pre_task_submit (submit_id, pre_task_id, student_id, submit_content, base_score, weak_points, submit_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, submitId, preTaskId, actor.getAccount(), submitContent, baseScore, weakPoints, Timestamp.valueOf(submitTime));
        return Map.of(
                "submit_id", submitId,
                "base_score", baseScore,
                "weak_points", weakPoints,
                "submit_time", submitTime
        );
    }

    private String requireTeacherCourse(String courseClassId, String teacherId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT course_id
                FROM course_class
                WHERE course_class_id = ? AND teacher_id = ?
                LIMIT 1
                """, courseClassId, teacherId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return stringValue(rows.get(0).get("course_id"));
    }

    private String buildListWhere(String courseClassId, String status, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                      SELECT 1 FROM student_profile sp
                      WHERE sp.student_id = ? AND sp.class_id = cc.class_id
                    )
                    """);
            params.add(actor.getAccount());
        } else if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("AND cc.teacher_id = ?\n");
            params.add(actor.getAccount());
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        append(where, params, "pt.course_class_id", courseClassId);
        append(where, params, "pt.status", status);
        return where.toString();
    }

    private void requireCourseClassAccess(String courseClassId, User actor) {
        Integer count;
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM course_class cc
                    JOIN student_profile sp ON cc.class_id = sp.class_id
                    WHERE cc.course_class_id = ? AND sp.student_id = ?
                    """, Integer.class, courseClassId, actor.getAccount());
        } else if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM course_class
                    WHERE course_class_id = ? AND teacher_id = ?
                    """, Integer.class, courseClassId, actor.getAccount());
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireTeacherPreTask(String preTaskId, String teacherId) {
        List<Map<String, Object>> exists = jdbcTemplate.queryForList("""
                SELECT pre_task_id
                FROM pre_task
                WHERE pre_task_id = ?
                LIMIT 1
                """, preTaskId);
        if (exists.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pre_task pt
                JOIN course_class cc ON pt.course_class_id = cc.course_class_id
                WHERE pt.pre_task_id = ? AND cc.teacher_id = ?
                """, Integer.class, preTaskId, teacherId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
        }
    }

    private LocalDateTime parseDeadline(Object value) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return LocalDateTime.parse(text.trim().replace(" ", "T"));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
