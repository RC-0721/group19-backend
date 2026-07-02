package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final Path uploadDir;

    public PlatformService(
            JdbcTemplate jdbcTemplate,
            @Value("${teaching.upload-dir:data/uploads}") String uploadDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadDir = Path.of(uploadDir);
    }

    public Map<String, Object> summary(String startTime, String endTime) {
        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_count", count("sys_user"));
        result.put("course_count", count("course"));
        result.put("material_count", count("course_material"));
        result.put("question_count", count("question"));
        result.put("homework_count", count("homework"));
        result.put("project_count", count("project_task"));
        result.put("ai_call_count", count("ai_call_log"));
        result.put("profile_count", count("ability_profile"));
        result.put("operation_log_count", operationLogCount(start, end));
        return result;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", databaseStatus());
        result.put("upload_dir", uploadStatus());
        result.put("ai_config", aiStatus());
        result.put("status", result.values().stream()
                .allMatch(value -> "UP".equals(String.valueOf(((Map<?, ?>) value).get("status")))) ? "UP" : "DOWN");
        return result;
    }

    public Map<String, Object> listAiCallLogs(
            String userId,
            String scene,
            String modelName,
            String status,
            String startTime,
            String endTime,
            Integer pageNo,
            Integer pageSize) {
        validatePage(pageNo, pageSize);
        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Object> params = new ArrayList<>();
        String where = aiWhere(userId, scene, modelName, status, start, end, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_call_log " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT log_id, user_id, scene, model, prompt_version, input_summary,
                       output_summary, call_status, call_time
                FROM ai_call_log
                """ + where + """
                ORDER BY call_time DESC, log_id DESC
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of("records", records, "total", total == null ? 0 : total,
                "page_no", pageNo, "page_size", pageSize);
    }

    private Map<String, Object> databaseStatus() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Map.of("status", "UP");
        } catch (RuntimeException exception) {
            return Map.of("status", "DOWN");
        }
    }

    private Map<String, Object> uploadStatus() {
        try {
            Files.createDirectories(uploadDir);
            return Map.of("status", Files.isWritable(uploadDir) ? "UP" : "DOWN");
        } catch (RuntimeException | java.io.IOException exception) {
            return Map.of("status", "DOWN");
        }
    }

    private Map<String, Object> aiStatus() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT model_name, prompt_version, fallback_enabled, status
                    FROM ai_config
                    WHERE config_id = 'default'
                    LIMIT 1
                    """);
            if (rows.isEmpty()) {
                return Map.of("status", "DOWN");
            }
            Map<String, Object> row = rows.get(0);
            return Map.of(
                    "status", "启用".equals(String.valueOf(row.get("status"))) ? "UP" : "DOWN",
                    "model_name", row.get("model_name"),
                    "prompt_version", row.get("prompt_version"),
                    "fallback_enabled", row.get("fallback_enabled")
            );
        } catch (RuntimeException exception) {
            return Map.of("status", "DOWN");
        }
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private int operationLogCount(LocalDateTime start, LocalDateTime end) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (start != null) {
            where.append("AND operation_time >= ?\n");
            params.add(start);
        }
        if (end != null) {
            where.append("AND operation_time <= ?\n");
            params.add(end);
        }
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_log " + where,
                Integer.class, params.toArray());
        return value == null ? 0 : value;
    }

    private String aiWhere(
            String userId,
            String scene,
            String modelName,
            String status,
            LocalDateTime start,
            LocalDateTime end,
            List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        append(where, params, "user_id", userId);
        append(where, params, "scene", scene);
        append(where, params, "model", modelName);
        append(where, params, "call_status", status);
        if (start != null) {
            where.append("AND call_time >= ?\n");
            params.add(start);
        }
        if (end != null) {
            where.append("AND call_time <= ?\n");
            params.add(end);
        }
        return where.toString();
    }

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
        }
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }
}
