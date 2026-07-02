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
public class TeachingStandardService {

    private static final List<String> STATUSES = List.of("启用", "停用");

    private final JdbcTemplate jdbcTemplate;

    public TeachingStandardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(String majorId, String courseId, String standardType, String status,
                                    Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        String normalizedStatus = normalizeStatus(status, false);

        List<Object> params = new ArrayList<>();
        String where = buildWhere(majorId, courseId, standardType, normalizedStatus, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teaching_standard ts " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT ts.standard_id, ts.major_id, m.major_name,
                       ts.course_id, c.course_name,
                       ts.standard_type, ts.title, ts.content, ts.version, ts.status
                FROM teaching_standard ts
                LEFT JOIN major m ON ts.major_id = m.major_id
                LEFT JOIN course c ON ts.course_id = c.course_id
                """ + where + """
                ORDER BY ts.standard_id
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
        String courseId = stringValue(request.get("course_id"));
        String standardType = stringValue(request.get("standard_type"));
        String title = stringValue(request.get("title"));
        String content = stringValue(request.get("content"));
        String version = stringValue(request.get("version"));
        String status = normalizeStatus(stringValue(request.get("status")), true);
        if (!StringUtils.hasText(standardType) || !StringUtils.hasText(title)
                || !StringUtils.hasText(content) || !StringUtils.hasText(version)
                || (!StringUtils.hasText(majorId) && !StringUtils.hasText(courseId))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireBindings(majorId, courseId);

        String standardId = "standard-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO teaching_standard
                  (standard_id, major_id, course_id, standard_type, title, content, version, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, standardId, blankToNull(majorId), blankToNull(courseId), standardType, title, content,
                version, status);
        writeOperationLog(actor, "CREATE_TEACHING_STANDARD");

        return Map.of("standard_id", standardId, "status", status);
    }

    @Transactional
    public Map<String, Object> update(String standardId, Map<String, Object> request, User actor) {
        if (!StringUtils.hasText(standardId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String status = normalizeStatus(stringValue(request.get("status")), true);
        requireStandard(standardId);

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE teaching_standard SET status = ?");
        params.add(status);
        appendUpdate(sql, params, "title", request.get("title"), false);
        appendUpdate(sql, params, "content", request.get("content"), false);
        appendUpdate(sql, params, "version", request.get("version"), false);
        sql.append(" WHERE standard_id = ?");
        params.add(standardId);
        jdbcTemplate.update(sql.toString(), params.toArray());
        writeOperationLog(actor, "UPDATE_TEACHING_STANDARD");

        return Map.of("standard_id", standardId, "status", status);
    }

    private String buildWhere(String majorId, String courseId, String standardType, String status,
                              User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(majorId)) {
            where.append("AND ts.major_id = ?\n");
            params.add(majorId.trim());
        }
        if (StringUtils.hasText(courseId)) {
            where.append("AND ts.course_id = ?\n");
            params.add(courseId.trim());
        }
        if (StringUtils.hasText(standardType)) {
            where.append("AND ts.standard_type = ?\n");
            params.add(standardType.trim());
        }
        if (StringUtils.hasText(status)) {
            where.append("AND ts.status = ?\n");
            params.add(status);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND (
                        EXISTS (
                            SELECT 1 FROM course_class cc
                            WHERE cc.course_id = ts.course_id AND cc.teacher_id = ?
                        )
                        OR EXISTS (
                            SELECT 1 FROM course c2
                            JOIN course_class cc2 ON c2.course_id = cc2.course_id
                            WHERE c2.major_id = ts.major_id AND cc2.teacher_id = ?
                        )
                    )
                    """);
            params.add(actor.getAccount());
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

    private void requireBindings(String majorId, String courseId) {
        if (StringUtils.hasText(majorId)) {
            requireExists("SELECT COUNT(*) FROM major WHERE major_id = ?", majorId);
        }
        if (StringUtils.hasText(courseId)) {
            requireExists("SELECT COUNT(*) FROM course WHERE course_id = ?", courseId);
        }
    }

    private void requireStandard(String standardId) {
        requireExists("SELECT COUNT(*) FROM teaching_standard WHERE standard_id = ?", standardId);
    }

    private void requireExists(String sql, String id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
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
                VALUES (?, ?, ?, 'TEACHING_STANDARD', ?, 'SUCCESS', CURRENT_TIMESTAMP)
                """, "op-" + UUID.randomUUID(), String.valueOf(actor.getId()), actor.getRole(), operationType);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
