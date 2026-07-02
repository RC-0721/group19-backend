package com.group19.teaching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiTaskService {

    private final JdbcTemplate jdbcTemplate;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public AiTaskService(JdbcTemplate jdbcTemplate, AiService aiService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> submit(Map<String, Object> request, User actor) {
        String taskType = stringValue(request.get("task_type"));
        Object taskRequest = request.get("request");
        if (!StringUtils.hasText(taskType) || taskRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String taskId = "ai-task-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO ai_task (task_id, task_type, created_by, request_json, task_status, created_time, started_time)
                VALUES (?, ?, ?, ?, '运行中', ?, ?)
                """, taskId, taskType, actor.getAccount(), json(taskRequest), Timestamp.valueOf(now), Timestamp.valueOf(now));
        String status = "已完成";
        try {
            Map<String, Object> aiResult = aiService.chat(Map.of(
                    "scene", taskType,
                    "prompt", json(taskRequest)
            ), actor);
            jdbcTemplate.update("""
                    UPDATE ai_task
                    SET task_status = '已完成', result_json = ?, finished_time = ?
                    WHERE task_id = ?
                    """, json(aiResult), Timestamp.valueOf(LocalDateTime.now()), taskId);
        } catch (RuntimeException exception) {
            status = "失败";
            jdbcTemplate.update("""
                    UPDATE ai_task
                    SET task_status = '失败', error_message = ?, finished_time = ?
                    WHERE task_id = ?
                    """, shortMessage(exception), Timestamp.valueOf(LocalDateTime.now()), taskId);
        }
        return Map.of("task_id", taskId, "task_status", status);
    }

    public Map<String, Object> get(String taskId, User actor) {
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT task_id, task_type, created_by, request_json, result_json, task_status,
                       error_message, created_time, started_time, finished_time
                FROM ai_task
                WHERE task_id = ?
                LIMIT 1
                """, taskId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        if (!actor.getAccount().equals(row.get("created_by")) && !"EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return row;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private String shortMessage(RuntimeException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
