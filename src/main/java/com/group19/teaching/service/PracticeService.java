package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PracticeService {

    private final JdbcTemplate jdbcTemplate;

    public PracticeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> submit(String questionId, String answer, String scene, String jobId, User actor) {
        if (!StringUtils.hasText(questionId) || !StringUtils.hasText(answer)
                || !StringUtils.hasText(scene) || !StringUtils.hasText(jobId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT question_id, answer, answer_analysis, audit_status
                FROM question
                WHERE question_id = ?
                LIMIT 1
                """, questionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> question = rows.get(0);
        if (!"已发布".equals(String.valueOf(question.get("audit_status")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }

        boolean correct = Objects.equals(answer, stringValue(question.get("answer")));
        int score = correct ? 100 : 0;
        String recordId = "practice-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO practice_record (record_id, student_id, question_id, answer, is_correct, score, submit_time)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """, recordId, actor.getAccount(), questionId, answer, correct, score);

        String wrongBookStatus = "无需记录";
        if (!correct) {
            wrongBookStatus = "未掌握";
            jdbcTemplate.update("""
                    INSERT INTO wrong_book (wrong_id, student_id, question_id, wrong_count, last_wrong_time, master_status)
                    VALUES (?, ?, ?, 1, NOW(), ?)
                    ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1, last_wrong_time = NOW(), master_status = VALUES(master_status)
                    """, "wrong-" + UUID.randomUUID(), actor.getAccount(), questionId, wrongBookStatus);
        }

        return Map.of(
                "record_id", recordId,
                "is_correct", correct,
                "score", score,
                "answer_analysis", stringValue(question.get("answer_analysis")),
                "wrong_book_status", wrongBookStatus
        );
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
