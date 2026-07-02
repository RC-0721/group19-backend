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
import com.group19.teaching.service.TeachingStandardService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeachingStandardController.class)
class TeachingStandardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeachingStandardService teachingStandardService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsTeachingStandardPage() throws Exception {
        User teacher = user(2L, "teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(teachingStandardService.list(eq("major-cs"), eq("course-java-001"), eq("课程目标"),
                eq("启用"), eq(1), eq(10), ArgumentMatchers.eq(teacher))).thenReturn(Map.of(
                "records", List.of(Map.of("standard_id", "standard-java-backend", "status", "启用")),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/teaching-standards")
                        .header("token", "teacher-token")
                        .param("major_id", "major-cs")
                        .param("course_id", "course-java-001")
                        .param("standard_type", "课程目标")
                        .param("status", "启用")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].standard_id").value("standard-java-backend"));
    }

    @Test
    void createReturnsCreatedTeachingStandard() throws Exception {
        User admin = user(9L, "admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(teachingStandardService.create(anyMap(), ArgumentMatchers.eq(admin))).thenReturn(Map.of(
                "standard_id", "standard-new",
                "status", "启用"
        ));

        mockMvc.perform(post("/api/teaching-standards")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"major_id\":\"major-cs\",\"course_id\":\"course-java-001\",\"standard_type\":\"课程目标\",\"title\":\"课程目标\",\"content\":\"掌握后端开发\",\"version\":\"2026\",\"status\":\"启用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.standard_id").value("standard-new"));
    }

    @Test
    void updateRejectsNonAdmin() throws Exception {
        when(authService.requireRole("teacher-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(put("/api/teaching-standards/standard-java-backend")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"停用\"}"))
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
