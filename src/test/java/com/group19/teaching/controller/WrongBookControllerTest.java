package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.WrongBookService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WrongBookController.class)
class WrongBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WrongBookService wrongBookService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsWrongBookData() throws Exception {
        User student = user();
        when(authService.requireRole("student-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(student);
        when(wrongBookService.list(
                ArgumentMatchers.eq("kp-1"),
                ArgumentMatchers.eq("job-1"),
                ArgumentMatchers.eq("未掌握"),
                ArgumentMatchers.eq(1),
                ArgumentMatchers.eq(10),
                ArgumentMatchers.eq(student))).thenReturn(Map.of(
                "records", List.of(Map.of(
                        "question_id", "q-1",
                        "stem", "题干",
                        "wrong_count", 2,
                        "master_status", "未掌握"
                )),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/wrong-book")
                        .header("token", "student-token")
                        .param("knowledge_id", "kp-1")
                        .param("job_id", "job-1")
                        .param("wrong_book_status", "未掌握")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].question_id").value("q-1"))
                .andExpect(jsonPath("$.data.records[0].wrong_count").value(2))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void listRejectsDisallowedRole() throws Exception {
        when(authService.requireRole("bad-token", "STUDENT", "TEACHER", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/wrong-book")
                        .header("token", "bad-token")
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
