package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WrongBookService {

    private final JdbcTemplate jdbcTemplate;

    public WrongBookService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(
            String knowledgeId,
            String jobId,
            String wrongBookStatus,
            Integer pageNo,
            Integer pageSize,
            User actor) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Object> params = new ArrayList<>();
        String where = buildWhere(knowledgeId, jobId, wrongBookStatus, actor, params);
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT wb.wrong_id)
                FROM wrong_book wb
                JOIN question q ON wb.question_id = q.question_id
                LEFT JOIN question_knowledge_relation qkr ON q.question_id = qkr.question_id
                LEFT JOIN question_job_relation qjr ON q.question_id = qjr.question_id
                """ + where, Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT DISTINCT wb.wrong_id, wb.student_id, wb.question_id, q.stem, q.question_type,
                       q.difficulty, q.answer_analysis, wb.wrong_count, wb.last_wrong_time,
                       wb.master_status AS wrong_book_status
                FROM wrong_book wb
                JOIN question q ON wb.question_id = q.question_id
                LEFT JOIN question_knowledge_relation qkr ON q.question_id = qkr.question_id
                LEFT JOIN question_job_relation qjr ON q.question_id = qjr.question_id
                """ + where + """
                ORDER BY wb.last_wrong_time DESC, wb.question_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
    }

    private String buildWhere(String knowledgeId, String jobId, String wrongBookStatus, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            where.append("AND wb.student_id = ?\n");
            params.add(actor.getAccount());
        } else if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                      SELECT 1
                      FROM student_profile sp
                      JOIN course_class cc ON sp.class_id = cc.class_id
                      WHERE sp.student_id = wb.student_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        } else if (!"EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        append(where, params, "qkr.knowledge_id", knowledgeId);
        append(where, params, "qjr.job_id", jobId);
        append(where, params, "wb.master_status", wrongBookStatus);
        return where.toString();
    }

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
        }
    }
}
