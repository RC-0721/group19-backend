package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.UserAdminService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserAdminService userAdminService;
    private final AuthService authService;

    public UserController(UserAdminService userAdminService, AuthService authService) {
        this.userAdminService = userAdminService;
        this.authService = authService;
    }

    @PutMapping("/{userId}")
    public ApiResponse<Map<String, Object>> updateUser(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(userAdminService.updateUser(userId, request, actor));
    }
}
