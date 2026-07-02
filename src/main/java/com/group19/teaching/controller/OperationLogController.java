package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.OperationLogService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationLogController {

    private final OperationLogService operationLogService;
    private final AuthService authService;

    public OperationLogController(OperationLogService operationLogService, AuthService authService) {
        this.operationLogService = operationLogService;
        this.authService = authService;
    }

    @GetMapping("/api/logs/operations")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "operation_type", required = false) String operationType,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(operationLogService.list(
                userId, module, operationType, startTime, endTime, pageNo, pageSize));
    }

    @GetMapping("/api/logs/operations/export")
    public ResponseEntity<String> export(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "operation_type", required = false) String operationType,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime) {
        authService.requireRole(token, "EDU_ADMIN");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operation-logs.csv")
                .contentType(new MediaType("text", "csv"))
                .body(operationLogService.exportCsv(userId, module, operationType, startTime, endTime));
    }
}
