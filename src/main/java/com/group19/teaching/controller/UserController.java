package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.config.RequireRole;
import com.group19.teaching.config.RequireRoleInterceptor;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.UserAdminService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequireRole("EDU_ADMIN")
public class UserController {

    private final UserAdminService userAdminService;

    public UserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        return ApiResponse.success(userAdminService.list(role, status, keyword, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createUser(
            @RequestAttribute(RequireRoleInterceptor.CURRENT_USER_ATTRIBUTE) User actor,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.success(userAdminService.createUser(request, actor));
    }

    @PutMapping("/{userId}")
    public ApiResponse<Map<String, Object>> updateUser(
            @RequestAttribute(RequireRoleInterceptor.CURRENT_USER_ATTRIBUTE) User actor,
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.success(userAdminService.updateUser(userId, request, actor));
    }
}
