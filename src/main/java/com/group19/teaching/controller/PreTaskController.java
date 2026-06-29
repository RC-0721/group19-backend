package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.PreTaskService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pre-tasks")
public class PreTaskController {

    private final PreTaskService preTaskService;
    private final AuthService authService;

    public PreTaskController(PreTaskService preTaskService, AuthService authService) {
        this.preTaskService = preTaskService;
        this.authService = authService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(preTaskService.create(request, actor));
    }

    @PostMapping("/{preTaskId}/submits")
    public ApiResponse<Map<String, Object>> submit(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String preTaskId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(preTaskService.submit(preTaskId, request, actor));
    }
}
