package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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

class KnowledgeServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final KnowledgeService knowledgeService = new KnowledgeService(jdbcTemplate, "target/test-uploads");

    @Test
    void answerReturnsKnowledgeUnavailableWhenNoApprovedChunk() {
        when(jdbcTemplate.queryForList(anyString(), eq("course-java-001"), eq("chapter-java-001"))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeService.answer("course-java-001", "chapter-java-001", "Java 是什么？", user("student001", "STUDENT")));

        assertEquals(ErrorCode.KNOWLEDGE_UNAVAILABLE, exception.errorCode());
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

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
