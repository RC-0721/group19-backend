package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AdminAiOpsService;
import com.group19.teaching.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminAiOpsController {

    private final AdminAiOpsService adminAiOpsService;
    private final AuthService authService;

    public AdminAiOpsController(AdminAiOpsService adminAiOpsService, AuthService authService) {
        this.adminAiOpsService = adminAiOpsService;
        this.authService = authService;
    }

    @GetMapping("/api/admin/alerts")
    public ApiResponse<Map<String, Object>> alerts(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "page_no", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "page_size", defaultValue = "20") Integer pageSize) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(adminAiOpsService.listAlerts(status, severity, pageNo, pageSize, actor));
    }

    @PutMapping("/api/admin/alerts/{alertId}/resolve")
    public ApiResponse<Map<String, Object>> resolveAlert(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String alertId) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(adminAiOpsService.resolveAlert(alertId, actor));
    }

    @GetMapping("/api/admin/ai-monitor")
    public ApiResponse<Map<String, Object>> monitor(
            @RequestHeader(value = "token", required = false) String token) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(adminAiOpsService.monitor(actor));
    }

    @PostMapping("/api/admin/alert-rules")
    public ApiResponse<Map<String, Object>> evaluateAlertRules(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody(required = false) Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(adminAiOpsService.evaluateAlertRules(request, actor));
    }

    @GetMapping("/api/admin/material-review-queue")
    public ApiResponse<Map<String, Object>> materialReviewQueue(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "review_status", required = false) String reviewStatus,
            @RequestParam(value = "page_no", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "page_size", defaultValue = "20") Integer pageSize) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(adminAiOpsService.listMaterialReviewQueue(reviewStatus, pageNo, pageSize, actor));
    }

    @PostMapping("/api/admin/material-review/{id}/ai-suggestion")
    public ApiResponse<Map<String, Object>> materialReviewSuggestion(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String id) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(adminAiOpsService.materialReviewSuggestion(id, actor));
    }

    @PutMapping("/api/admin/material-review/{id}")
    public ApiResponse<Map<String, Object>> updateMaterialReview(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(adminAiOpsService.updateMaterialReview(id, request, actor));
    }
}
