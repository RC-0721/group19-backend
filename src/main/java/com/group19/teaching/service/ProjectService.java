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
public class ProjectService {

    private final JdbcTemplate jdbcTemplate;

    public ProjectService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> createStandard(Map<String, Object> request) {
        String jobId = stringValue(request.get("job_id"));
        String evaluationDimension = stringValue(request.get("evaluation_dimension"));
        String scoreLevel = stringValue(request.get("score_level"));
        String evidenceRequirement = stringValue(request.get("evidence_requirement"));
        if (!StringUtils.hasText(jobId) || !StringUtils.hasText(evaluationDimension)
                || !StringUtils.hasText(scoreLevel) || !StringUtils.hasText(evidenceRequirement)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireExists("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", jobId);
        String standardId = "standard-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO enterprise_project_standard
                  (standard_id, job_id, evaluation_dimension, score_level, evidence_requirement)
                VALUES (?, ?, ?, ?, ?)
                """, standardId, jobId, evaluationDimension, scoreLevel, evidenceRequirement);
        return Map.of("standard_id", standardId);
    }

    @Transactional
    public Map<String, Object> createRubricDimension(String standardId, Map<String, Object> request) {
        String dimensionName = stringValue(request.get("dimension_name"));
        Double weight = doubleValue(request.get("weight"));
        String levelRule = stringValue(request.get("level_rule"));
        if (!StringUtils.hasText(standardId) || !StringUtils.hasText(dimensionName)
                || weight == null || weight <= 0 || weight > 1 || !StringUtils.hasText(levelRule)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireExists("SELECT standard_id FROM enterprise_project_standard WHERE standard_id = ? LIMIT 1", standardId);
        String dimensionId = "rubric-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rubric_dimension (dimension_id, standard_id, dimension_name, weight, level_rule)
                VALUES (?, ?, ?, ?, ?)
                """, dimensionId, standardId, dimensionName, weight, levelRule);
        return Map.of("dimension_id", dimensionId, "standard_id", standardId);
    }

