package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.PracticeService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PracticeController {

    private final PracticeService practiceService;
    private final AuthService authService;

    public PracticeController(PracticeService practiceService, AuthService authService) {
        this.practiceService = practiceService;
        this.authService = authService;
    }

    @PostMapping("/api/practice/records")
    public ApiResponse<Map<String, Object>> submit(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, String> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(practiceService.submit(
                request.get("question_id"), request.get("answer"), request.get("scene"), request.get("job_id"), actor));
    }

    @GetMapping("/api/practice/records")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize,
            @RequestParam(value = "question_id", required = false) String questionId,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(practiceService.listRecords(
                pageNo, pageSize, questionId, startTime, endTime, actor));
    }
}
