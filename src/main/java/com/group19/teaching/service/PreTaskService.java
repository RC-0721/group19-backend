package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
