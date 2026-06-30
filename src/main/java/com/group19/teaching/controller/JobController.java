package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.JobService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobController {

    private final JobService jobService;
    private final AuthService authService;

    public JobController(JobService jobService, AuthService authService) {
        this.jobService = jobService;
        this.authService = authService;
    }

    @GetMapping("/api/jobs")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "job_id", required = false) String jobId,
            @RequestParam(value = "tech_id", required = false) String techId,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(jobService.list(jobId, techId, pageNo, pageSize));
    }

    @PostMapping("/api/jobs")
    public ApiResponse<Map<String, Object>> save(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(jobService.save(request));
    }
}
