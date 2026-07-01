package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.MajorService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MajorController.class)
class MajorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MajorService majorService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsMajorPage() throws Exception {
        User admin = user(9L, "admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(admin);
        when(majorService.list(eq("计算机"), eq("启用"), eq(1), eq(10), ArgumentMatchers.eq(admin))).thenReturn(Map.of(
                "records", List.of(Map.of("major_id", "major-cs", "major_name", "计算机科学与技术", "status", "启用")),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/majors")
                        .header("token", "admin-token")
                        .param("keyword", "计算机")
                        .param("status", "启用")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].major_id").value("major-cs"));
    }

    @Test
    void createReturnsCreatedMajor() throws Exception {
        User admin = user(9L, "admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(majorService.create(anyMap(), ArgumentMatchers.eq(admin))).thenReturn(Map.of(
                "major_id", "major-ai",
                "major_name", "人工智能",
                "status", "启用"
        ));

        mockMvc.perform(post("/api/majors")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"major_name\":\"人工智能\",\"major_code\":\"AI\",\"status\":\"启用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.major_id").value("major-ai"));
    }

    @Test
    void createRejectsNonAdmin() throws Exception {
        when(authService.requireRole("teacher-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/majors")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"major_name\":\"人工智能\",\"major_code\":\"AI\",\"status\":\"启用\"}"))
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
