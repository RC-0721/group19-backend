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

    public Map<String, Object> metadata(User actor) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("question_types", optionRows(distinctQuestionColumn("question_type", actor)));
        result.put("difficulties", optionRows(distinctQuestionColumn("difficulty", actor)));
        result.put("knowledge_points", knowledgeMetadata(actor));
        result.put("jobs", jdbcTemplate.queryForList("""
                SELECT job_id, job_name
                FROM job_direction
                WHERE status = '启用'
                ORDER BY job_name, job_id
                """));
        result.put("tech_stacks", jdbcTemplate.queryForList("""
                SELECT DISTINCT ts.tech_id, ts.tech_name, jss.job_id
                FROM tech_stack ts
                JOIN job_skill_standard jss ON jss.tech_id = ts.tech_id
                JOIN job_direction jd ON jd.job_id = jss.job_id
                WHERE jd.status = '启用'
                ORDER BY jss.job_id, ts.tech_name, ts.tech_id
                """));
        result.put("audit_statuses", auditStatusMetadata(actor));
        return result;
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

    private List<Map<String, Object>> distinctQuestionColumn(String column, User actor) {
        String condition = "STUDENT".equalsIgnoreCase(actor.getRole())
                ? "WHERE q.audit_status = '已发布' AND q." + column + " IS NOT NULL AND q." + column + " <> ''"
                : "WHERE q." + column + " IS NOT NULL AND q." + column + " <> ''";
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT q.%s AS value
                FROM question q
                %s
                ORDER BY q.%s
                """.formatted(column, condition, column));
    }

    private List<Map<String, Object>> knowledgeMetadata(User actor) {
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            return jdbcTemplate.queryForList("""
                    SELECT DISTINCT kp.knowledge_id, kp.name AS knowledge_name, kp.course_id, kp.chapter_id
                    FROM knowledge_point kp
                    JOIN question_knowledge_relation qkr ON qkr.knowledge_id = kp.knowledge_id
                    JOIN question q ON q.question_id = qkr.question_id
                    WHERE q.audit_status = '已发布'
                      AND kp.audit_status = '已发布'
                    ORDER BY kp.course_id, kp.chapter_id, kp.name, kp.knowledge_id
                    """);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            return jdbcTemplate.queryForList("""
                    SELECT DISTINCT kp.knowledge_id, kp.name AS knowledge_name, kp.course_id, kp.chapter_id
                    FROM knowledge_point kp
                    JOIN course_class cc ON cc.course_id = kp.course_id
                    WHERE cc.teacher_id = ?
                    ORDER BY kp.course_id, kp.chapter_id, kp.name, kp.knowledge_id
                    """, actor.getAccount());
        }
        if ("EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            return jdbcTemplate.queryForList("""
                    SELECT kp.knowledge_id, kp.name AS knowledge_name, kp.course_id, kp.chapter_id
                    FROM knowledge_point kp
                    ORDER BY kp.course_id, kp.chapter_id, kp.name, kp.knowledge_id
                    """);
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private List<Map<String, Object>> auditStatusMetadata(User actor) {
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            return List.of(option("已发布"));
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT DISTINCT audit_status AS value
                FROM question
                WHERE audit_status IS NOT NULL AND audit_status <> ''
                ORDER BY audit_status
                """);
        return optionRows(rows);
    }

    private List<Map<String, Object>> optionRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String value = stringValue(row.get("value"));
            if (StringUtils.hasText(value)) {
                options.add(option(value));
            }
        }
        return options;
    }

    private Map<String, Object> option(String value) {
        Map<String, Object> option = new java.util.LinkedHashMap<>();
        option.put("value", value);
        option.put("label", value);
        return option;
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
