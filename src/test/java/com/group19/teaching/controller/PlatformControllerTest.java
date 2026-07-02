package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.PlatformService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformController.class)
class PlatformControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformService platformService;

    @MockBean
    private AuthService authService;

    @Test
    void summaryReturnsCounts() throws Exception {
        when(platformService.summary(null, null)).thenReturn(Map.of("user_count", 3));

        mockMvc.perform(get("/api/platform/summary").header("token", "admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_count").value(3));
    }

    @Test
    void aiCallLogsReturnsPagedRows() throws Exception {
        when(platformService.listAiCallLogs(null, "QA", "mock-local", "SUCCESS", null, null, 1, 10))
                .thenReturn(Map.of(
                        "records", List.of(Map.of("log_id", "ai-1")),
                        "total", 1,
                        "page_no", 1,
                        "page_size", 10
                ));

        mockMvc.perform(get("/api/ai/call-logs")
                        .header("token", "admin-token")
                        .param("scene", "QA")
                        .param("model_name", "mock-local")
                        .param("status", "SUCCESS")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].log_id").value("ai-1"));
    }

    @Test
    void platformRejectsNonAdmin() throws Exception {
        when(authService.requireRole("student-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/platform/health").header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }
}
