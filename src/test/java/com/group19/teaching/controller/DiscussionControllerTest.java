package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.DiscussionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiscussionController.class)
class DiscussionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DiscussionService discussionService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsDiscussionPage() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(student);
        when(discussionService.list("课程学习", "Java", 1, 10, student)).thenReturn(Map.of(
                "records", List.of(Map.of(
                        "post_id", "post-1",
                        "title", "Java 学习",
                        "liked", true
                )),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/discussions")
                        .header("token", "student-token")
                        .param("category", "课程学习")
                        .param("keyword", "Java")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].post_id").value("post-1"))
                .andExpect(jsonPath("$.data.records[0].liked").value(true));
    }

    @Test
    void createReturnsNewPost() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "STUDENT", "TEACHER")).thenReturn(teacher);
        when(discussionService.create(anyMap(), eq(teacher))).thenReturn(Map.of(
                "post_id", "post-1",
                "title", "课程讨论",
                "tags", List.of("Java")
        ));

        mockMvc.perform(post("/api/discussions")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"课程讨论\",\"content\":\"讨论内容\",\"category\":\"课程学习\",\"tags\":[\"Java\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.post_id").value("post-1"))
                .andExpect(jsonPath("$.data.tags[0]").value("Java"));
    }

    @Test
    void detailReturnsPostAndReplies() throws Exception {
        User admin = user("admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(admin);
        when(discussionService.detail("post-1", admin)).thenReturn(Map.of(
                "post", Map.of("post_id", "post-1", "title", "讨论帖"),
                "replies", List.of(Map.of("reply_id", "reply-1", "content", "收到"))
        ));

        mockMvc.perform(get("/api/discussions/post-1")
                        .header("token", "admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.post.post_id").value("post-1"))
                .andExpect(jsonPath("$.data.replies[0].reply_id").value("reply-1"));
    }

    @Test
    void replyReturnsCreatedReply() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT", "TEACHER")).thenReturn(student);
        when(discussionService.reply(eq("post-1"), anyMap(), eq(student))).thenReturn(Map.of(
                "reply_id", "reply-1",
                "post_id", "post-1",
                "content", "收到"
        ));

        mockMvc.perform(post("/api/discussions/post-1/replies")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"收到\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.reply_id").value("reply-1"));
    }

    @Test
    void likeAndUnlikeReturnLikedState() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT", "TEACHER")).thenReturn(student);
        when(discussionService.like("post-1", student)).thenReturn(Map.of("post_id", "post-1", "liked", true));
        when(discussionService.unlike("post-1", student)).thenReturn(Map.of("post_id", "post-1", "liked", false));

        mockMvc.perform(post("/api/discussions/post-1/likes")
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true));
        mockMvc.perform(delete("/api/discussions/post-1/likes")
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false));
    }

    @Test
    void createRejectsEduAdminRole() throws Exception {
        when(authService.requireRole("admin-token", "STUDENT", "TEACHER"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/discussions")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"课程讨论\",\"content\":\"讨论内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setName(account);
        user.setRole(role);
        return user;
    }
}
