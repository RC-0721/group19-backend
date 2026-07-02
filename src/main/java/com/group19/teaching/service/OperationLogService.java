package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationLogService {

    private final JdbcTemplate jdbcTemplate;

    public OperationLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(
            String userId,
            String module,
            String operationType,
            String startTime,
            String endTime,
            Integer pageNo,
            Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        List<Object> params = new ArrayList<>();
        String where = buildWhere(userId, module, operationType, start, end, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_log " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT log_id, user_id, role, module, operation_type, operation_result, operation_time
                FROM operation_log
                """ + where + """
                ORDER BY operation_time DESC, log_id DESC
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
    }

    public String exportCsv(
            String userId,
            String module,
            String operationType,
            String startTime,
            String endTime) {
        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        List<Object> params = new ArrayList<>();
        String where = buildWhere(userId, module, operationType, start, end, params);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT log_id, user_id, role, module, operation_type, operation_result, operation_time
                FROM operation_log
                """ + where + """
                ORDER BY operation_time DESC, log_id DESC
                """, params.toArray());
        List<String> columns = List.of("log_id", "user_id", "role", "module", "operation_type",
                "operation_result", "operation_time");
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", columns)).append("\n");
        for (Map<String, Object> record : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            columns.forEach(column -> row.put(column, record.get(column)));
            csv.append(csvLine(row.values().stream().map(this::csvValue).toList())).append("\n");
        }
        return csv.toString();
    }

    private String buildWhere(
            String userId,
            String module,
            String operationType,
            LocalDateTime start,
            LocalDateTime end,
            List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        appendFilter(where, params, "user_id", userId);
        appendFilter(where, params, "module", module);
        appendFilter(where, params, "operation_type", operationType);
        if (start != null) {
            where.append("AND operation_time >= ?\n");
            params.add(start);
        }
        if (end != null) {
            where.append("AND operation_time <= ?\n");
            params.add(end);
        }
        return where.toString();
    }

    private void appendFilter(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
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

    private String csvLine(List<String> values) {
        return String.join(",", values);
    }

    private String csvValue(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
