package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AIConfigService {

    private static final String CONFIG_ID = "default";

    private final JdbcTemplate jdbcTemplate;

    public AIConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> get() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT config_id, model_name, prompt_version, timeout_ms, fallback_enabled, status
                FROM ai_config
                WHERE config_id = ?
                LIMIT 1
                """, CONFIG_ID);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> update(Map<String, Object> request, User actor) {
        String modelName = stringValue(request.get("model_name"));
        String promptVersion = stringValue(request.get("prompt_version"));
        Integer timeoutMs = intValue(request.get("timeout_ms"));
        Boolean fallbackEnabled = booleanValue(request.get("fallback_enabled"));
        String status = stringValue(request.get("status"));
        if (!StringUtils.hasText(modelName) || !StringUtils.hasText(promptVersion)
                || timeoutMs == null || timeoutMs < 1000 || timeoutMs > 120000
                || fallbackEnabled == null || !List.of("启用", "停用").contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        int updated = jdbcTemplate.update("""
                UPDATE ai_config
                SET model_name = ?, prompt_version = ?, timeout_ms = ?, fallback_enabled = ?, status = ?
                WHERE config_id = ?
                """, modelName, promptVersion, timeoutMs, fallbackEnabled, status, CONFIG_ID);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, user_id, role, module, operation_type, operation_result, operation_time)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """, "log-" + UUID.randomUUID(), actor.getAccount(), actor.getRole(),
                "AI_CONFIG", "UPDATE_AI_CONFIG", "SUCCESS");
        return Map.of("config_id", CONFIG_ID, "status", status);
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            if ("true".equalsIgnoreCase(text.trim()) || "false".equalsIgnoreCase(text.trim())) {
                return Boolean.parseBoolean(text.trim());
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
