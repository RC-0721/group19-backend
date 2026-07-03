package com.group19.teaching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DiscussionService {

    private static final String PUBLISHED = "已发布";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DiscussionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> list(String category, String keyword, Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        List<Object> params = new ArrayList<>();
        String where = buildPostWhere(category, keyword, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM discussion_post p " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>();
        pageParams.add(actor.getAccount());
        pageParams.addAll(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT p.post_id, p.title, p.content, p.category, p.tags, p.author_id,
                       p.author_name, p.avatar_url, p.like_count, p.reply_count,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM discussion_like dl
                           WHERE dl.post_id = p.post_id AND dl.user_id = ?
                       ) THEN TRUE ELSE FALSE END AS liked,
                       p.created_at
                FROM discussion_post p
                """ + where + """
                ORDER BY p.created_at DESC
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        records.forEach(this::normalizePost);

        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request, User actor) {
        String title = stringValue(request.get("title"));
        String content = stringValue(request.get("content"));
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String postId = "post-" + UUID.randomUUID();
        String category = stringValue(request.get("category"));
        String tagsJson = tagsJson(request.get("tags"));
        String authorName = StringUtils.hasText(actor.getName()) ? actor.getName() : actor.getAccount();

        jdbcTemplate.update("""
                INSERT INTO discussion_post
                  (post_id, title, content, category, tags, author_id, author_name, avatar_url,
                   like_count, reply_count, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, NOW(), NOW())
                """, postId, title, content, category, tagsJson, actor.getAccount(), authorName, "", PUBLISHED);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("post_id", postId);
        result.put("title", title);
        result.put("category", category);
        result.put("tags", parseTags(tagsJson));
        result.put("author_id", actor.getAccount());
        result.put("author_name", authorName);
        result.put("like_count", 0);
        result.put("reply_count", 0);
        return result;
    }

    public Map<String, Object> detail(String postId, User actor) {
        Map<String, Object> post = requirePost(postId, actor);
        List<Map<String, Object>> replies = jdbcTemplate.queryForList("""
                SELECT reply_id, post_id, content, author_id, author_name, avatar_url, created_at
                FROM discussion_reply
                WHERE post_id = ? AND status = ?
                ORDER BY created_at ASC
                """, postId, PUBLISHED);
        return Map.of("post", post, "replies", replies);
    }

    @Transactional
    public Map<String, Object> reply(String postId, Map<String, Object> request, User actor) {
        requirePost(postId, actor);
        String content = stringValue(request.get("content"));
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String replyId = "reply-" + UUID.randomUUID();
        String authorName = StringUtils.hasText(actor.getName()) ? actor.getName() : actor.getAccount();
        jdbcTemplate.update("""
                INSERT INTO discussion_reply
                  (reply_id, post_id, content, author_id, author_name, avatar_url, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                """, replyId, postId, content, actor.getAccount(), authorName, "", PUBLISHED);
        jdbcTemplate.update("""
                UPDATE discussion_post
                SET reply_count = reply_count + 1, updated_at = NOW()
                WHERE post_id = ?
                """, postId);
        return Map.of(
                "reply_id", replyId,
                "post_id", postId,
                "content", content,
                "author_id", actor.getAccount(),
                "author_name", authorName
        );
    }

    @Transactional
    public Map<String, Object> like(String postId, User actor) {
        requirePost(postId, actor);
        boolean changed = false;
        try {
            int rows = jdbcTemplate.update("""
                    INSERT INTO discussion_like (like_id, post_id, user_id, created_at)
                    VALUES (?, ?, ?, NOW())
                    """, "like-" + UUID.randomUUID(), postId, actor.getAccount());
            changed = rows > 0;
        } catch (DuplicateKeyException ignored) {
            changed = false;
        }
        if (changed) {
            jdbcTemplate.update("""
                    UPDATE discussion_post
                    SET like_count = like_count + 1, updated_at = NOW()
                    WHERE post_id = ?
                    """, postId);
        }
        return Map.of("post_id", postId, "liked", true);
    }

    @Transactional
    public Map<String, Object> unlike(String postId, User actor) {
        requirePost(postId, actor);
        int rows = jdbcTemplate.update("""
                DELETE FROM discussion_like
                WHERE post_id = ? AND user_id = ?
                """, postId, actor.getAccount());
        if (rows > 0) {
            jdbcTemplate.update("""
                    UPDATE discussion_post
                    SET like_count = GREATEST(like_count - 1, 0), updated_at = NOW()
                    WHERE post_id = ?
                    """, postId);
        }
        return Map.of("post_id", postId, "liked", false);
    }

    private Map<String, Object> requirePost(String postId, User actor) {
        if (!StringUtils.hasText(postId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.post_id, p.title, p.content, p.category, p.tags, p.author_id,
                       p.author_name, p.avatar_url, p.like_count, p.reply_count,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM discussion_like dl
                           WHERE dl.post_id = p.post_id AND dl.user_id = ?
                       ) THEN TRUE ELSE FALSE END AS liked,
                       p.created_at
                FROM discussion_post p
                WHERE p.post_id = ? AND p.status = ?
                LIMIT 1
                """, actor.getAccount(), postId, PUBLISHED);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> post = rows.get(0);
        normalizePost(post);
        return post;
    }

    private String buildPostWhere(String category, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE p.status = ?\n");
        params.add(PUBLISHED);
        if (StringUtils.hasText(category)) {
            where.append("AND p.category = ?\n");
            params.add(category.trim());
        }
        if (StringUtils.hasText(keyword)) {
            where.append("AND (p.title LIKE ? OR p.content LIKE ?)\n");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        return where.toString();
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private void normalizePost(Map<String, Object> row) {
        row.put("tags", parseTags(row.get("tags")));
        row.put("liked", booleanValue(row.get("liked")));
    }

    private String tagsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(normalizeTags(value));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private List<String> normalizeTags(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> tags = new ArrayList<>();
            for (Object item : iterable) {
                String tag = stringValue(item);
                if (StringUtils.hasText(tag)) {
                    tags.add(tag);
                }
            }
            return tags;
        }
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                return objectMapper.readValue(text, STRING_LIST);
            } catch (JsonProcessingException exception) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
        }
        return List.of(text);
    }

    private List<String> parseTags(Object value) {
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(text, STRING_LIST);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(stringValue(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
