package com.group19.teaching.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.CourseService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsCoursePage() throws Exception {
        User teacher = user(2L, "teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(courseService.list(eq("Java"), eq("major-cs"), eq("已发布"), eq(1), eq(10), eq(teacher)))
                .thenReturn(Map.of(
                        "records", List.of(Map.of("course_id", "course-java-001", "course_name", "Java 后端")),
                        "total", 1,
                        "page_no", 1,
                        "page_size", 10
                ));

        mockMvc.perform(get("/api/courses")
                        .header("token", "teacher-token")
                        .param("keyword", "Java")
                        .param("major_id", "major-cs")
                        .param("status", "已发布")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].course_id").value("course-java-001"));
    }

    @Test
    void createReturnsCreatedCourse() throws Exception {
        User admin = user(9L, "admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(courseService.create(anyMap(), eq(admin))).thenReturn(Map.of(
                "course_id", "course-ai-001",
                "course_name", "AI 工程实践",
                "status", "已发布"
        ));

        mockMvc.perform(post("/api/courses")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_name\":\"AI 工程实践\",\"course_code\":\"AI-001\",\"major_id\":\"major-cs\",\"credit\":3,\"status\":\"已发布\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.course_id").value("course-ai-001"));
    }

    @Test
    void updateRejectsNonAdmin() throws Exception {
        when(authService.requireRole("teacher-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(put("/api/courses/course-java-001")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"停用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    @Test
    void detailReturnsCoursePageData() throws Exception {
        when(authService.requireRole("demo-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(user());
        when(courseService.detail(eq("course-java-001"), eq("class-java-001"), any(User.class))).thenReturn(Map.of(
                "course", Map.of("course_id", "course-java-001", "course_class_id", "class-java-001"),
                "chapters", List.of(Map.of("chapter_id", "chapter-java-001")),
                "materials", List.of(Map.of("material_id", "material-jg-001")),
                "pre_tasks", List.of(),
                "homeworks", List.of(),
                "questions", List.of(Map.of("question_id", "jg-q-001")),
                "project_tasks", List.of()
        ));
        mockMvc.perform(get("/api/courses/course-java-001")
                        .header("token", "demo-token")
                        .param("course_class_id", "class-java-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.course.course_id").value("course-java-001"))
                .andExpect(jsonPath("$.data.course.course_class_id").value("class-java-001"))
                .andExpect(jsonPath("$.data.chapters[0].chapter_id").value("chapter-java-001"))
                .andExpect(jsonPath("$.data.materials[0].material_id").value("material-jg-001"))
                .andExpect(jsonPath("$.data.questions[0].question_id").value("jg-q-001"));
    }

    @Test
    void detailRejectsMissingCourseClassId() throws Exception {
        mockMvc.perform(get("/api/courses/course-java-001")
                        .header("token", "demo-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40001"));
    }

    @Test
    void listChaptersReturnsRecords() throws Exception {
        User student = user();
        when(authService.requireRole("student-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(student);
        when(courseService.listChapters("course-java-001", student)).thenReturn(Map.of(
                "records", List.of(Map.of("chapter_id", "chapter-java-001", "status", "启用"))
        ));

        mockMvc.perform(get("/api/courses/course-java-001/chapters")
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].chapter_id").value("chapter-java-001"));
    }

    @Test
    void createChapterReturnsCreatedChapter() throws Exception {
        User teacher = user(2L, "teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(courseService.createChapter(eq("course-java-001"), anyMap(), eq(teacher))).thenReturn(Map.of(
                "chapter_id", "chapter-new",
                "course_id", "course-java-001",
                "status", "启用"
        ));

        mockMvc.perform(post("/api/courses/course-java-001/chapters")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapter_name\":\"新章节\",\"sort_order\":4,\"status\":\"启用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.chapter_id").value("chapter-new"));
    }

    private static User user() {
        return user(null, "student001", "STUDENT");
    }

    private static User user(Long id, String account, String role) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
