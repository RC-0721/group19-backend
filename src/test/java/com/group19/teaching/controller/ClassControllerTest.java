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
import com.group19.teaching.service.ClassService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClassController.class)
class ClassControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClassService classService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsClassPage() throws Exception {
        User teacher = user(2L, "teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(classService.list(eq("major-cs"), eq("2026"), eq("启用"), eq(1), eq(10),
                ArgumentMatchers.eq(teacher))).thenReturn(Map.of(
                "records", List.of(Map.of("class_id", "class-cs-2026", "class_name", "计软 2026 班")),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/classes")
                        .header("token", "teacher-token")
                        .param("major_id", "major-cs")
                        .param("grade", "2026")
                        .param("status", "启用")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].class_id").value("class-cs-2026"));
    }

    @Test
    void createReturnsCreatedClass() throws Exception {
        User admin = user(9L, "admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(classService.create(anyMap(), ArgumentMatchers.eq(admin))).thenReturn(Map.of(
                "class_id", "class-ai-2026",
                "major_id", "major-cs",
                "class_name", "智能 2026 班",
                "status", "启用"
        ));

        mockMvc.perform(post("/api/classes")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"major_id\":\"major-cs\",\"class_name\":\"智能 2026 班\",\"grade\":\"2026\",\"status\":\"启用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.class_id").value("class-ai-2026"));
    }

    @Test
    void createRejectsNonAdmin() throws Exception {
        when(authService.requireRole("teacher-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/classes")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"major_id\":\"major-cs\",\"class_name\":\"智能 2026 班\",\"status\":\"启用\"}"))
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
