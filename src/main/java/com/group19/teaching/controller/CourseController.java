package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @GetMapping("/{courseId}")
    public ApiResponse<Map<String, Object>> detail(
            @PathVariable String courseId,
            @RequestParam("course_class_id") String courseClassId) {
        // Mock data keeps the course page contract stable before course tables are migrated.
        return ApiResponse.success(Map.of(
                "course", Map.of(
                        "course_id", courseId,
                        "course_class_id", courseClassId,
                        "course_name", "Java Web 应用开发",
                        "teacher_name", "教师一",
                        "progress", 68,
                        "semester", "2025-2026-2"
                ),
                "chapters", List.of(
                        Map.of("chapter_id", "chapter-001", "chapter_name", "Spring Boot 基础", "sort_no", 1),
                        Map.of("chapter_id", "chapter-002", "chapter_name", "MySQL 与 MyBatis-Plus", "sort_no", 2)
                ),
                "materials", List.of(Map.of(
                        "material_id", "material-001",
                        "chapter_id", "chapter-001",
                        "file_name", "Spring Boot 登录接口讲义.pdf",
                        "file_type", "PDF",
                        "parse_status", "已发布"
                )),
                "pre_tasks", List.of(Map.of(
                        "task_id", "pre-task-001",
                        "title", "课前基础测评",
                        "status", "已发布",
                        "deadline", "2026-06-30"
                )),
                "homeworks", List.of(Map.of(
                        "homework_id", "homework-001",
                        "title", "登录接口联调作业",
                        "status", "已发布",
                        "deadline", "2026-07-02"
                )),
                "questions", List.of(Map.of(
                        "question_id", "question-001",
                        "title", "Spring Boot Controller 的职责是什么？",
                        "question_type", "单选题",
                        "difficulty", "简单"
                )),
                "project_tasks", List.of(Map.of(
                        "project_task_id", "project-001",
                        "title", "最小后端接口联调项目",
                        "status", "已发布"
                ))
        ));
    }
}
