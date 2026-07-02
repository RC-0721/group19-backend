package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.domain.vo.LoginResponse;
import com.group19.teaching.service.AuthService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void loginReturnsUnifiedSuccessResponse() throws Exception {
        given(authService.login(any())).willReturn(new LoginResponse("token-1", "1", "学生一", "STUDENT", List.of()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"student001\",\"password\":\"123456\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.token").value("token-1"))
                .andExpect(jsonPath("$.data.user_id").value("1"));
    }

    @Test
    void loginRejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123456\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40001"))
                .andExpect(jsonPath("$.message").value("参数错误"));
    }

    @Test
    void logoutInvalidatesToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header("token", "token-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.logged_out").value(true));

        verify(authService).logout("token-1");
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        given(authService.currentUser("token-1")).willReturn(Map.of(
                "user_id", "1",
                "account", "student001",
                "name", "学生一",
                "role", "STUDENT",
                "status", "ENABLED",
                "permission_scope", "ALL",
                "menus", List.of(),
                "profile", Map.of("student_id", "student001")
        ));

        mockMvc.perform(get("/api/auth/me").header("token", "token-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.user_id").value("1"))
                .andExpect(jsonPath("$.data.account").value("student001"))
                .andExpect(jsonPath("$.data.profile.student_id").value("student001"));
    }

    @Test
    void refreshReturnsNewToken() throws Exception {
        given(authService.refresh("token-1")).willReturn(Map.of(
                "token", "token-2",
                "expires_in", 7200,
                "user_id", "1",
                "role", "STUDENT"
        ));

        mockMvc.perform(post("/api/auth/refresh").header("token", "token-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.token").value("token-2"))
                .andExpect(jsonPath("$.data.expires_in").value(7200))
                .andExpect(jsonPath("$.data.user_id").value("1"));
    }
}
