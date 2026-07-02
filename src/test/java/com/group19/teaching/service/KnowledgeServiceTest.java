package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class KnowledgeServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final KnowledgeService knowledgeService = new KnowledgeService(jdbcTemplate, "target/test-uploads");

    @Test
    void answerReturnsKnowledgeUnavailableWhenNoApprovedChunk() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("course-java-001"), eq("student001"))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("course-java-001"), eq("chapter-java-001"))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeService.answer("course-java-001", "chapter-java-001", "Java 是什么？", user("student001", "STUDENT")));

        assertEquals(ErrorCode.KNOWLEDGE_UNAVAILABLE, exception.errorCode());
        verify(jdbcTemplate).update(contains("INSERT INTO ai_call_log"),
                org.mockito.ArgumentMatchers.any(), eq("QA"), eq("mock-local"), eq("stage12"),
                eq("Java 是什么？"), eq("NO_PUBLISHED_CHUNK"), eq("FAILED"));
    }

    @Test
    void listMaterialsForStudentOnlyReturnsPublishedScopedRows() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM course_material"), eq(Integer.class), eq("student001")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(contains("sp.student_id = ?"), eq("student001"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of("material_id", "material-1", "parse_status", "已发布")));

        Map<String, Object> result = knowledgeService.listMaterials(null, null, null, 1, 10,
                user("student001", "STUDENT"));

        assertEquals(1, result.get("total"));
        verify(jdbcTemplate).queryForList(contains("cm.parse_status = '已发布'"), eq("student001"), eq(10), eq(0));
    }

    @Test
    void updateMaterialChangesStatusAndWritesAuditLog() {
        User actor = user(2L, "teacher001", "TEACHER");
        when(jdbcTemplate.queryForList(contains("FROM course_material"), eq("material-1"))).thenReturn(List.of(Map.of(
                "material_id", "material-1",
                "course_id", "course-java-001",
                "chapter_id", "chapter-java-001",
                "parse_status", "待审核"
        )));
        when(jdbcTemplate.queryForObject(contains("FROM course WHERE"), eq(Integer.class), eq("course-java-001"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("teacher_id = ?"), eq(Integer.class), eq("course-java-001"), eq("teacher001"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM chapter"), eq(Integer.class), eq("course-java-001"), eq("chapter-java-001"))).thenReturn(1);

        Map<String, Object> result = knowledgeService.updateMaterial("material-1", Map.of("parse_status", "已发布"), actor);

        assertEquals("已发布", result.get("parse_status"));
        verify(jdbcTemplate).update(contains("UPDATE course_material"), eq("已发布"), eq("material-1"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                org.mockito.ArgumentMatchers.any(), eq("2"), eq("TEACHER"), eq("MATERIAL"), eq("UPDATE_MATERIAL"));
    }

    @Test
    void createKnowledgePointInsertsPointAndWritesAuditLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(contains("FROM course WHERE"), eq(Integer.class), eq("course-java-001"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM chapter"), eq(Integer.class), eq("course-java-001"), eq("chapter-java-001"))).thenReturn(1);

        Map<String, Object> result = knowledgeService.createKnowledgePoint(Map.of(
                "course_id", "course-java-001",
                "chapter_id", "chapter-java-001",
                "knowledge_name", "Spring Boot",
                "description", "Web 开发基础",
                "status", "待审核"
        ), actor);

        assertEquals("待审核", result.get("status"));
        verify(jdbcTemplate).update(contains("INSERT INTO knowledge_point"),
                org.mockito.ArgumentMatchers.any(), eq("course-java-001"), eq("chapter-java-001"),
                eq("Spring Boot"), eq("Web 开发基础"), eq(""), eq("manual"), eq("待审核"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                org.mockito.ArgumentMatchers.any(), eq("9"), eq("EDU_ADMIN"), eq("KNOWLEDGE"), eq("CREATE_KNOWLEDGE_POINT"));
    }

    @Test
    void auditChunkPublishesChunkAndMaterial() {
        when(jdbcTemplate.queryForList(anyString(), eq("chunk-1"))).thenReturn(List.of(Map.of(
                "chunk_id", "chunk-1",
                "course_id", "course-java-001",
                "material_id", "material-1"
        )));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("course-java-001"), eq("teacher001"))).thenReturn(1);

        Map<String, Object> result = knowledgeService.auditChunk("chunk-1", "已发布", user("teacher001", "TEACHER"));

        assertEquals("已发布", result.get("status"));
        verify(jdbcTemplate).update("UPDATE knowledge_chunk SET status = ? WHERE chunk_id = ?", "已发布", "chunk-1");
        verify(jdbcTemplate).update("UPDATE course_material SET parse_status = '已发布' WHERE material_id = ?", "material-1");
    }

    @Test
    void auditChunkRejectsMissingStatus() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeService.auditChunk("chunk-1", null, user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void uploadFileReturnsMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "pdf".getBytes());

        Map<String, Object> result = knowledgeService.uploadFile(file, user("student001", "STUDENT"));

        assertEquals("report.pdf", result.get("file_name"));
        assertEquals("pdf", result.get("file_type"));
    }

    @Test
    void uploadFileRejectsDangerousName() {
        MockMultipartFile file = new MockMultipartFile("file", "../report.pdf", "application/pdf", "pdf".getBytes());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeService.uploadFile(file, user("student001", "STUDENT")));

        assertEquals(ErrorCode.FILE_INVALID, exception.errorCode());
    }

    private static User user(String account, String role) {
        return user(null, account, role);
    }

    private static User user(Long id, String account, String role) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
