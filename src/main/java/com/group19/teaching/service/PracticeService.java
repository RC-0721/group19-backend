package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.ArrayList;
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

    public Map<String, Object> listRecords(
            Integer pageNo,
            Integer pageSize,
            String questionId,
            String startTime,
            String endTime,
            User actor) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE pr.student_id = ?\n");
        params.add(actor.getAccount());
        append(where, params, "pr.question_id", questionId);
        if (StringUtils.hasText(startTime)) {
            where.append("AND pr.submit_time >= ?\n");
            params.add(startTime.trim());
        }
        if (StringUtils.hasText(endTime)) {
            where.append("AND pr.submit_time <= ?\n");
            params.add(endTime.trim());
        }

        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM practice_record pr
                JOIN question q ON pr.question_id = q.question_id
                """ + where, Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT pr.record_id, pr.question_id, q.stem,
                       CASE WHEN pr.is_correct THEN '正确' ELSE '错误' END AS answer_result,
                       pr.score, pr.submit_time
                FROM practice_record pr
                JOIN question q ON pr.question_id = q.question_id
                """ + where + """
                ORDER BY pr.submit_time DESC, pr.record_id DESC
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
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

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
        }
    }
}
