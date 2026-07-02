package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.OperationLogService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OperationLogController.class)
class OperationLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OperationLogService operationLogService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsOperationLogs() throws Exception {
        when(operationLogService.list("9", "USER", "UPDATE_USER", null, null, 1, 10)).thenReturn(Map.of(
                "records", List.of(Map.of("log_id", "op-1", "user_id", "9")),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/logs/operations")
                        .header("token", "admin-token")
                        .param("user_id", "9")
                        .param("module", "USER")
                        .param("operation_type", "UPDATE_USER")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].log_id").value("op-1"));
    }

    @Test
    void listRejectsNonAdmin() throws Exception {
        when(authService.requireRole("student-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/logs/operations")
                        .header("token", "student-token")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    @Test
    void exportReturnsCsv() throws Exception {
        when(operationLogService.exportCsv("9", null, null, null, null))
                .thenReturn("log_id,user_id\nop-1,9\n");

        mockMvc.perform(get("/api/logs/operations/export")
                        .header("token", "admin-token")
                        .param("user_id", "9"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(content().string("log_id,user_id\nop-1,9\n"));
    }
}