    @Transactional
    public Map<String, Object> createProject(Map<String, Object> request, User actor) {
        String courseId = stringValue(request.get("course_id"));
        String jobId = stringValue(request.get("job_id"));
        String taskGoal = stringValue(request.get("task_goal"));
        String techRequirement = stringValue(request.get("tech_requirement"));
        String deliverable = stringValue(request.get("deliverable"));
        String status = stringValue(request.get("status"));
        if (!StringUtils.hasText(courseId) || !StringUtils.hasText(jobId) || !StringUtils.hasText(taskGoal)
                || !StringUtils.hasText(techRequirement) || !StringUtils.hasText(deliverable)
                || !List.of("草稿", "已发布").contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireTeacherCourse(courseId, actor.getAccount());
        requireExists("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", jobId);
        String projectTaskId = "project-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO project_task
                  (project_task_id, course_id, job_id, title, task_goal, tech_requirement, deliverable, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, projectTaskId, courseId, jobId, taskGoal, taskGoal, techRequirement, deliverable, status);
        return Map.of("project_task_id", projectTaskId, "status", status);
    }

    @Transactional
    public Map<String, Object> submitProject(String projectTaskId, Map<String, Object> request, User actor) {
        String artifactPath = stringValue(request.get("artifact_path"));
        String description = stringValue(request.get("description"));
        if (!StringUtils.hasText(projectTaskId) || !StringUtils.hasText(artifactPath)
                || !StringUtils.hasText(description)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> task = requirePublishedProject(projectTaskId);
        String submissionId = "project-submit-" + UUID.randomUUID();
        String evaluationId = "project-eval-" + UUID.randomUUID();
        String submitStatus = "待评价";
        LocalDateTime submitTime = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO project_submission
                  (submission_id, project_task_id, student_id, artifact_path, description, submit_status, submit_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, submissionId, projectTaskId, actor.getAccount(), artifactPath, description,
                submitStatus, Timestamp.valueOf(submitTime));
        jdbcTemplate.update("""
                INSERT INTO project_evaluation (evaluation_id, submission_id, rubric_id, ai_evaluation)
                VALUES (?, ?, ?, ?)
                """, evaluationId, submissionId, findStandardId(stringValue(task.get("job_id"))), "待教师确认");
        return Map.of("submission_id", submissionId, "submit_status", submitStatus, "submit_time", submitTime);
    }

    public Map<String, Object> listSubmissions(
            String projectTaskId,
            String submitStatus,
            Integer pageNo,
            Integer pageSize,
            User actor) {
        if (!StringUtils.hasText(projectTaskId) || pageNo == null || pageNo < 1
                || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireTeacherProject(projectTaskId, actor.getAccount());
        List<Object> params = new ArrayList<>();
        params.add(projectTaskId);
        String where = "WHERE ps.project_task_id = ?\n";
        if (StringUtils.hasText(submitStatus)) {
            where += "AND ps.submit_status = ?\n";
            params.add(submitStatus);
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_submission ps " + where,
                Integer.class,
                params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT ps.submission_id, ps.project_task_id, ps.student_id, ps.artifact_path, ps.description,
                       ps.submit_status, ps.submit_time, pe.evaluation_id, pe.rubric_id, pe.ai_evaluation,
                       pe.teacher_score, pe.teacher_comment, pe.confirmed_time
                FROM project_submission ps
                LEFT JOIN project_evaluation pe ON ps.submission_id = pe.submission_id
                """ + where + """
                ORDER BY ps.submit_time DESC, ps.submission_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of("records", records, "total", total == null ? 0 : total,
                "page_no", pageNo, "page_size", pageSize);
    }

    @Transactional
    public Map<String, Object> confirmEvaluation(String evaluationId, Map<String, Object> request, User actor) {
        Double teacherScore = doubleValue(request.get("teacher_score"));
        String teacherComment = stringValue(request.get("teacher_comment"));
        if (!StringUtils.hasText(evaluationId) || teacherScore == null || teacherScore < 0 || teacherScore > 100
                || !StringUtils.hasText(teacherComment)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT pe.evaluation_id, pe.teacher_score, ps.submission_id, ps.student_id, pt.project_task_id
                FROM project_evaluation pe
                JOIN project_submission ps ON pe.submission_id = ps.submission_id
                JOIN project_task pt ON ps.project_task_id = pt.project_task_id
                JOIN course_class cc ON pt.course_id = cc.course_id
                WHERE pe.evaluation_id = ? AND cc.teacher_id = ?
                LIMIT 1
                """, evaluationId, actor.getAccount());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Map<String, Object> row = rows.get(0);
        if (row.get("teacher_score") != null) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        LocalDateTime confirmedTime = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE project_evaluation
                SET teacher_score = ?, teacher_comment = ?, confirmed_time = ?
                WHERE evaluation_id = ?
                """, teacherScore, teacherComment, Timestamp.valueOf(confirmedTime), evaluationId);
        jdbcTemplate.update("""
                UPDATE project_submission
                SET submit_status = ?
                WHERE submission_id = ?
                """, "已反馈", row.get("submission_id"));
        jdbcTemplate.update("""
                INSERT INTO ability_evidence (evidence_id, student_id, source_type, source_id, score)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE score = VALUES(score)
                """, "evidence-" + UUID.randomUUID(), row.get("student_id"), "PROJECT", evaluationId, teacherScore);
        return Map.of("evaluation_id", evaluationId, "teacher_score", teacherScore, "confirmed_time", confirmedTime);
    }

    private Map<String, Object> requirePublishedProject(String projectTaskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT project_task_id, job_id, status
                FROM project_task
                WHERE project_task_id = ?
                LIMIT 1
                """, projectTaskId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> task = rows.get(0);
        if (!"已发布".equals(stringValue(task.get("status")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        return task;
    }

    private void requireTeacherCourse(String courseId, String teacherId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT course_id
                FROM course_class
                WHERE course_id = ? AND teacher_id = ?
                LIMIT 1
                """, courseId, teacherId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireTeacherProject(String projectTaskId, String teacherId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT pt.project_task_id
                FROM project_task pt
                JOIN course_class cc ON pt.course_id = cc.course_id
                WHERE pt.project_task_id = ? AND cc.teacher_id = ?
                LIMIT 1
                """, projectTaskId, teacherId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireExists(String sql, String id) {
        if (jdbcTemplate.queryForList(sql, id).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private String findStandardId(String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT standard_id
                FROM enterprise_project_standard
                WHERE job_id = ?
                ORDER BY standard_id
                LIMIT 1
                """, jobId);
        return rows.isEmpty() ? null : stringValue(rows.get(0).get("standard_id"));
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
