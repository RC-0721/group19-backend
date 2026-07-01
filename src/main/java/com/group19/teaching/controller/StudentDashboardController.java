package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.StudentDashboardService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentDashboardController {

    private final AuthService authService;
    private final StudentDashboardService studentDashboardService;

    public StudentDashboardController(AuthService authService, StudentDashboardService studentDashboardService) {
        this.authService = authService;
        this.studentDashboardService = studentDashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@RequestHeader(value = "token", required = false) String token) {
        return ApiResponse.success(studentDashboardService.load(authService.requireRole(token, "STUDENT")));
    }
}
