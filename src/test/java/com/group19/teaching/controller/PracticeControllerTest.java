package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.PracticeService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PracticeController.class)
class PracticeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PracticeService practiceService;

    @MockBean
    private AuthService authService;

    @Test
    void submitReturnsPracticeResult() throws Exception {
        User student = user();
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(practiceService.submit("q-1", "A", "daily", "job-java", student)).thenReturn(Map.of(
                "record_id", "practice-1",
                "is_correct", true,
                "score", 100,
                "answer_analysis", "解析",
                "wrong_book_status", "无需记录"
        ));

        mockMvc.perform(post("/api/practice/records")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"q-1\",\"answer\":\"A\",\"scene\":\"daily\",\"job_id\":\"job-java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.record_id").value("practice-1"))
                .andExpect(jsonPath("$.data.is_correct").value(true))
                .andExpect(jsonPath("$.data.score").value(100));
    }

    @Test
    void submitRejectsNonStudentRole() throws Exception {
        when(authService.requireRole("teacher-token", "STUDENT"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/practice/records")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"q-1\",\"answer\":\"A\",\"scene\":\"daily\",\"job_id\":\"job-java\"}"))
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
