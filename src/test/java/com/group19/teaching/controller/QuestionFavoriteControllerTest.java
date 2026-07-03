package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.QuestionFavoriteService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuestionFavoriteController.class)
class QuestionFavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionFavoriteService questionFavoriteService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsFavorites() throws Exception {
        User student = user();
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(questionFavoriteService.list(1, 10, student)).thenReturn(Map.of(
                "records", List.of(Map.of(
                        "question_id", "q-1",
                        "stem", "题干",
                        "question_type", "单选题",
                        "difficulty", "简单"
                )),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/question-favorites")
                        .header("token", "student-token")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].question_id").value("q-1"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void addReturnsFavoritedQuestion() throws Exception {
        User student = user();
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(questionFavoriteService.add(ArgumentMatchers.eq("q-1"), ArgumentMatchers.eq(student))).thenReturn(Map.of(
                "question_id", "q-1",
                "favorited", true
        ));

        mockMvc.perform(post("/api/question-favorites")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"q-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.question_id").value("q-1"))
                .andExpect(jsonPath("$.data.favorited").value(true));
    }

    @Test
    void deleteReturnsUnfavoritedQuestion() throws Exception {
        User student = user();
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(questionFavoriteService.delete("q-1", student)).thenReturn(Map.of(
                "question_id", "q-1",
                "favorited", false
        ));

        mockMvc.perform(delete("/api/question-favorites/q-1")
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.favorited").value(false));
    }

    @Test
    void listRejectsNonStudentRole() throws Exception {
        when(authService.requireRole("teacher-token", "STUDENT"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/question-favorites")
                        .header("token", "teacher-token")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    private static User user() {
        User user = new User();
        user.setAccount("student001");
        user.setRole("STUDENT");
        return user;
    }
}
