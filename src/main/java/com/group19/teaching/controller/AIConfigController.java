package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AIConfigService;
import com.group19.teaching.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AIConfigController {

    private final AIConfigService aiConfigService;
    private final AuthService authService;

    public AIConfigController(AIConfigService aiConfigService, AuthService authService) {
        this.aiConfigService = aiConfigService;
        this.authService = authService;
    }

    @GetMapping("/api/ai/config")
    public ApiResponse<Map<String, Object>> get(@RequestHeader(value = "token", required = false) String token) {
        authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(aiConfigService.get());
    }

    @PutMapping("/api/ai/config")
    public ApiResponse<Map<String, Object>> update(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(aiConfigService.update(request, actor));
    }
}
