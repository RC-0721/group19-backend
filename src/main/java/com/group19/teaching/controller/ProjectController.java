package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.ProjectService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController {

    private final ProjectService projectService;
    private final AuthService authService;

    public ProjectController(ProjectService projectService, AuthService authService) {
        this.projectService = projectService;
        this.authService = authService;
    }

    @GetMapping("/api/projects")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "course_id", required = false) String courseId,
            @RequestParam(value = "job_id", required = false) String jobId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(projectService.list(courseId, jobId, status, pageNo, pageSize, actor));
    }

    @PostMapping("/api/project-standards")
    public ApiResponse<Map<String, Object>> createStandard(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(projectService.createStandard(request));
    }

    @PostMapping("/api/project-standards/{standardId}/rubric-dimensions")
    public ApiResponse<Map<String, Object>> createRubricDimension(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String standardId,
            @RequestBody Map<String, Object> request) {
        authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(projectService.createRubricDimension(standardId, request));
    }

    @PostMapping("/api/projects")
    public ApiResponse<Map<String, Object>> createProject(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(projectService.createProject(request, actor));
    }

    @PostMapping("/api/projects/{projectTaskId}/submissions")
    public ApiResponse<Map<String, Object>> submitProject(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String projectTaskId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(projectService.submitProject(projectTaskId, request, actor));
    }

    @GetMapping("/api/projects/{projectTaskId}/submissions")
    public ApiResponse<Map<String, Object>> listSubmissions(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String projectTaskId,
            @RequestParam(value = "submit_status", required = false) String submitStatus,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(projectService.listSubmissions(projectTaskId, submitStatus, pageNo, pageSize, actor));
    }

    @PutMapping("/api/project-evaluations/{evaluationId}")
    public ApiResponse<Map<String, Object>> confirmEvaluation(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String evaluationId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(projectService.confirmEvaluation(evaluationId, request, actor));
    }
}
