package com.group19.teaching.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.service.CourseService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @Test
    void detailReturnsCoursePageData() throws Exception {
        when(courseService.detail("course-java-001", "class-java-001")).thenReturn(Map.of(
                "course", Map.of("course_id", "course-java-001", "course_class_id", "class-java-001"),
                "chapters", List.of(Map.of("chapter_id", "chapter-java-001")),
                "materials", List.of(Map.of("material_id", "material-jg-001")),
                "pre_tasks", List.of(),
                "homeworks", List.of(),
                "questions", List.of(Map.of("question_id", "jg-q-001")),
                "project_tasks", List.of()
        ));
        mockMvc.perform(get("/api/courses/course-java-001").param("course_class_id", "class-java-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.course.course_id").value("course-java-001"))
                .andExpect(jsonPath("$.data.course.course_class_id").value("class-java-001"))
                .andExpect(jsonPath("$.data.chapters[0].chapter_id").value("chapter-java-001"))
                .andExpect(jsonPath("$.data.materials[0].material_id").value("material-jg-001"))
                .andExpect(jsonPath("$.data.questions[0].question_id").value("jg-q-001"));
    }
}
