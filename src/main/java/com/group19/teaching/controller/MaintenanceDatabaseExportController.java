package com.group19.teaching.controller;

import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.DatabaseExportFile;
import com.group19.teaching.service.DatabaseExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MaintenanceDatabaseExportController {

    private final DatabaseExportService databaseExportService;
    private final AuthService authService;

    public MaintenanceDatabaseExportController(DatabaseExportService databaseExportService, AuthService authService) {
        this.databaseExportService = databaseExportService;
        this.authService = authService;
    }

    @PostMapping("/api/maintenance/database/export")
    public ResponseEntity<byte[]> exportDatabase(
            @RequestHeader(value = "token", required = false) String token) {
        authService.requireRole(token, "EDU_ADMIN");
        DatabaseExportFile exportFile = databaseExportService.exportCurrentDatabaseAsXlsx();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + exportFile.filename())
                .contentType(MediaType.parseMediaType(exportFile.contentType()))
                .body(exportFile.content());
    }
}
