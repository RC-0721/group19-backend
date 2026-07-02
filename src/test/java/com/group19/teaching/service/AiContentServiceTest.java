package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group19.teaching.domain.entity.User;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class AiContentServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiContentService aiContentService = new AiContentService(jdbcTemplate, objectMapper);

    @TempDir
    private Path tempDir;

    @Test
    void tikaExtractsTxtAndDocx() throws Exception {
        Path txt = tempDir.resolve("note.txt");
        Files.writeString(txt, "Java Spring material", StandardCharsets.UTF_8);
        assertTrue(aiContentService.extractText(txt).contains("Java Spring"));

        Path docx = tempDir.resolve("note.docx");
        writeMinimalDocx(docx, "Docx Java material");
        assertTrue(aiContentService.extractText(docx).contains("Docx Java material"));
    }

    @Test
    void parseMaterialStoresPendingConfirmationResult() throws Exception {
        Path txt = tempDir.resolve("note.md");
        Files.writeString(txt, "Java Spring Boot course material for backend students.", StandardCharsets.UTF_8);
        when(jdbcTemplate.queryForList(contains("FROM course_material"), eq("material-1")))
                .thenReturn(List.of(material(txt, "md")));
        when(jdbcTemplate.queryForObject(contains("FROM course_class"), eq(Integer.class),
                eq("course-java-001"), eq("teacher001"))).thenReturn(1);

        Map<String, Object> result = aiContentService.parseMaterial(Map.of("material_id", "material-1"), teacher());

        assertEquals("待确认", result.get("task_status"));
        verify(jdbcTemplate).update(contains("SET task_status = '待确认'"), any(), any(), any());
        verify(jdbcTemplate).update("UPDATE course_material SET parse_status = '待确认' WHERE material_id = ?", "material-1");
    }

    @Test
    void parseMaterialFailureIsStoredAsFailedTask() {
        Path missing = tempDir.resolve("missing.pdf");
        when(jdbcTemplate.queryForList(contains("FROM course_material"), eq("material-1")))
                .thenReturn(List.of(material(missing, "pdf")));
        when(jdbcTemplate.queryForObject(contains("FROM course_class"), eq(Integer.class),
                eq("course-java-001"), eq("teacher001"))).thenReturn(1);

        Map<String, Object> result = aiContentService.parseMaterial(Map.of("material_id", "material-1"), teacher());

        assertEquals("解析失败", result.get("task_status"));
        verify(jdbcTemplate).update(contains("SET task_status = '解析失败'"), any(), any(), any());
        verify(jdbcTemplate).update("UPDATE course_material SET parse_status = '解析失败' WHERE material_id = ?", "material-1");
    }

    @Test
    void confirmParseTaskWritesMaterialFieldsAndUnboundChunks() throws Exception {
        String resultJson = objectMapper.writeValueAsString(Map.of(
                "summary", "Java summary",
                "category", "文本资料",
                "tags", List.of("java", "spring"),
                "chunks", List.of(Map.of("chunk_text", "Java chunk"))
        ));
        when(jdbcTemplate.queryForList(contains("FROM material_parse_task"), eq("parse-1")))
                .thenReturn(List.of(Map.of(
                        "parse_task_id", "parse-1",
                        "material_id", "material-1",
                        "course_id", "course-java-001",
                        "chapter_id", "chapter-java-001",
                        "task_status", "待确认",
                        "result_json", resultJson
                )));
        when(jdbcTemplate.queryForObject(contains("FROM course_class"), eq(Integer.class),
                eq("course-java-001"), eq("teacher001"))).thenReturn(1);

        Map<String, Object> result = aiContentService.confirmParseTask("parse-1", teacher());

        assertEquals("已确认", result.get("task_status"));
        verify(jdbcTemplate).update(contains("UPDATE course_material"), any(), eq("文本资料"),
                eq("Java summary"), eq("material-1"));
        verify(jdbcTemplate).update(contains("INSERT INTO knowledge_chunk"),
                any(), eq("material-1"), eq("Java chunk"));
    }

    @Test
    void approveQuestionCandidateWritesFormalQuestionOptionsAndRelations() throws Exception {
        String optionsJson = objectMapper.writeValueAsString(List.of(
                Map.of("option_label", "A", "option_content", "正确选项", "is_correct", true),
                Map.of("option_label", "B", "option_content", "错误选项", "is_correct", false)
        ));
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("candidate_id", "qcand-1");
        candidate.put("material_id", "material-1");
        candidate.put("course_id", "course-java-001");
        candidate.put("chapter_id", "chapter-java-001");
        candidate.put("question_type", "单选题");
        candidate.put("stem", "Java 是什么？");
        candidate.put("difficulty", "中等");
        candidate.put("options_json", optionsJson);
        candidate.put("answer", "A");
        candidate.put("answer_analysis", "解析");
        candidate.put("knowledge_id", "kp-001");
        candidate.put("job_id", "job-java-backend");
        candidate.put("tech_id", "tech-001");
        candidate.put("audit_status", "待审核");
        when(jdbcTemplate.queryForList(contains("FROM question_candidate"), eq("qcand-1")))
                .thenReturn(List.of(candidate));
        when(jdbcTemplate.queryForObject(contains("FROM course_class"), eq(Integer.class),
                eq("course-java-001"), eq("teacher001"))).thenReturn(1);

        Map<String, Object> result = aiContentService.approveQuestionCandidate("qcand-1", teacher());

        assertEquals("已通过", result.get("audit_status"));
        verify(jdbcTemplate).update(contains("INSERT INTO question"), any(), eq("material-1"),
                eq("单选题"), eq("Java 是什么？"), eq("中等"), eq("A"), eq("解析"));
        verify(jdbcTemplate).update(contains("INSERT INTO question_option"), any(), any(), eq("A"), eq("正确选项"), eq(true));
        verify(jdbcTemplate).update(contains("INSERT INTO question_knowledge_relation"), any(), any(), eq("kp-001"));
        verify(jdbcTemplate).update(contains("INSERT INTO question_job_relation"), any(), any(), eq("job-java-backend"), eq("tech-001"));
    }

    private Map<String, Object> material(Path path, String fileType) {
        return Map.of(
                "material_id", "material-1",
                "course_id", "course-java-001",
                "chapter_id", "chapter-java-001",
                "file_name", "note." + fileType,
                "file_type", fileType,
                "storage_path", path.toString(),
                "parse_status", "待解析"
        );
    }

    private void writeMinimalDocx(Path path, String text) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:p><w:r><w:t>%s</w:t></w:r></w:p></w:body>
                    </w:document>
                    """.formatted(text)).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static User teacher() {
        User user = new User();
        user.setAccount("teacher001");
        user.setRole("TEACHER");
        return user;
    }
}
