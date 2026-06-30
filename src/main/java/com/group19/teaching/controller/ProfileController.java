package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.ProfileService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/api/profiles/classes/{classId}/analysis")
    public ApiResponse<Map<String, Object>> classAnalysis(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String classId,
            @RequestParam(value = "course_id", required = false) String courseId,
            @RequestParam(value = "job_id", required = false) String jobId) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(profileService.classAnalysis(classId, courseId, jobId, actor));
    }
}
