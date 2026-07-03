package com.group19.teaching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TeachingAiEnhancementService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiService aiService;

    public TeachingAiEnhancementService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AiService aiService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiService = aiService;
    }

    @Transactional
    public Map<String, Object> generatePreTaskCandidates(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String courseClassId = stringValue(request.get("course_class_id"));
        String materialId = stringValue(request.get("material_id"));
        if (!StringUtils.hasText(courseClassId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> courseClass = requireTeacherCourseClass(courseClassId, actor);
        Map<String, Object> material = StringUtils.hasText(materialId)
                ? requireMaterial(materialId, stringValue(courseClass.get("course_id")), actor)
                : defaultMaterial(stringValue(courseClass.get("course_id")));
        String candidateId = "pre-cand-" + UUID.randomUUID();
        Map<String, Object> payload = linkedMap(
                "title", "AI 课前预习：" + stringValue(material.get("file_name")),
                "material_id", material.get("material_id"),
                "deadline_suggestion", LocalDateTime.now().plusDays(7).toString(),
                "task_type", "课前任务",
                "reason", "根据课程资料摘要生成，需教师审核后发布"
        );
        jdbcTemplate.update("""
                INSERT INTO pre_task_candidate
                  (candidate_id, course_class_id, course_id, material_id, title, task_type,
                   deadline_suggestion, raw_output_json, audit_status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, '待审核', ?)
                """, candidateId, courseClassId, courseClass.get("course_id"), material.get("material_id"),
                payload.get("title"), payload.get("task_type"), Timestamp.valueOf(LocalDateTime.now().plusDays(7)),
                json(payload), actor.getAccount());
        return Map.of("candidate_id", candidateId, "audit_status", "待审核");
    }

    public Map<String, Object> listPreTaskCandidates(
            String courseClassId, String auditStatus, Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(courseClassId)) {
            requireTeacherCourseClass(courseClassId, actor);
            where.append("AND course_class_id = ?\n");
            params.add(courseClassId);
        } else if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                      SELECT 1 FROM course_class cc
                      WHERE cc.course_class_id = pre_task_candidate.course_class_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        }
        if (StringUtils.hasText(auditStatus)) {
            where.append("AND audit_status = ?\n");
            params.add(auditStatus);
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pre_task_candidate " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT candidate_id, course_class_id, course_id, material_id, title, task_type,
                       deadline_suggestion, raw_output_json, audit_status, created_by, created_time, reviewed_time
                FROM pre_task_candidate
                """ + where + """
                ORDER BY created_time DESC, candidate_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return page(records, total, pageNo, pageSize);
    }

    public Map<String, Object> recommendPractice(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String studentId = "STUDENT".equalsIgnoreCase(actor.getRole())
                ? actor.getAccount()
                : stringValue(request.get("student_id"));
        String courseId = stringValue(request.get("course_id"));
        if (!StringUtils.hasText(studentId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (!actor.getAccount().equals(studentId) && !"TEACHER".equalsIgnoreCase(actor.getRole())
                && !"EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<Object> params = new ArrayList<>();
        params.add(studentId);
        String courseFilter = "";
        if (StringUtils.hasText(courseId)) {
            courseFilter = "AND kp.course_id = ?\n";
            params.add(courseId);
        }
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT DISTINCT q.question_id, q.question_type, q.stem, q.difficulty,
                       COALESCE(wb.wrong_count, 0) AS wrong_count,
                       CASE WHEN wb.question_id IS NOT NULL THEN '错题巩固'
                            WHEN pr.question_id IS NULL THEN '新题练习'
                            ELSE '薄弱点复习' END AS reason
                FROM question q
                LEFT JOIN wrong_book wb ON q.question_id = wb.question_id AND wb.student_id = ?
                LEFT JOIN practice_record pr ON q.question_id = pr.question_id AND pr.student_id = ?
                LEFT JOIN question_knowledge_relation qkr ON q.question_id = qkr.question_id
                LEFT JOIN knowledge_point kp ON qkr.knowledge_id = kp.knowledge_id
                WHERE q.audit_status = '已发布'
                """ + courseFilter + """
                ORDER BY wrong_count DESC, q.question_id
                LIMIT 10
                """, duplicateFirstParam(params, studentId).toArray());
        return Map.of("student_id", studentId, "records", records);
    }

    @Transactional
    public Map<String, Object> auditMaterial(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String materialId = stringValue(request.get("material_id"));
        if (!StringUtils.hasText(materialId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> material = requireMaterial(materialId, null, actor);
        Map<String, Object> result = linkedMap(
                "category", defaultValue(stringValue(material.get("ai_category")), "教学资料"),
                "difficulty", difficulty(material),
                "applicable_course", material.get("course_id"),
                "inappropriate_content", "未发现",
                "format_risk", formatRisk(material),
                "copyright_risk", "需确认资料来源授权"
        );
        String auditId = "mat-audit-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO material_ai_audit
                  (audit_id, material_id, category, difficulty, applicable_course,
                   inappropriate_content, format_risk, copyright_risk, audit_result_json, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, auditId, materialId, result.get("category"), result.get("difficulty"),
                result.get("applicable_course"), result.get("inappropriate_content"),
                result.get("format_risk"), result.get("copyright_risk"), json(result), actor.getAccount());
        return Map.of("audit_id", auditId, "material_id", materialId, "audit_result", result);
    }

    @Transactional
    public Map<String, Object> scoreContent(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String sourceType = stringValue(request.get("source_type"));
        String sourceId = stringValue(request.get("source_id"));
        String content = stringValue(request.get("content"));
        if (!StringUtils.hasText(sourceType) || !StringUtils.hasText(sourceId) || !StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        double qualityScore = contentScore(content);
        String abnormalFlag = abnormalFlag(content, qualityScore);
        String aiExplanation = aiExplanation(sourceType, content, actor);
        String scoreId = "content-score-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO content_score
                  (score_id, source_type, source_id, quality_score, abnormal_flag, ai_explanation, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, scoreId, sourceType, sourceId, qualityScore, abnormalFlag, aiExplanation, actor.getAccount());
        return Map.of(
                "score_id", scoreId,
                "quality_score", qualityScore,
                "abnormal_flag", abnormalFlag,
                "ai_explanation", aiExplanation
        );
    }

    private String aiExplanation(String sourceType, String content, User actor) {
        try {
            Map<String, Object> result = aiService.chat(Map.of(
                    "scene", "CONTENT_SCORE",
                    "system_prompt", "你是教学内容质量审核助手，请解释评分风险。",
                    "prompt", "来源：" + sourceType + "\n内容：" + limit(content, 300)
            ), actor);
            return limit(stringValue(result.get("content")), 500);
        } catch (RuntimeException exception) {
            return "AI 解释不可用，已使用统计规则给出评分。";
        }
    }

    private Map<String, Object> requireTeacherCourseClass(String courseClassId, User actor) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT course_class_id, course_id, teacher_id
                FROM course_class
                WHERE course_class_id = ?
                LIMIT 1
                """, courseClassId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"EDU_ADMIN".equalsIgnoreCase(actor.getRole())
                && !"TEACHER".equalsIgnoreCase(actor.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())
                && !actor.getAccount().equals(stringValue(rows.get(0).get("teacher_id")))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return rows.get(0);
    }

    private Map<String, Object> requireMaterial(String materialId, String expectedCourseId, User actor) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT material_id, course_id, chapter_id, file_name, file_type, storage_path,
                       parse_status, ai_tags, ai_category, content_summary
                FROM course_material
                WHERE material_id = ?
                LIMIT 1
                """, materialId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (StringUtils.hasText(expectedCourseId) && !expectedCourseId.equals(stringValue(rows.get(0).get("course_id")))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM course_class
                    WHERE course_id = ? AND teacher_id = ?
                    """, Integer.class, rows.get(0).get("course_id"), actor.getAccount());
            if (count == null || count == 0) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        return rows.get(0);
    }

    private Map<String, Object> defaultMaterial(String courseId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT material_id, course_id, file_name
                FROM course_material
                WHERE course_id = ?
                ORDER BY material_id
                LIMIT 1
                """, courseId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private List<Object> duplicateFirstParam(List<Object> params, String studentId) {
        List<Object> values = new ArrayList<>();
        values.add(studentId);
        values.addAll(params);
        return values;
    }

    private double contentScore(String content) {
        double score = Math.min(100, 40 + Math.log10(Math.max(content.length(), 10)) * 25);
        if (content.length() < 20) {
            score -= 20;
        }
        if (content.chars().distinct().count() < 6) {
            score -= 25;
        }
        return Math.max(0, Math.round(score * 10.0) / 10.0);
    }

    private String abnormalFlag(String content, double qualityScore) {
        if (qualityScore < 50 || content.chars().distinct().count() < 6) {
            return "疑似异常";
        }
        return "正常";
    }

    private String difficulty(Map<String, Object> material) {
        String summary = stringValue(material.get("content_summary"));
        if (summary.length() > 200 || summary.contains("高级") || summary.contains("架构")) {
            return "较难";
        }
        return "中等";
    }

    private String formatRisk(Map<String, Object> material) {
        String type = stringValue(material.get("file_type")).toLowerCase(java.util.Locale.ROOT);
        return List.of("txt", "md", "pdf", "doc", "docx", "ppt", "pptx").contains(type) ? "低" : "需人工确认";
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private Map<String, Object> page(List<Map<String, Object>> records, Integer total, Integer pageNo, Integer pageSize) {
        return Map.of("records", records, "total", total == null ? 0 : total, "page_no", pageNo, "page_size", pageSize);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
