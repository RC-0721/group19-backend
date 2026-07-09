package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.HomeworkService;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
public class HomeworkController {

    private final HomeworkService homeworkService;
    private final AuthService authService;

    public HomeworkController(HomeworkService homeworkService, AuthService authService) {
        this.homeworkService = homeworkService;
        this.authService = authService;
    }

    @GetMapping("/api/homeworks")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "course_class_id", required = false) String courseClassId,
            @RequestParam(value = "class_id", required = false) String classId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(homeworkService.list(courseClassId, classId, status, pageNo, pageSize, actor));
    }

    @PostMapping("/api/homeworks")
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(homeworkService.create(request, actor));
    }

    @PostMapping("/api/homeworks/{homeworkId}/submits")
    public ApiResponse<Map<String, Object>> submit(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String homeworkId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(homeworkService.submit(homeworkId, request, actor));
    }

    @PostMapping("/api/homeworks/{homeworkId}/files")
    public ApiResponse<Map<String, Object>> uploadFile(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String homeworkId,
            @RequestParam("file") MultipartFile file) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(homeworkService.uploadFile(homeworkId, file, actor));
    }

    @GetMapping("/api/homeworks/{homeworkId}/submits")
    public ApiResponse<Map<String, Object>> listSubmits(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String homeworkId,
            @RequestParam(value = "submit_status", required = false) String submitStatus,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        if ("EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            return ApiResponse.success(homeworkService.listSubmissions(
                    homeworkId, null, null, submitStatus, pageNo, pageSize, actor));
        }
        return ApiResponse.success(homeworkService.listSubmits(homeworkId, submitStatus, pageNo, pageSize, actor));
    }

    @GetMapping({"/api/homework-submits", "/api/homework-submissions", "/api/platform/homework-submissions"})
    public ApiResponse<Map<String, Object>> listSubmissions(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "homework_id", required = false) String homeworkId,
            @RequestParam(value = "class_id", required = false) String classId,
            @RequestParam(value = "course_class_id", required = false) String courseClassId,
            @RequestParam(value = "submit_status", required = false) String submitStatus,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(homeworkService.listSubmissions(
                homeworkId, classId, courseClassId, submitStatus, pageNo, pageSize, actor));
    }

    @PutMapping("/api/homework-reviews/{reviewId}")
    public ApiResponse<Map<String, Object>> review(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String reviewId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(homeworkService.review(reviewId, request, actor));
    }

    @PostMapping("/api/homework-submits/{submitId}/appeals")
    public ApiResponse<Map<String, Object>> appeal(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String submitId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(homeworkService.appeal(submitId, request, actor));
    }

    @PutMapping("/api/homework-appeals/{appealId}")
    public ApiResponse<Map<String, Object>> reviewAppeal(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String appealId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(homeworkService.reviewAppeal(appealId, request, actor));
    }
}
