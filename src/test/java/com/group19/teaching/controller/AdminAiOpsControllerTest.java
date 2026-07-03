package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AdminAiOpsService;
import com.group19.teaching.service.AuthService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAiOpsController.class)
class AdminAiOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAiOpsService adminAiOpsService;

    @MockBean
    private AuthService authService;

    @Test
    void routesAdminAiOpsApis() throws Exception {
        User admin = admin();
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(adminAiOpsService.listAlerts(null, null, 1, 20, admin)).thenReturn(page("alert_id", "ai-alert-1"));
        when(adminAiOpsService.resolveAlert("ai-alert-1", admin)).thenReturn(Map.of(
                "alert_id", "ai-alert-1",
                "status", "已解决"
        ));
        when(adminAiOpsService.monitor(admin)).thenReturn(Map.of("metrics", Map.of("ai_call_total", 2)));
        when(adminAiOpsService.evaluateAlertRules(anyMap(), eq(admin))).thenReturn(Map.of(
                "created_alerts", List.of(Map.of("alert_id", "ai-alert-2"))
        ));
        when(adminAiOpsService.listMaterialReviewQueue(null, 1, 20, admin)).thenReturn(page("review_id", "audit:audit-1"));
        when(adminAiOpsService.materialReviewSuggestion("audit:audit-1", admin)).thenReturn(Map.of(
                "review_id", "audit:audit-1",
                "suggestion", "建议复核"
        ));
        when(adminAiOpsService.updateMaterialReview(eq("audit:audit-1"), anyMap(), eq(admin))).thenReturn(Map.of(
                "review_id", "audit:audit-1",
                "review_status", "已复核"
        ));

        mockMvc.perform(get("/api/admin/alerts").header("token", "admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].alert_id").value("ai-alert-1"));

        mockMvc.perform(put("/api/admin/alerts/ai-alert-1/resolve").header("token", "admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已解决"));

        mockMvc.perform(get("/api/admin/ai-monitor").header("token", "admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.ai_call_total").value(2));

        mockMvc.perform(post("/api/admin/alert-rules")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pending_threshold\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created_alerts[0].alert_id").value("ai-alert-2"));

        mockMvc.perform(get("/api/admin/material-review-queue").header("token", "admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].review_id").value("audit:audit-1"));

        mockMvc.perform(post("/api/admin/material-review/audit:audit-1/ai-suggestion")
                        .header("token", "admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestion").value("建议复核"));

        mockMvc.perform(put("/api/admin/material-review/audit:audit-1")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"review_result\":\"确认风险，退回教师\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review_status").value("已复核"));
    }

    private Map<String, Object> page(String key, String value) {
        return Map.of("records", List.of(Map.of(key, value)), "total", 1, "page_no", 1, "page_size", 20);
    }

    private User admin() {
        User user = new User();
        user.setAccount("admin001");
        user.setRole("EDU_ADMIN");
        return user;
    }
}
