package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.service.AuthService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentDashboardController {

    private final AuthService authService;

    public StudentDashboardController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@RequestHeader(value = "token", required = false) String token) {
        authService.requireRole(token, "STUDENT");
        // Mock data keeps frontend integration moving before real course tables are introduced.
        return ApiResponse.success(Map.of(
                "courses", List.of(Map.of(
                        "course_id", "course-java-001",
                        "course_name", "Java Web 应用开发",
                        "progress", 68,
                        "next_task", "完成 Spring Boot 登录接口联调"
                )),
                "todo_tasks", List.of(Map.of(
                        "task_id", "todo-001",
                        "task_type", "PRE_TASK",
                        "title", "课前基础测评",
                        "deadline", "2026-06-30"
                )),
                "practice_summary", Map.of(
                        "finished_count", 24,
                        "accuracy", 82,
                        "weak_point", "数据库连接与事务"
                ),
                "project_summary", Map.of(
                        "submitted_count", 1,
                        "latest_status", "系统辅助评估"
                ),
                "interview_summary", Map.of(
                        "completed_count", 2,
                        "latest_score", 76
                ),
                "profile_summary", Map.of(
                        "profile_status", "初始画像",
                        "target_job", "Java 后端开发工程师",
                        "recommendation", "优先补齐 Spring Boot、MySQL 和接口联调能力"
                )
        ));
    }
}
