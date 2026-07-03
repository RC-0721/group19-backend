package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminAiOpsServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final AiService aiService = org.mockito.Mockito.mock(AiService.class);
    private final AdminAiOpsService adminAiOpsService =
            new AdminAiOpsService(jdbcTemplate, new ObjectMapper(), aiService);

    @Test
    void evaluateAlertRulesCreatesOpenAlertsFromMetrics() {
        when(jdbcTemplate.queryForObject(contains("FROM ai_call_log"), eq(Integer.class)))
                .thenReturn(10)
                .thenReturn(4);
        when(jdbcTemplate.queryForObject(contains("FROM material_parse_task"), eq(Integer.class))).thenReturn(2);
        when(jdbcTemplate.queryForObject(contains("FROM content_score"), eq(Integer.class))).thenReturn(3);
        when(jdbcTemplate.queryForObject(contains("FROM operation_log"), eq(Integer.class))).thenReturn(0);
        when(jdbcTemplate.queryForList(contains("FROM ai_alert"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of());

        Map<String, Object> result = adminAiOpsService.evaluateAlertRules(Map.of("pending_threshold", 2), admin());

        List<?> created = (List<?>) result.get("created_alerts");
        assertEquals(3, created.size());
        verify(jdbcTemplate).update(contains("INSERT INTO ai_alert"), any(), eq("AI_CALL_FAILURE_RATE"),
                eq("高"), contains("AI 调用失败率"), any());
    }

    @Test
    void resolveAlertUpdatesStatus() {
        when(jdbcTemplate.update(contains("UPDATE ai_alert"), any(), eq("ai-alert-1"))).thenReturn(1);

        Map<String, Object> result = adminAiOpsService.resolveAlert("ai-alert-1", admin());

        assertEquals("已解决", result.get("status"));
    }

    @Test
    void materialReviewSuggestionUsesAiService() {
        User admin = admin();
        when(jdbcTemplate.queryForList(contains("FROM material_ai_audit"), eq("audit-1")))
                .thenReturn(List.of(Map.of(
                        "audit_id", "audit-1",
                        "material_id", "material-1",
                        "format_risk", "需人工确认"
                )));
        when(aiService.chat(any(), eq(admin))).thenReturn(Map.of(
                "request_id", "ai-req-1",
                "content", "建议退回教师补充来源授权"
        ));

        Map<String, Object> result = adminAiOpsService.materialReviewSuggestion("audit:audit-1", admin);

        assertEquals("建议退回教师补充来源授权", result.get("suggestion"));
    }

    @Test
    void updateMaterialReviewWritesAuditDecision() {
        when(jdbcTemplate.update(contains("UPDATE material_ai_audit"), eq("已复核"),
                eq("确认风险"), eq("admin001"), any(), eq("audit-1"))).thenReturn(1);

        Map<String, Object> result = adminAiOpsService.updateMaterialReview(
                "audit:audit-1", Map.of("review_result", "确认风险"), admin());

        assertEquals("已复核", result.get("review_status"));
    }

    private User admin() {
        User user = new User();
        user.setAccount("admin001");
        user.setRole("EDU_ADMIN");
        return user;
    }
}
