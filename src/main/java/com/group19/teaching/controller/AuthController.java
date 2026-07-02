package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.dto.LoginRequest;
import com.group19.teaching.domain.vo.LoginResponse;
import com.group19.teaching.service.AuthService;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader(value = "token", required = false) String token) {
        return ApiResponse.success(authService.currentUser(token));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestHeader(value = "token", required = false) String token) {
        return ApiResponse.success(authService.refresh(token));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Boolean>> logout(@RequestHeader(value = "token", required = false) String token) {
        authService.logout(token);
        return ApiResponse.success(Map.of("logged_out", true));
    }
}
