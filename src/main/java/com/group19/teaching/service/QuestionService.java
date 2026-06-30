package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class QuestionService {

    private final JdbcTemplate jdbcTemplate;

    public QuestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request, User actor) {
        String questionType = stringValue(request.get("question_type"));
        String stem = stringValue(request.get("stem"));
        String difficulty = stringValue(request.get("difficulty"));
        String answer = stringValue(request.get("answer"));
        String answerAnalysis = stringValue(request.get("answer_analysis"));
        String knowledgeId = stringValue(request.get("knowledge_id"));
        String jobId = stringValue(request.get("job_id"));
        String techId = stringValue(request.get("tech_id"));
        String auditStatus = stringValue(request.get("audit_status"));
        Object optionsValue = request.get("options");
        if (!StringUtils.hasText(questionType) || !StringUtils.hasText(stem) || !StringUtils.hasText(difficulty)
                || !StringUtils.hasText(answer) || !StringUtils.hasText(answerAnalysis)
                || !StringUtils.hasText(knowledgeId) || !StringUtils.hasText(jobId)
                || !StringUtils.hasText(techId) || !isQuestionStatus(auditStatus)
                || (optionsValue != null && !(optionsValue instanceof List<?>))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        String questionId = "q-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO question (question_id, source_id, source_path, source_url, question_type, stem, difficulty, answer, answer_analysis, audit_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, questionId, blankToNull(request.get("source_id")), blankToNull(request.get("source_path")),
                blankToNull(request.get("source_url")), questionType, stem, difficulty, answer, answerAnalysis,
                auditStatus);

        if (optionsValue instanceof List<?> options) {
            for (Object optionValue : options) {
                if (!(optionValue instanceof Map<?, ?> option)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR);
                }
                String label = stringValue(option.get("option_label"));
                String content = stringValue(option.get("option_content"));
                if (!StringUtils.hasText(label) || !StringUtils.hasText(content)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR);
                }
                jdbcTemplate.update("""
                        INSERT INTO question_option (option_id, question_id, option_label, option_content, is_correct)
                        VALUES (?, ?, ?, ?, ?)
                        """, "qo-" + UUID.randomUUID(), questionId, label, content,
                        booleanValue(option.get("is_correct")));
            }
        }

        jdbcTemplate.update("""
                INSERT INTO question_knowledge_relation (relation_id, question_id, knowledge_id, weight)
                VALUES (?, ?, ?, ?)
                """, "qk-" + UUID.randomUUID(), questionId, knowledgeId, 1.0);
        jdbcTemplate.update("""
                INSERT INTO question_job_relation (relation_id, question_id, job_id, tech_id, match_level)
                VALUES (?, ?, ?, ?, ?)
                """, "qj-" + UUID.randomUUID(), questionId, jobId, techId, "核心");
        return Map.of("question_id", questionId, "audit_status", auditStatus);
    }

    public Map<String, Object> audit(String questionId, String auditStatus, User actor) {
        if (!StringUtils.hasText(questionId) || !isQuestionStatus(auditStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        int updated = jdbcTemplate.update("""
                UPDATE question
                SET audit_status = ?
                WHERE question_id = ?
                """, auditStatus, questionId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return Map.of("question_id", questionId, "audit_status", auditStatus);
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

    private boolean isQuestionStatus(String value) {
        return List.of("草稿", "待审核", "已发布", "已驳回", "已下架").contains(value);
    }

    private Object blankToNull(Object value) {
        String text = stringValue(value);
        return StringUtils.hasText(text) ? text : null;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(stringValue(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
