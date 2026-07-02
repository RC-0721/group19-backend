package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.CourseClassService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/course-classes")
public class CourseClassController {

    private final CourseClassService courseClassService;
    private final AuthService authService;

    public CourseClassController(CourseClassService courseClassService, AuthService authService) {
        this.courseClassService = courseClassService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "course_id", required = false) String courseId,
            @RequestParam(value = "teacher_id", required = false) String teacherId,
            @RequestParam(value = "class_id", required = false) String classId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(courseClassService.list(courseId, teacherId, classId, status, pageNo, pageSize, actor));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(courseClassService.create(request, actor));
    }

    @PutMapping("/{courseClassId}")
    public ApiResponse<Map<String, Object>> update(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String courseClassId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(courseClassService.update(courseClassId, request, actor));
    }
}
