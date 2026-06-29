package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.WrongBookService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wrong-book")
public class WrongBookController {

    private final WrongBookService wrongBookService;
    private final AuthService authService;

    public WrongBookController(WrongBookService wrongBookService, AuthService authService) {
        this.wrongBookService = wrongBookService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(wrongBookService.list(actor.getAccount(), pageNo, pageSize));
    }
}
