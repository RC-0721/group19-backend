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
public class HomeworkService {

    private final JdbcTemplate jdbcTemplate;

    public HomeworkService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request, User actor) {
        String courseClassId = stringValue(request.get("course_class_id"));
        String title = stringValue(request.get("title"));
        String submitRequirement = stringValue(request.get("submit_requirement"));
        String scoringStandard = stringValue(request.get("scoring_standard"));
        String status = stringValue(request.get("status"));
        LocalDateTime deadline = parseDateTime(request.get("deadline"));
        if (!StringUtils.hasText(courseClassId) || !StringUtils.hasText(title)
                || !StringUtils.hasText(submitRequirement) || !StringUtils.hasText(scoringStandard)
                || deadline == null || !List.of("草稿", "已发布").contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String courseId = requireTeacherCourse(courseClassId, actor.getAccount());
        String homeworkId = "hw-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO homework (homework_id, course_id, course_class_id, title, submit_requirement, scoring_standard, deadline, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, homeworkId, courseId, courseClassId, title, submitRequirement, scoringStandard,
                Timestamp.valueOf(deadline), status);
        return Map.of("homework_id", homeworkId, "status", status);
    }

    @Transactional
    public Map<String, Object> submit(String homeworkId, Map<String, Object> request, User actor) {
        String submitContent = stringValue(request.get("submit_content"));
        String attachmentPath = stringValue(request.get("attachment_path"));
        if (!StringUtils.hasText(homeworkId) || !StringUtils.hasText(submitContent)
                || !StringUtils.hasText(attachmentPath)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> homework = requireOpenHomework(homeworkId);
        String submitId = "hw-submit-" + UUID.randomUUID();
        String reviewId = "hw-review-" + UUID.randomUUID();
        String submitStatus = "待批改";
        LocalDateTime submitTime = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO homework_submit (submit_id, homework_id, student_id, submit_content, attachment_path, submit_status, submit_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, submitId, homework.get("homework_id"), actor.getAccount(), submitContent, attachmentPath,
                submitStatus, Timestamp.valueOf(submitTime));
        jdbcTemplate.update("""
                INSERT INTO homework_review (review_id, submit_id, ai_score, ai_comment)
                VALUES (?, ?, ?, ?)
                """, reviewId, submitId, 60.0, "待教师确认");
        return Map.of("submit_id", submitId, "submit_status", submitStatus, "submit_time", submitTime);
    }

    @Transactional
    public Map<String, Object> review(String reviewId, Map<String, Object> request, User actor) {
        Double teacherScore = doubleValue(request.get("teacher_score"));
        String teacherComment = stringValue(request.get("teacher_comment"));
        if (!StringUtils.hasText(reviewId) || teacherScore == null || teacherScore < 0 || teacherScore > 100
                || !StringUtils.hasText(teacherComment)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT hr.review_id, hr.teacher_score, hs.submit_id, h.course_class_id
                FROM homework_review hr
                JOIN homework_submit hs ON hr.submit_id = hs.submit_id
                JOIN homework h ON hs.homework_id = h.homework_id
                JOIN course_class cc ON h.course_class_id = cc.course_class_id
                WHERE hr.review_id = ? AND cc.teacher_id = ?
                LIMIT 1
                """, reviewId, actor.getAccount());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (rows.get(0).get("teacher_score") != null) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        LocalDateTime reviewTime = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE homework_review
                SET teacher_score = ?, teacher_comment = ?, review_time = ?
                WHERE review_id = ?
                """, teacherScore, teacherComment, Timestamp.valueOf(reviewTime), reviewId);
        jdbcTemplate.update("""
                UPDATE homework_submit
                SET submit_status = ?
                WHERE submit_id = ?
                """, "已批改", rows.get(0).get("submit_id"));
        return Map.of("review_id", reviewId, "teacher_score", teacherScore, "review_time", reviewTime);
    }

    private Map<String, Object> requireOpenHomework(String homeworkId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT homework_id, status, deadline
                FROM homework
                WHERE homework_id = ?
                LIMIT 1
                """, homeworkId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> homework = rows.get(0);
        if (!"已发布".equals(stringValue(homework.get("status")))
                || LocalDateTime.now().isAfter(toLocalDateTime(homework.get("deadline")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        return homework;
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

    private LocalDateTime parseDateTime(Object value) {
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
