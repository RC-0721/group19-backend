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
public class AdminAiOpsService {

    private static final int DEFAULT_FAILURE_RATE_THRESHOLD = 30;
    private static final int DEFAULT_PENDING_THRESHOLD = 10;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiService aiService;

    public AdminAiOpsService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AiService aiService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiService = aiService;
    }

    @Transactional
    public Map<String, Object> listAlerts(String status, String severity, Integer pageNo, Integer pageSize, User actor) {
        validateAdmin(actor);
        validatePage(pageNo, pageSize);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(status)) {
            where.append("AND status = ?\n");
            params.add(status);
        }
        if (StringUtils.hasText(severity)) {
            where.append("AND severity = ?\n");
            params.add(severity);
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_alert " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT alert_id, alert_type, severity, summary, detail_json, status, created_time, resolved_time
                FROM ai_alert
                """ + where + """
                ORDER BY created_time DESC, alert_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return page(records, total, pageNo, pageSize);
    }

    @Transactional
    public Map<String, Object> resolveAlert(String alertId, User actor) {
        validateAdmin(actor);
        if (!StringUtils.hasText(alertId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        int updated = jdbcTemplate.update("""
                UPDATE ai_alert
                SET status = '已解决', resolved_time = ?
                WHERE alert_id = ?
                """, Timestamp.valueOf(LocalDateTime.now()), alertId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return Map.of("alert_id", alertId, "status", "已解决", "resolved_time", LocalDateTime.now().toString());
    }

    @Transactional
    public Map<String, Object> monitor(User actor) {
        validateAdmin(actor);
        Map<String, Object> metrics = metrics();
        List<Map<String, Object>> recentAlerts = jdbcTemplate.queryForList("""
                SELECT alert_id, alert_type, severity, summary, detail_json, status, created_time, resolved_time
                FROM ai_alert
                ORDER BY created_time DESC, alert_id
                LIMIT 10
                """);
        return Map.of("metrics", metrics, "recent_alerts", recentAlerts);
    }

    @Transactional
    public Map<String, Object> evaluateAlertRules(Map<String, Object> request, User actor) {
        validateAdmin(actor);
        int failureRateThreshold = intValue(request, "failure_rate_threshold", DEFAULT_FAILURE_RATE_THRESHOLD);
        int pendingThreshold = intValue(request, "pending_threshold", DEFAULT_PENDING_THRESHOLD);
        Map<String, Object> metrics = metrics();
        List<Map<String, Object>> created = new ArrayList<>();

        int aiCallTotal = number(metrics.get("ai_call_total"));
        int aiCallFailures = number(metrics.get("ai_call_failures"));
        int failureRate = aiCallTotal == 0 ? 0 : (int) Math.round(aiCallFailures * 100.0 / aiCallTotal);
        if (aiCallTotal > 0 && failureRate >= failureRateThreshold) {
            created.add(upsertOpenAlert("AI_CALL_FAILURE_RATE", "高",
                    "AI 调用失败率达到 " + failureRate + "%",
                    linkedMap("total", aiCallTotal, "failures", aiCallFailures, "failure_rate", failureRate)));
        }
        createPendingAlert(created, "MATERIAL_PARSE_BACKLOG", "资料解析任务积压",
                number(metrics.get("material_parse_pending")), pendingThreshold);
        createPendingAlert(created, "CONTENT_SCORE_BACKLOG", "内容评分异常积压",
                number(metrics.get("content_score_abnormal_pending")), pendingThreshold);
        createPendingAlert(created, "OPERATION_ERROR_SPIKE", "异常操作日志增多",
                number(metrics.get("operation_errors")), pendingThreshold);
        return Map.of("rules", linkedMap(
                "failure_rate_threshold", failureRateThreshold,
                "pending_threshold", pendingThreshold
        ), "metrics", metrics, "created_alerts", created);
    }

    public Map<String, Object> listMaterialReviewQueue(
            String reviewStatus, Integer pageNo, Integer pageSize, User actor) {
        validateAdmin(actor);
        validatePage(pageNo, pageSize);
        String targetStatus = StringUtils.hasText(reviewStatus) ? reviewStatus : "待复核";
        int limit = pageSize;
        int offset = (pageNo - 1) * pageSize;
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT CONCAT('audit:', maa.audit_id) AS review_id,
                       'MATERIAL_AUDIT' AS source_type,
                       maa.audit_id AS source_id,
                       maa.material_id,
                       cm.file_name AS material_name,
                       CONCAT_WS('；',
                         NULLIF(maa.inappropriate_content, ''),
                         CONCAT('格式风险：', COALESCE(maa.format_risk, '')),
                         CONCAT('版权风险：', COALESCE(maa.copyright_risk, ''))
                       ) AS risk_summary,
                       maa.category,
                       maa.difficulty,
                       maa.applicable_course,
                       maa.inappropriate_content,
                       maa.format_risk,
                       maa.copyright_risk,
                       maa.audit_result_json AS detail_json,
                       COALESCE(maa.review_status, '待复核') AS review_status,
                       maa.review_result,
                       maa.created_time
                FROM material_ai_audit maa
                LEFT JOIN course_material cm ON cm.material_id = maa.material_id
                WHERE COALESCE(maa.review_status, '待复核') = ?
                  AND (COALESCE(maa.inappropriate_content, '') <> '未发现'
                       OR COALESCE(maa.format_risk, '') <> '低'
                       OR COALESCE(maa.copyright_risk, '') <> '低')
                UNION ALL
                SELECT CONCAT('score:', cs.score_id) AS review_id,
                       'CONTENT_SCORE' AS source_type,
                       cs.score_id AS source_id,
                       cs.source_id AS material_id,
                       cm.file_name AS material_name,
                       CONCAT(COALESCE(cs.abnormal_flag, ''), '：', COALESCE(cs.ai_explanation, '')) AS risk_summary,
                       cs.source_type AS category,
                       NULL AS difficulty,
                       NULL AS applicable_course,
                       cs.abnormal_flag AS inappropriate_content,
                       NULL AS format_risk,
                       NULL AS copyright_risk,
                       cs.ai_explanation AS detail_json,
                       COALESCE(cs.review_status, '待复核') AS review_status,
                       cs.review_result,
                       cs.created_time
                FROM content_score cs
                LEFT JOIN course_material cm ON cs.source_type = 'MATERIAL' AND cm.material_id = cs.source_id
                WHERE COALESCE(cs.review_status, '待复核') = ?
                  AND cs.abnormal_flag <> '正常'
                ORDER BY created_time DESC, review_id
                LIMIT ? OFFSET ?
                """, targetStatus, targetStatus, limit, offset);
        Integer total = jdbcTemplate.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM material_ai_audit
                   WHERE COALESCE(review_status, '待复核') = ?
                     AND (COALESCE(inappropriate_content, '') <> '未发现'
                          OR COALESCE(format_risk, '') <> '低'
                          OR COALESCE(copyright_risk, '') <> '低'))
                  +
                  (SELECT COUNT(*) FROM content_score
                   WHERE COALESCE(review_status, '待复核') = ?
                     AND abnormal_flag <> '正常')
                """, Integer.class, targetStatus, targetStatus);
        return page(records, total, pageNo, pageSize);
    }

    public Map<String, Object> materialReviewSuggestion(String reviewId, User actor) {
        validateAdmin(actor);
        ReviewTarget target = requireReviewTarget(reviewId);
        Map<String, Object> result = aiService.chat(Map.of(
                "scene", "MATERIAL_REVIEW",
                "system_prompt", "你是教学平台资料复核助手，请给出管理员复核建议。",
                "prompt", "复核对象：" + target.type() + "\n明细：" + target.detail()
        ), actor);
        return Map.of(
                "review_id", reviewId,
                "source_type", target.type(),
                "suggestion", result.get("content"),
                "request_id", result.get("request_id")
        );
    }

    @Transactional
    public Map<String, Object> updateMaterialReview(String reviewId, Map<String, Object> request, User actor) {
        validateAdmin(actor);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String reviewStatus = defaultValue(stringValue(request.get("review_status")), "已复核");
        String reviewResult = stringValue(request.get("review_result"));
        if (!StringUtils.hasText(reviewResult)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        ReviewId id = parseReviewId(reviewId);
        String sql = switch (id.type()) {
            case "audit" -> """
                    UPDATE material_ai_audit
                    SET review_status = ?, review_result = ?, reviewed_by = ?, reviewed_time = ?
                    WHERE audit_id = ?
                    """;
            case "score" -> """
                    UPDATE content_score
                    SET review_status = ?, review_result = ?, reviewed_by = ?, reviewed_time = ?
                    WHERE score_id = ?
                    """;
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR);
        };
        int updated = jdbcTemplate.update(sql, reviewStatus, reviewResult, actor.getAccount(),
                Timestamp.valueOf(LocalDateTime.now()), id.sourceId());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return Map.of("review_id", reviewId, "review_status", reviewStatus, "review_result", reviewResult);
    }

    private Map<String, Object> metrics() {
        Integer aiCallTotal = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_call_log
                WHERE call_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)
                """, Integer.class);
        Integer aiCallFailures = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_call_log
                WHERE call_time >= DATE_SUB(NOW(), INTERVAL 1 DAY) AND call_status <> '成功'
                """, Integer.class);
        Integer materialParsePending = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM material_parse_task
                WHERE task_status IN ('待解析', '解析中', '待确认')
                """, Integer.class);
        Integer contentScoreAbnormalPending = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM content_score
                WHERE abnormal_flag <> '正常' AND COALESCE(review_status, '待复核') = '待复核'
                """, Integer.class);
        Integer operationErrors = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM operation_log
                WHERE operation_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)
                  AND (operation_result LIKE '%失败%' OR operation_result LIKE '5%')
                """, Integer.class);
        return linkedMap(
                "ai_call_total", zero(aiCallTotal),
                "ai_call_failures", zero(aiCallFailures),
                "material_parse_pending", zero(materialParsePending),
                "content_score_abnormal_pending", zero(contentScoreAbnormalPending),
                "operation_errors", zero(operationErrors)
        );
    }

    private void createPendingAlert(List<Map<String, Object>> created, String type, String summary,
                                    int pendingCount, int threshold) {
        if (pendingCount >= threshold) {
            created.add(upsertOpenAlert(type, pendingCount >= threshold * 2 ? "高" : "中",
                    summary + "：" + pendingCount + " 条",
                    linkedMap("pending_count", pendingCount, "threshold", threshold)));
        }
    }

    private Map<String, Object> upsertOpenAlert(
            String alertType, String severity, String summary, Map<String, Object> detail) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("""
                SELECT alert_id, alert_type, severity, summary, detail_json, status, created_time, resolved_time
                FROM ai_alert
                WHERE alert_type = ? AND status = '待处理'
                ORDER BY created_time DESC
                LIMIT 1
                """, alertType);
        if (!existing.isEmpty()) {
            String alertId = stringValue(existing.get(0).get("alert_id"));
            jdbcTemplate.update("""
                    UPDATE ai_alert SET severity = ?, summary = ?, detail_json = ?
                    WHERE alert_id = ?
                    """, severity, summary, json(detail), alertId);
            return linkedMap("alert_id", alertId, "alert_type", alertType, "severity", severity, "summary", summary);
        }
        String alertId = "ai-alert-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_alert (alert_id, alert_type, severity, summary, detail_json, status)
                VALUES (?, ?, ?, ?, ?, '待处理')
                """, alertId, alertType, severity, summary, json(detail));
        return linkedMap("alert_id", alertId, "alert_type", alertType, "severity", severity, "summary", summary);
    }

    private ReviewTarget requireReviewTarget(String reviewId) {
        ReviewId id = parseReviewId(reviewId);
        if ("audit".equals(id.type())) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT audit_id, material_id, category, difficulty, applicable_course,
                           inappropriate_content, format_risk, copyright_risk, audit_result_json
                    FROM material_ai_audit
                    WHERE audit_id = ?
                    LIMIT 1
                    """, id.sourceId());
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return new ReviewTarget("MATERIAL_AUDIT", json(rows.get(0)));
        }
        if ("score".equals(id.type())) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT score_id, source_type, source_id, quality_score, abnormal_flag, ai_explanation
                    FROM content_score
                    WHERE score_id = ?
                    LIMIT 1
                    """, id.sourceId());
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return new ReviewTarget("CONTENT_SCORE", json(rows.get(0)));
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR);
    }

    private ReviewId parseReviewId(String reviewId) {
        if (!StringUtils.hasText(reviewId) || !reviewId.contains(":")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String[] parts = reviewId.split(":", 2);
        if (!StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return new ReviewId(parts[0], parts[1]);
    }

    private void validateAdmin(User actor) {
        if (actor == null || !"EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private Map<String, Object> page(List<Map<String, Object>> records, Integer total, Integer pageNo, Integer pageSize) {
        return Map.of("records", records, "total", zero(total), "page_no", pageNo, "page_size", pageSize);
    }

    private int intValue(Map<String, Object> request, String key, int fallback) {
        if (request == null || request.get(key) == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(request.get(key)));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private record ReviewId(String type, String sourceId) {
    }

    private record ReviewTarget(String type, String detail) {
    }
}
