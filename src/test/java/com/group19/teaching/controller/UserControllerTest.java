package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
