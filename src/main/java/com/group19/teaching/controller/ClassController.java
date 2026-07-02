package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.ClassService;
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
@RequestMapping("/api/classes")
public class ClassController {

    private final ClassService classService;
    private final AuthService authService;

    public ClassController(ClassService classService, AuthService authService) {
        this.classService = classService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "major_id", required = false) String majorId,
            @RequestParam(value = "grade", required = false) String grade,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(classService.list(majorId, grade, status, pageNo, pageSize, actor));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(classService.create(request, actor));
    }

    @PutMapping("/{classId}")
    public ApiResponse<Map<String, Object>> update(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String classId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(classService.update(classId, request, actor));
    }
}
