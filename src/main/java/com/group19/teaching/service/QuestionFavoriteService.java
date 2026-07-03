package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class QuestionFavoriteService {

    private final JdbcTemplate jdbcTemplate;

    public QuestionFavoriteService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(Integer pageNo, Integer pageSize, User actor) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM question_favorite qf
                JOIN question q ON qf.question_id = q.question_id
                WHERE qf.student_id = ?
                """, Integer.class, actor.getAccount());
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT qf.question_id, q.stem, q.question_type, q.difficulty, qf.created_time
                FROM question_favorite qf
                JOIN question q ON qf.question_id = q.question_id
                WHERE qf.student_id = ?
                ORDER BY qf.created_time DESC, qf.question_id
                LIMIT ? OFFSET ?
                """, actor.getAccount(), pageSize, (pageNo - 1) * pageSize);
        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
    }

    @Transactional
    public Map<String, Object> add(String questionId, User actor) {
        String normalizedQuestionId = stringValue(questionId);
        if (!StringUtils.hasText(normalizedQuestionId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> questions = jdbcTemplate.queryForList("""
                SELECT question_id, audit_status
                FROM question
                WHERE question_id = ?
                LIMIT 1
                """, normalizedQuestionId);
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"已发布".equals(stringValue(questions.get(0).get("audit_status")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        jdbcTemplate.update("""
                INSERT INTO question_favorite (favorite_id, student_id, question_id, created_time)
                VALUES (?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE question_id = VALUES(question_id)
                """, "qf-" + UUID.randomUUID(), actor.getAccount(), normalizedQuestionId);
        return Map.of("question_id", normalizedQuestionId, "favorited", true);
    }

    @Transactional
    public Map<String, Object> delete(String questionId, User actor) {
        String normalizedQuestionId = stringValue(questionId);
        if (!StringUtils.hasText(normalizedQuestionId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        jdbcTemplate.update("""
                DELETE FROM question_favorite
                WHERE student_id = ? AND question_id = ?
                """, actor.getAccount(), normalizedQuestionId);
        return Map.of("question_id", normalizedQuestionId, "favorited", false);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
