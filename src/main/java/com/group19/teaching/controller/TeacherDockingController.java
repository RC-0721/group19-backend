package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.TeacherDockingService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeacherDockingController {

    private final AuthService authService;
    private final TeacherDockingService teacherDockingService;

    public TeacherDockingController(AuthService authService, TeacherDockingService teacherDockingService) {
        this.authService = authService;
        this.teacherDockingService = teacherDockingService;
    }

    @GetMapping("/api/teacher/options")
    public ApiResponse<Map<String, Object>> options(
            @RequestHeader(value = "token", required = false) String token) {
        User actor = authService.requireUser(token);
        return ApiResponse.success(teacherDockingService.options(actor));
    }

    @GetMapping("/api/teacher/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(
            @RequestHeader(value = "token", required = false) String token) {
        User actor = authService.requireUser(token);
        return ApiResponse.success(teacherDockingService.dashboard(actor));
    }

    @GetMapping("/api/teacher/profile-analysis-options")
    public ApiResponse<Map<String, Object>> profileAnalysisOptions(
            @RequestHeader(value = "token", required = false) String token) {
        User actor = authService.requireUser(token);
        return ApiResponse.success(teacherDockingService.profileAnalysisOptions(actor));
    }
}
