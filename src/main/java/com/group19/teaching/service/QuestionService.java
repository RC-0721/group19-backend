package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QuestionService {

    private final JdbcTemplate jdbcTemplate;

    public QuestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(
            String knowledgeId,
            String sourceId,
            String jobId,
            String techId,
            String keyword,
            String questionType,
            String difficulty,
            Integer pageNo,
            Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Object> params = new ArrayList<>();
        String where = buildWhere(knowledgeId, sourceId, jobId, techId, keyword, questionType, difficulty, params);
        String countSql = "SELECT COUNT(DISTINCT q.question_id) " + baseFrom() + where;
        Integer total = params.isEmpty()
                ? jdbcTemplate.queryForObject(countSql, Integer.class)
                : jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT DISTINCT q.question_id, q.stem, q.question_type, q.difficulty, q.audit_status,
                       ds.source_id, ds.source_name, ds.license AS source_license, q.source_url,
                       kp.knowledge_id, qjr.job_id, qjr.tech_id
                """ + baseFrom() + where + """
                ORDER BY q.question_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
    }

    private String baseFrom() {
        return """
                FROM question q
                LEFT JOIN data_source ds ON q.source_id = ds.source_id
                LEFT JOIN question_knowledge_relation qkr ON q.question_id = qkr.question_id
                LEFT JOIN knowledge_point kp ON qkr.knowledge_id = kp.knowledge_id
                LEFT JOIN question_job_relation qjr ON q.question_id = qjr.question_id
                """;
    }

    private String buildWhere(
            String knowledgeId,
            String sourceId,
            String jobId,
            String techId,
            String keyword,
            String questionType,
            String difficulty,
            List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE q.audit_status = '已发布'\n");
        appendFilter(where, params, "kp.knowledge_id", knowledgeId);
        appendFilter(where, params, "q.source_id", sourceId);
        appendFilter(where, params, "qjr.job_id", jobId);
        appendFilter(where, params, "qjr.tech_id", techId);
        appendFilter(where, params, "q.question_type", questionType);
        appendFilter(where, params, "q.difficulty", difficulty);
        if (StringUtils.hasText(keyword)) {
            where.append("AND q.stem LIKE ?\n");
            params.add("%" + keyword + "%");
        }
        return where.toString();
    }

    private void appendFilter(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value);
        }
    }
}
