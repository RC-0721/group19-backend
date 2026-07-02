package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.UserAdminService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private AuthService authService;

    @Test
    void listUsersReturnsPageForAdmin() throws Exception {
        User admin = user(9L, "admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(userAdminService.list("STUDENT", "ENABLED", "student", 1, 10)).thenReturn(Map.of(
                "records", java.util.List.of(Map.of(
                        "user_id", "1",
                        "account", "student001",
                        "role", "STUDENT",
                        "status", "ENABLED"
                )),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/users")
                        .header("token", "admin-token")
                        .param("role", "STUDENT")
                        .param("status", "ENABLED")
                        .param("keyword", "student")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].account").value("student001"))
                .andExpect(jsonPath("$.data.records[0].password_hash").doesNotExist());
    }

    @Test
    void updateUserReturnsUpdatedState() throws Exception {
        User admin = user(9L, "admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(userAdminService.updateUser(eq(2L), anyMap(), ArgumentMatchers.eq(admin))).thenReturn(Map.of(
                "user_id", "2",
                "role", "STUDENT",
                "status", "DISABLED",
                "permission_scope", "ALL"
        ));

        mockMvc.perform(put("/api/users/2")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"status\":\"DISABLED\",\"permission_scope\":\"ALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.user_id").value("2"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void createUserReturnsCreatedState() throws Exception {
        User admin = user(9L, "admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(userAdminService.createUser(anyMap(), ArgumentMatchers.eq(admin))).thenReturn(Map.of(
                "user_id", "10",
                "role", "STUDENT",
                "status", "ENABLED",
                "permission_scope", "ALL"
        ));

        mockMvc.perform(post("/api/users")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "student002",
                                  "password": "123456",
                                  "name": "学生二",
                                  "role": "STUDENT",
                                  "status": "ENABLED",
                                  "permission_scope": "ALL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.user_id").value("10"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.password_hash").doesNotExist());
    }

    @Test
    void updateUserRejectsNonAdmin() throws Exception {
        when(authService.requireRole("teacher-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(put("/api/users/2")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"status\":\"DISABLED\",\"permission_scope\":\"ALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    private static User user(Long id, String account, String role) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
