package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class DiscussionServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final DiscussionService discussionService = new DiscussionService(jdbcTemplate, new ObjectMapper());

    @Test
    void listReturnsPostsWithParsedTags() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM discussion_post"), eq(Integer.class),
                eq("已发布"), eq("课程学习"), eq("%Java%"), eq("%Java%"))).thenReturn(1);
        when(jdbcTemplate.queryForList(contains("FROM discussion_post p"),
                eq("student001"), eq("已发布"), eq("课程学习"), eq("%Java%"), eq("%Java%"), eq(10), eq(0)))
                .thenReturn(List.of(postRow("post-1", true)));

        Map<String, Object> result = discussionService.list("课程学习", "Java", 1, 10, user());

        assertEquals(1, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertEquals(List.of("Java"), records.get(0).get("tags"));
        assertEquals(true, records.get(0).get("liked"));
    }

    @Test
    void createInsertsPublishedPost() {
        User actor = user();

        Map<String, Object> result = discussionService.create(Map.of(
                "title", "Java 学习",
                "content", "讨论 Spring Boot",
                "category", "课程学习",
                "tags", List.of("Java", "Spring")
        ), actor);

        assertEquals("Java 学习", result.get("title"));
        assertEquals(List.of("Java", "Spring"), result.get("tags"));
        verify(jdbcTemplate).update(contains("INSERT INTO discussion_post"),
                any(), eq("Java 学习"), eq("讨论 Spring Boot"), eq("课程学习"), eq("[\"Java\",\"Spring\"]"),
                eq("student001"), eq("学生一"), eq(""), eq("已发布"));
    }

    @Test
    void createRejectsBlankTitle() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> discussionService.create(Map.of("title", "", "content", "正文"), user()));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void detailReturnsPostAndReplies() {
        when(jdbcTemplate.queryForList(contains("WHERE p.post_id = ?"),
                eq("student001"), eq("post-1"), eq("已发布"))).thenReturn(List.of(postRow("post-1", false)));
        when(jdbcTemplate.queryForList(contains("FROM discussion_reply"),
                eq("post-1"), eq("已发布"))).thenReturn(List.of(Map.of("reply_id", "reply-1")));

        Map<String, Object> result = discussionService.detail("post-1", user());

        @SuppressWarnings("unchecked")
        Map<String, Object> post = (Map<String, Object>) result.get("post");
        assertEquals("post-1", post.get("post_id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> replies = (List<Map<String, Object>>) result.get("replies");
        assertEquals("reply-1", replies.get(0).get("reply_id"));
    }

    @Test
    void detailRejectsMissingPost() {
        when(jdbcTemplate.queryForList(anyString(), eq("student001"), eq("missing"), eq("已发布")))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> discussionService.detail("missing", user()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void replyInsertsReplyAndUpdatesCount() {
        when(jdbcTemplate.queryForList(contains("WHERE p.post_id = ?"),
                eq("student001"), eq("post-1"), eq("已发布"))).thenReturn(List.of(postRow("post-1", false)));

        Map<String, Object> result = discussionService.reply("post-1", Map.of("content", "收到"), user());

        assertEquals("post-1", result.get("post_id"));
        assertEquals("收到", result.get("content"));
        verify(jdbcTemplate).update(contains("INSERT INTO discussion_reply"),
                any(), eq("post-1"), eq("收到"), eq("student001"), eq("学生一"), eq(""), eq("已发布"));
        verify(jdbcTemplate).update(contains("SET reply_count = reply_count + 1"), eq("post-1"));
    }

    @Test
    void likeIgnoresDuplicateLike() {
        when(jdbcTemplate.queryForList(contains("WHERE p.post_id = ?"),
                eq("student001"), eq("post-1"), eq("已发布"))).thenReturn(List.of(postRow("post-1", true)));
        when(jdbcTemplate.update(contains("INSERT INTO discussion_like"), any(), eq("post-1"), eq("student001")))
                .thenThrow(new DuplicateKeyException("duplicate"));

        Map<String, Object> result = discussionService.like("post-1", user());

        assertEquals(true, result.get("liked"));
        verify(jdbcTemplate, never()).update(contains("SET like_count = like_count + 1"), eq("post-1"));
    }

    @Test
    void unlikeOnlyDecrementsWhenRelationExists() {
        when(jdbcTemplate.queryForList(contains("WHERE p.post_id = ?"),
                eq("student001"), eq("post-1"), eq("已发布"))).thenReturn(List.of(postRow("post-1", true)));
        when(jdbcTemplate.update(contains("DELETE FROM discussion_like"), eq("post-1"), eq("student001")))
                .thenReturn(1);

        Map<String, Object> result = discussionService.unlike("post-1", user());

        assertEquals(false, result.get("liked"));
        verify(jdbcTemplate).update(contains("GREATEST(like_count - 1, 0)"), eq("post-1"));
    }

    @Test
    void listRejectsInvalidPage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> discussionService.list(null, null, 0, 10, user()));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    private static Map<String, Object> postRow(String postId, boolean liked) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("post_id", postId);
        row.put("title", "Java 学习");
        row.put("content", "讨论内容");
        row.put("category", "课程学习");
        row.put("tags", "[\"Java\"]");
        row.put("author_id", "student001");
        row.put("author_name", "学生一");
        row.put("avatar_url", "");
        row.put("like_count", liked ? 1 : 0);
        row.put("reply_count", 0);
        row.put("liked", liked);
        row.put("created_at", LocalDateTime.now());
        return row;
    }

    private static User user() {
        User user = new User();
        user.setAccount("student001");
        user.setName("学生一");
        user.setRole("STUDENT");
        return user;
    }
}
