package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MajorService {

    private static final String ENABLED = "启用";
    private static final List<String> STATUSES = List.of(ENABLED, "停用");

    private final JdbcTemplate jdbcTemplate;

    public MajorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(String keyword, String status, Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        String normalizedStatus = normalizeStatus(status, false);
        if (!"EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            normalizedStatus = ENABLED;
        }

        List<Object> params = new ArrayList<>();
        String where = buildWhere(keyword, normalizedStatus, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM major " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT major_id, major_name, major_code, major_category, training_program_id, description, status
                FROM major
                """ + where + """
                ORDER BY major_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());

        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request, User actor) {
        String majorName = stringValue(request.get("major_name"));
        String majorCode = stringValue(request.get("major_code"));
        String status = normalizeStatus(stringValue(request.get("status")), true);
        if (!StringUtils.hasText(majorName) || !StringUtils.hasText(majorCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        String majorId = "major-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO major
                  (major_id, major_name, major_code, major_category, training_program_id, description, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                majorId,
                majorName,
                majorCode,
                stringValue(request.get("major_category")),
                stringValue(request.get("training_program_id")),
                stringValue(request.get("description")),
                status);
        writeOperationLog(actor, "CREATE_MAJOR");

        return Map.of(
                "major_id", majorId,
                "major_name", majorName,
                "status", status
        );
    }

    @Transactional
    public Map<String, Object> update(String majorId, Map<String, Object> request, User actor) {
        if (!StringUtils.hasText(majorId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String status = normalizeStatus(stringValue(request.get("status")), true);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM major WHERE major_id = ?",
                Integer.class, majorId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE major SET status = ?");
        params.add(status);
        appendUpdate(sql, params, "major_name", request.get("major_name"), false);
        appendUpdate(sql, params, "major_code", request.get("major_code"), false);
        appendUpdate(sql, params, "major_category", request.get("major_category"), true);
        appendUpdate(sql, params, "training_program_id", request.get("training_program_id"), true);
        appendUpdate(sql, params, "description", request.get("description"), true);
        sql.append(" WHERE major_id = ?");
        params.add(majorId);
        jdbcTemplate.update(sql.toString(), params.toArray());
        writeOperationLog(actor, "UPDATE_MAJOR");

        return Map.of(
                "major_id", majorId,
                "status", status
        );
    }

    private String buildWhere(String keyword, String status, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(keyword)) {
            where.append("AND major_name LIKE ?\n");
            params.add("%" + keyword.trim() + "%");
        }
        if (StringUtils.hasText(status)) {
            where.append("AND status = ?\n");
            params.add(status);
        }
        return where.toString();
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private String normalizeStatus(String status, boolean required) {
        if (!StringUtils.hasText(status)) {
            if (required) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            return "";
        }
        String value = status.trim();
        if (!STATUSES.contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private void appendUpdate(StringBuilder sql, List<Object> params, String column, Object value, boolean allowBlank) {
        if (value == null) {
            return;
        }
        String text = stringValue(value);
        if (!allowBlank && !StringUtils.hasText(text)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        sql.append(", ").append(column).append(" = ?");
        params.add(text);
    }

    private void writeOperationLog(User actor, String operationType) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, user_id, role, module, operation_type, operation_result, operation_time)
                VALUES (?, ?, ?, 'MAJOR', ?, 'SUCCESS', CURRENT_TIMESTAMP)
                """, "op-" + UUID.randomUUID(), String.valueOf(actor.getId()), actor.getRole(), operationType);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
