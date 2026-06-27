package com.group19.teaching.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void detailReturnsCoursePageData() throws Exception {
        mockMvc.perform(get("/api/courses/course-java-001").param("course_class_id", "class-java-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.course.course_id").value("course-java-001"))
                .andExpect(jsonPath("$.data.course.course_class_id").value("class-java-001"))
                .andExpect(jsonPath("$.data.chapters[0].chapter_id").value("chapter-001"))
                .andExpect(jsonPath("$.data.materials[0].material_id").value("material-001"))
                .andExpect(jsonPath("$.data.questions[0].question_id").value("question-001"));
    }
}
