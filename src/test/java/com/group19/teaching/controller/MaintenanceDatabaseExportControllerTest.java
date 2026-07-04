package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.DatabaseExportFile;
import com.group19.teaching.service.DatabaseExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MaintenanceDatabaseExportController.class)
class MaintenanceDatabaseExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatabaseExportService databaseExportService;

    @MockBean
    private AuthService authService;

    @Test
    void exportReturnsXlsxAttachment() throws Exception {
        when(databaseExportService.exportCurrentDatabaseAsXlsx()).thenReturn(new DatabaseExportFile(
                "teaching-sys-db-20260704-120000.xlsx",
                new byte[]{'P', 'K', 3, 4},
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));

        mockMvc.perform(post("/api/maintenance/database/export")
                        .header("token", "admin-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=teaching-sys-db-20260704-120000.xlsx"))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(new byte[]{'P', 'K', 3, 4}));
    }

    @Test
    void exportRejectsNonAdmin() throws Exception {
        when(authService.requireRole("student-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/maintenance/database/export")
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }
}
