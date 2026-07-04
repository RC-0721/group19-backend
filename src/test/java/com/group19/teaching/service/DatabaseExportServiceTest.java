package com.group19.teaching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseExportServiceTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exportCurrentDatabaseAsXlsxBuildsValidWorkbookForEmptyDatabase() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(List.of());
        DatabaseExportService service = new DatabaseExportService(jdbcTemplate);

        DatabaseExportFile exportFile = service.exportCurrentDatabaseAsXlsx();

        assertThat(exportFile.filename()).startsWith("teaching-sys-db-").endsWith(".xlsx");
        assertThat(exportFile.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        Map<String, byte[]> entries = unzip(exportFile.content());
        assertThat(entries).containsKeys("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml");
        parseXml(entries.get("[Content_Types].xml"));
        parseXml(entries.get("xl/workbook.xml"));
        parseXml(entries.get("xl/worksheets/sheet1.xml"));
        assertThat(new String(entries.get("xl/worksheets/sheet1.xml"), java.nio.charset.StandardCharsets.UTF_8))
                .contains("current database has no tables");
    }

    private Map<String, byte[]> unzip(byte[] content) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private void parseXml(byte[] content) throws Exception {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(content));
    }
}
