package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.ProfileService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {

    private final ProfileService profileService;
    private final AuthService authService;

    public ProfileController(ProfileService profileService, AuthService authService) {
        this.profileService = profileService;
        this.authService = authService;
    }

    @GetMapping("/api/profiles/{studentId}")
    public ApiResponse<Map<String, Object>> get(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String studentId,
            @RequestParam("job_id") String jobId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(profileService.get(studentId, jobId, actor));
    }

    @PostMapping("/api/profiles/{studentId}/refresh")
    public ApiResponse<Map<String, Object>> refresh(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String studentId,
            @RequestParam("job_id") String jobId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(profileService.refresh(studentId, jobId, actor));
    }

    @GetMapping("/api/recommendations")
    public ApiResponse<Map<String, Object>> recommendations(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "student_id", required = false) String studentId,
            @RequestParam("job_id") String jobId,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(profileService.listRecommendations(studentId, jobId, pageNo, pageSize, actor));
    }

    @GetMapping("/api/profiles/classes/{classId}/analysis")
    public ApiResponse<Map<String, Object>> classAnalysis(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String classId,
            @RequestParam(value = "course_id", required = false) String courseId,
            @RequestParam(value = "job_id", required = false) String jobId) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(profileService.classAnalysis(classId, courseId, jobId, actor));
    }

    @GetMapping("/api/reports/classes/{classId}")
    public ResponseEntity<String> classReport(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String classId,
            @RequestParam(value = "course_id", required = false) String courseId,
            @RequestParam(value = "job_id", required = false) String jobId) {
        User actor = authService.requireRole(token, "TEACHER");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=class-report.csv")
                .contentType(new MediaType("text", "csv"))
                .body(profileService.classReportCsv(classId, courseId, jobId, actor));
    }
}
