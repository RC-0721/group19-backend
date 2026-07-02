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
import com.group19.teaching.service.CourseClassService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseClassController.class)
class CourseClassControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseClassService courseClassService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsCourseClassPage() throws Exception {
        User student = user(1L, "student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(student);
        when(courseClassService.list(eq("course-java-001"), eq("teacher001"), eq("class-cs-2026"),
                eq("开课中"), eq(1), eq(10), ArgumentMatchers.eq(student))).thenReturn(Map.of(
                "records", List.of(Map.of("course_class_id", "class-java-001", "status", "开课中")),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/course-classes")
                        .header("token", "student-token")
                        .param("course_id", "course-java-001")
                        .param("teacher_id", "teacher001")
                        .param("class_id", "class-cs-2026")
                        .param("status", "开课中")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].course_class_id").value("class-java-001"));
    }

    @Test
    void createReturnsCreatedCourseClass() throws Exception {
        User admin = user(9L, "admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(courseClassService.create(anyMap(), ArgumentMatchers.eq(admin))).thenReturn(Map.of(
                "course_class_id", "cc-new",
                "course_id", "course-java-001",
                "class_id", "class-cs-2026",
                "teacher_id", "teacher001",
                "status", "开课中"
        ));

        mockMvc.perform(post("/api/course-classes")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":\"course-java-001\",\"class_id\":\"class-cs-2026\",\"teacher_id\":\"teacher001\",\"semester\":\"2026 春季\",\"status\":\"开课中\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.course_class_id").value("cc-new"));
    }

    @Test
    void updateRejectsNonAdmin() throws Exception {
        when(authService.requireRole("teacher-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(put("/api/course-classes/class-java-001")
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
