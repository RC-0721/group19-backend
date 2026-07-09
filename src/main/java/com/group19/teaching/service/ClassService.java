package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClassService {

    private static final List<String> STATUSES = List.of("启用", "停用");

    private final JdbcTemplate jdbcTemplate;

    public ClassService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(String majorId, String grade, String status,
                                    Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        String normalizedStatus = normalizeStatus(status, false);

        List<Object> params = new ArrayList<>();
        String where = buildWhere(majorId, grade, normalizedStatus, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT c.class_id) FROM `class` c " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT DISTINCT c.class_id, c.major_id, m.major_name, c.class_name,
                       c.grade, c.counselor_id, c.class_code, c.status
                FROM `class` c
                LEFT JOIN major m ON c.major_id = m.major_id
                """ + where + """
                ORDER BY c.grade DESC, c.class_id
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
        String majorId = stringValue(request.get("major_id"));
        String className = stringValue(request.get("class_name"));
        String status = normalizeStatus(stringValue(request.get("status")), true);
        if (!StringUtils.hasText(majorId) || !StringUtils.hasText(className)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireMajor(majorId);

        String classId = "class-" + UUID.randomUUID();
        String classCode = normalizeClassCode(request.get("class_code"));
        if (!StringUtils.hasText(classCode)) {
            classCode = generateClassCode();
        }
        requireUniqueClassCode(classCode, null);
        jdbcTemplate.update("""
                INSERT INTO `class` (class_id, major_id, class_name, grade, counselor_id, class_code, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                classId,
                majorId,
                className,
                stringValue(request.get("grade")),
                stringValue(request.get("counselor_id")),
                classCode,
                status);
        writeOperationLog(actor, "CREATE_CLASS");

        return Map.of(
                "class_id", classId,
                "major_id", majorId,
                "class_name", className,
                "class_code", classCode,
                "status", status
        );
    }

    @Transactional
    public Map<String, Object> update(String classId, Map<String, Object> request, User actor) {
        if (!StringUtils.hasText(classId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String status = normalizeStatus(stringValue(request.get("status")), true);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `class` WHERE class_id = ?",
                Integer.class, classId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE `class` SET status = ?");
        params.add(status);
        appendMajorUpdate(sql, params, request.get("major_id"));
        appendUpdate(sql, params, "class_name", request.get("class_name"), false);
        appendUpdate(sql, params, "grade", request.get("grade"), true);
        appendUpdate(sql, params, "counselor_id", request.get("counselor_id"), true);
        appendClassCodeUpdate(sql, params, request.get("class_code"), classId);
        sql.append(" WHERE class_id = ?");
        params.add(classId);
        jdbcTemplate.update(sql.toString(), params.toArray());
        writeOperationLog(actor, "UPDATE_CLASS");

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("class_id", classId);
        response.put("status", status);
        if (request.containsKey("class_code")) {
            response.put("class_code", normalizeClassCode(request.get("class_code")));
        }
        return response;
    }

    @Transactional
    public Map<String, Object> updateJoinCode(String classId, Map<String, Object> request, User actor) {
        if (!StringUtils.hasText(classId) || request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String classCode = normalizeClassCode(request.get("class_code"));
        if (!validClassCode(classCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireClassVisible(classId, actor);
        requireUniqueClassCode(classCode, classId);

        jdbcTemplate.update("UPDATE `class` SET class_code = ? WHERE class_id = ?", classCode, classId);
        writeOperationLog(actor, "UPDATE_CLASS_JOIN_CODE");

        return Map.of(
                "class_id", classId,
                "class_code", classCode
        );
    }

    private String buildWhere(String majorId, String grade, String status, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(majorId)) {
            where.append("AND c.major_id = ?\n");
            params.add(majorId.trim());
        }
        if (StringUtils.hasText(grade)) {
            where.append("AND c.grade = ?\n");
            params.add(grade.trim());
        }
        if (StringUtils.hasText(status)) {
            where.append("AND c.status = ?\n");
            params.add(status);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM course_class cc
                        WHERE cc.class_id = c.class_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
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

    private void requireMajor(String majorId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM major WHERE major_id = ?",
                Integer.class, majorId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void appendMajorUpdate(StringBuilder sql, List<Object> params, Object value) {
        if (value == null) {
            return;
        }
        String majorId = stringValue(value);
        if (!StringUtils.hasText(majorId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireMajor(majorId);
        sql.append(", major_id = ?");
        params.add(majorId);
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

    private void appendClassCodeUpdate(StringBuilder sql, List<Object> params, Object value, String classId) {
        if (value == null) {
            return;
        }
        String classCode = normalizeClassCode(value);
        if (!validClassCode(classCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireUniqueClassCode(classCode, classId);
        sql.append(", class_code = ?");
        params.add(classCode);
    }

    private void requireClassVisible(String classId, User actor) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM `class` c WHERE c.class_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(classId.trim());
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            sql.append("""
                    AND EXISTS (
                        SELECT 1 FROM course_class cc
                        WHERE cc.class_id = c.class_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireUniqueClassCode(String classCode, String classId) {
        if (!validClassCode(classCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Integer count;
        if (StringUtils.hasText(classId)) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `class` WHERE class_code = ? AND class_id <> ?",
                    Integer.class, classCode, classId);
        } else {
            count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `class` WHERE class_code = ?",
                    Integer.class, classCode);
        }
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
    }

    private boolean validClassCode(String value) {
        return StringUtils.hasText(value) && value.length() <= 64 && value.matches("[A-Z0-9_-]+");
    }

    private String normalizeClassCode(Object value) {
        return stringValue(value).toUpperCase(Locale.ROOT);
    }

    private String generateClassCode() {
        return "CLS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void writeOperationLog(User actor, String operationType) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, user_id, role, module, operation_type, operation_result, operation_time)
                VALUES (?, ?, ?, 'CLASS', ?, 'SUCCESS', CURRENT_TIMESTAMP)
                """, "op-" + UUID.randomUUID(), String.valueOf(actor.getId()), actor.getRole(), operationType);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
