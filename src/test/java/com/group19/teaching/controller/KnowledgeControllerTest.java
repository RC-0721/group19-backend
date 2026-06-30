package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.KnowledgeService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KnowledgeController.class)
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeService knowledgeService;

    @MockBean
    private AuthService authService;

    @Test
    void uploadMaterialReturnsParseTask() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        MockMultipartFile file = new MockMultipartFile("file", "note.md", "text/markdown", "Java basics".getBytes());
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(knowledgeService.uploadMaterial(eq("course-java-001"), eq("chapter-java-001"), any(), eq(teacher))).thenReturn(Map.of(
                "material_id", "material-1",
                "parse_status", "待审核",
                "parse_task_id", "parse-1"
        ));

        mockMvc.perform(multipart("/api/materials")
                        .file(file)
                        .header("token", "teacher-token")
                        .param("course_id", "course-java-001")
                        .param("chapter_id", "chapter-java-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.material_id").value("material-1"))
                .andExpect(jsonPath("$.data.parse_task_id").value("parse-1"));
    }

    @Test
    void uploadFileReturnsMetadata() throws Exception {
        User student = user("student001", "STUDENT");
        MockMultipartFile file = new MockMultipartFile("file", "homework.zip", "application/zip", "zip".getBytes());
        when(authService.requireRole("student-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(student);
        when(knowledgeService.uploadFile(any(), eq(student))).thenReturn(Map.of(
                "file_name", "homework.zip",
                "file_type", "zip",
                "storage_path", "target/test-uploads/file-1.zip"
        ));

        mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.file_name").value("homework.zip"))
                .andExpect(jsonPath("$.data.file_type").value("zip"));
    }

    @Test
    void uploadFileRequiresLogin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "homework.zip", "application/zip", "zip".getBytes());
        doThrow(new BusinessException(ErrorCode.AUTH_FAILED))
                .when(authService).requireRole(null, "STUDENT", "TEACHER", "EDU_ADMIN");

        mockMvc.perform(multipart("/api/files").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40101"));
    }

    @Test
    void chunksReturnsPagedData() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(knowledgeService.listChunks("material-1", null, "待审核", 1, 10, teacher)).thenReturn(Map.of(
                "records", List.of(Map.of("chunk_id", "chunk-1", "status", "待审核")),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/knowledge/chunks")
                        .header("token", "teacher-token")
                        .param("material_id", "material-1")
                        .param("status", "待审核")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].chunk_id").value("chunk-1"));
    }

    @Test
    void qaReturnsApprovedReference() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(knowledgeService.answer("course-java-001", "chapter-java-001", "Java 是什么？", student)).thenReturn(Map.of(
                "message_content", "根据已审核课程资料：Java 基础",
                "reference_chunk", "chunk-1",
                "status", "已完成"
        ));

        mockMvc.perform(post("/api/qa")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":\"course-java-001\",\"chapter_id\":\"chapter-java-001\",\"question_text\":\"Java 是什么？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reference_chunk").value("chunk-1"));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
