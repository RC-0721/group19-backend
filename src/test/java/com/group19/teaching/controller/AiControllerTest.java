package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AiContentService;
import com.group19.teaching.service.AiService;
import com.group19.teaching.service.AiTaskService;
import com.group19.teaching.service.AuthService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(AiController.class)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiService aiService;

    @MockBean
    private AiTaskService aiTaskService;

    @MockBean
    private AiContentService aiContentService;

    @MockBean
    private AuthService authService;

    @Test
    void chatReturnsAiContent() throws Exception {
        User teacher = user();
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(aiService.chat(anyMap(), eq(teacher))).thenReturn(Map.of(
                "request_id", "req-1",
                "model", "mock-ai",
                "content", "ok",
                "duration_ms", 1L
        ));

        mockMvc.perform(post("/api/ai/chat")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"CHAT\",\"prompt\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("ok"));
    }

    @Test
    void taskRoutesToService() throws Exception {
        User teacher = user();
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(aiTaskService.submit(anyMap(), eq(teacher))).thenReturn(Map.of(
                "task_id", "task-1",
                "task_status", "已完成"
        ));
        when(aiTaskService.get("task-1", teacher)).thenReturn(Map.of(
                "task_id", "task-1",
                "task_status", "已完成"
        ));

        mockMvc.perform(post("/api/ai/tasks")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task_type\":\"MATERIAL_PARSE\",\"request\":{\"id\":\"m1\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task_status").value("已完成"));

        mockMvc.perform(get("/api/ai/tasks/task-1").header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task_id").value("task-1"));
    }

    @Test
    void streamChatStartsSseResponse() throws Exception {
        User teacher = user();
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(aiService.stream(anyMap(), eq(teacher))).thenReturn(new SseEmitter());

        mockMvc.perform(post("/api/ai/chat/stream")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"CHAT\",\"prompt\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void stage16RoutesToAiContentService() throws Exception {
        User teacher = user();
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(aiContentService.parseMaterial(anyMap(), eq(teacher))).thenReturn(Map.of(
                "parse_task_id", "parse-1",
                "task_status", "待确认"
        ));
        when(aiContentService.getParseTask("parse-1", teacher)).thenReturn(Map.of(
                "parse_task_id", "parse-1",
                "task_status", "待确认"
        ));
        when(aiContentService.confirmParseTask("parse-1", teacher)).thenReturn(Map.of(
                "parse_task_id", "parse-1",
                "task_status", "已确认"
        ));
        when(aiContentService.buildKnowledgeGraph(anyMap(), eq(teacher))).thenReturn(Map.of(
                "task_id", "task-graph-1",
                "created_candidates", 2
        ));
        when(aiContentService.approveKnowledgeCandidate("kcand-1", teacher)).thenReturn(Map.of(
                "candidate_id", "kcand-1",
                "audit_status", "已通过"
        ));
        when(aiContentService.generateQuestions(anyMap(), eq(teacher))).thenReturn(Map.of(
                "task_id", "task-question-1",
                "created_candidates", 5
        ));
        when(aiContentService.approveQuestionCandidate("qcand-1", teacher)).thenReturn(Map.of(
                "candidate_id", "qcand-1",
                "question_id", "q-1"
        ));
        when(aiContentService.rejectQuestionCandidate("qcand-2", teacher)).thenReturn(Map.of(
                "candidate_id", "qcand-2",
                "audit_status", "已驳回"
        ));

        mockMvc.perform(post("/api/ai/parse-material")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"material_id\":\"material-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task_status").value("待确认"));

        mockMvc.perform(get("/api/ai/parse-material/parse-1").header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parse_task_id").value("parse-1"));

        mockMvc.perform(put("/api/ai/parse-material/parse-1/confirm").header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task_status").value("已确认"));

        mockMvc.perform(post("/api/ai/build-knowledge-graph")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":\"course-java-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created_candidates").value(2));

        mockMvc.perform(put("/api/ai/knowledge-graph/candidates/kcand-1/approve")
                        .header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audit_status").value("已通过"));

        mockMvc.perform(post("/api/ai/generate-questions")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_id\":\"kp-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created_candidates").value(5));

        mockMvc.perform(post("/api/ai/question-candidates/qcand-1/approve")
                        .header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question_id").value("q-1"));

        mockMvc.perform(post("/api/ai/question-candidates/qcand-2/reject")
                        .header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audit_status").value("已驳回"));
    }

    private static User user() {
        User user = new User();
        user.setAccount("teacher001");
        user.setRole("TEACHER");
        return user;
    }
}
