package com.group19.teaching.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseExportService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILENAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JdbcTemplate jdbcTemplate;

    public DatabaseExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DatabaseExportFile exportCurrentDatabaseAsXlsx() {
        List<TableSheet> sheets = readTables();
        if (sheets == null) {
            sheets = List.of();
        }
        if (sheets.isEmpty()) {
            sheets = List.of(new TableSheet("__empty_database__", "empty_database"));
        }
        byte[] content = buildWorkbook(sheets);
        String filename = "teaching-sys-db-" + LocalDateTime.now().format(FILENAME_TIME) + ".xlsx";
        return new DatabaseExportFile(filename, content, XLSX_CONTENT_TYPE);
    }

    private List<TableSheet> readTables() {
        return jdbcTemplate.execute((Connection connection) -> {
            List<String> tableNames = new ArrayList<>();
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (tableName != null) {
                        tableNames.add(tableName);
                    }
                }
            }
            tableNames.sort(String.CASE_INSENSITIVE_ORDER);
            Set<String> usedSheetNames = new HashSet<>();
            List<TableSheet> sheets = new ArrayList<>();
            for (String tableName : tableNames) {
                sheets.add(new TableSheet(tableName, uniqueSheetName(tableName, usedSheetNames)));
            }
            return sheets;
        });
    }

    private byte[] buildWorkbook(List<TableSheet> sheets) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            writeEntry(zip, "[Content_Types].xml", contentTypesXml(sheets.size()));
            writeEntry(zip, "_rels/.rels", rootRelationshipsXml());
            writeEntry(zip, "docProps/core.xml", corePropertiesXml());
            writeEntry(zip, "docProps/app.xml", appPropertiesXml());
            writeEntry(zip, "xl/workbook.xml", workbookXml(sheets));
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml(sheets.size()));
            writeEntry(zip, "xl/styles.xml", stylesXml());
            for (int index = 0; index < sheets.size(); index++) {
                writeEntry(zip, "xl/worksheets/sheet" + (index + 1) + ".xml",
                        worksheetXml(sheets.get(index).tableName()));
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build database export workbook", exception);
        }
    }

    private String worksheetXml(String tableName) {
        if ("__empty_database__".equals(tableName)) {
            return """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <sheetData>
                        <row r="1"><c r="A1" t="inlineStr"><is><t>message</t></is></c></row>
                        <row r="2"><c r="A2" t="inlineStr"><is><t>current database has no tables</t></is></c></row>
                      </sheetData>
                    </worksheet>
                    """;
        }
        return jdbcTemplate.query("SELECT * FROM " + quoteIdentifier(tableName), resultSet -> {
            StringBuilder xml = new StringBuilder(8192);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                    .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
                    .append("  <sheetData>\n");
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            appendRow(xml, 1, columnNames(metaData, columnCount));
            int rowIndex = 2;
            while (resultSet.next()) {
                List<String> values = new ArrayList<>(columnCount);
                for (int column = 1; column <= columnCount; column++) {
                    values.add(cellValue(resultSet.getObject(column)));
                }
                appendRow(xml, rowIndex++, values);
            }
            xml.append("  </sheetData>\n</worksheet>\n");
            return xml.toString();
        });
    }

    private List<String> columnNames(ResultSetMetaData metaData, int columnCount) throws SQLException {
        List<String> columns = new ArrayList<>(columnCount);
        for (int column = 1; column <= columnCount; column++) {
            columns.add(metaData.getColumnLabel(column));
        }
        return columns;
    }

    private void appendRow(StringBuilder xml, int rowIndex, List<String> values) {
        xml.append("    <row r=\"").append(rowIndex).append("\">");
        for (int index = 0; index < values.size(); index++) {
            String cellRef = columnName(index + 1) + rowIndex;
            xml.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escapeXml(values.get(index)))
                    .append("</t></is></c>");
        }
        xml.append("</row>\n");
    }

    private String cellValue(Object value) throws SQLException {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return fitCellValue(Base64.getEncoder().encodeToString(bytes));
        }
        if (value instanceof Blob blob) {
            return fitCellValue(Base64.getEncoder().encodeToString(blob.getBytes(1, (int) blob.length())));
        }
        if (value instanceof Clob clob) {
            return fitCellValue(clob.getSubString(1, (int) clob.length()));
        }
        return fitCellValue(String.valueOf(value));
    }

    private String fitCellValue(String value) {
        return value.length() > 32767 ? value.substring(0, 32767) : value;
    }

    private String uniqueSheetName(String tableName, Set<String> used) {
        String base = sanitizeSheetName(tableName);
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
            String tail = "_" + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 31 - tail.length())) + tail;
        }
        return candidate;
    }

    private String sanitizeSheetName(String tableName) {
        String cleaned = tableName.replaceAll("[\\\\/*?:\\[\\]]", "_").trim();
        if (cleaned.isEmpty()) {
            cleaned = "sheet";
        }
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String columnName(int index) {
        StringBuilder value = new StringBuilder();
        int current = index;
        while (current > 0) {
            current--;
            value.insert(0, (char) ('A' + current % 26));
            current /= 26;
        }
        return value.toString();
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypesXml(int sheetCount) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                """);
        for (int index = 1; index <= sheetCount; index++) {
            xml.append("  <Override PartName=\"/xl/worksheets/sheet").append(index)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n");
        }
        xml.append("</Types>\n");
        return xml.toString();
    }

    private String rootRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
                </Relationships>
                """;
    }

    private String workbookXml(List<TableSheet> sheets) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                """);
        for (int index = 0; index < sheets.size(); index++) {
            xml.append("    <sheet name=\"").append(escapeXml(sheets.get(index).sheetName()))
                    .append("\" sheetId=\"").append(index + 1)
                    .append("\" r:id=\"rId").append(index + 1).append("\"/>\n");
        }
        xml.append("  </sheets>\n</workbook>\n");
        return xml.toString();
    }

    private String workbookRelationshipsXml(int sheetCount) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                """);
        for (int index = 1; index <= sheetCount; index++) {
            xml.append("  <Relationship Id=\"rId").append(index)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(index).append(".xml\"/>\n");
        }
        xml.append("  <Relationship Id=\"rId").append(sheetCount + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n")
                .append("</Relationships>\n");
        return xml.toString();
    }

    private String stylesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
                  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
                  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
                </styleSheet>
                """;
    }

    private String corePropertiesXml() {
        String now = java.time.OffsetDateTime.now().toString();
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <dc:creator>teaching-sys-backend</dc:creator>
                  <cp:lastModifiedBy>teaching-sys-backend</cp:lastModifiedBy>
                  <dcterms:created xsi:type="dcterms:W3CDTF">""" + now + """
                </dcterms:created>
                  <dcterms:modified xsi:type="dcterms:W3CDTF">""" + now + """
                </dcterms:modified>
                </cp:coreProperties>
                """;
    }

    private String appPropertiesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
                  <Application>teaching-sys-backend</Application>
                </Properties>
                """;
    }

    private String escapeXml(String value) {
        StringBuilder escaped = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (!isValidXmlCodePoint(codePoint)) {
                return;
            }
            switch (codePoint) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.appendCodePoint(codePoint);
            }
        });
        return escaped.toString();
    }

    private boolean isValidXmlCodePoint(int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xA
                || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    private record TableSheet(String tableName, String sheetName) {
    }
}
