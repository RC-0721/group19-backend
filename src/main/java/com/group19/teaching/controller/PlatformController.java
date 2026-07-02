package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.PlatformService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformController {

    private final PlatformService platformService;
    private final AuthService authService;

    public PlatformController(PlatformService platformService, AuthService authService) {
        this.platformService = platformService;
        this.authService = authService;
    }

    @GetMapping("/api/platform/summary")
    public ApiResponse<Map<String, Object>> summary(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime) {
        authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(platformService.summary(startTime, endTime));
    }

    @GetMapping("/api/platform/health")
    public ApiResponse<Map<String, Object>> health(
            @RequestHeader(value = "token", required = false) String token) {
        authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(platformService.health());
    }

    @GetMapping("/api/ai/call-logs")
    public ApiResponse<Map<String, Object>> aiCallLogs(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "scene", required = false) String scene,
            @RequestParam(value = "model_name", required = false) String modelName,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(platformService.listAiCallLogs(
                userId, scene, modelName, status, startTime, endTime, pageNo, pageSize));
    }
}
