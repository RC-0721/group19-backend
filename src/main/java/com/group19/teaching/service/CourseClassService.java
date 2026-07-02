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
public class CourseClassService {

    private static final List<String> STATUSES = List.of("开课中", "停用");

    private final JdbcTemplate jdbcTemplate;

    public CourseClassService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(String courseId, String teacherId, String classId, String status,
                                    Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        String normalizedStatus = normalizeStatus(status, false);

        List<Object> params = new ArrayList<>();
        String where = buildWhere(courseId, teacherId, classId, normalizedStatus, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_class cc " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT cc.course_class_id, cc.course_id, c.course_name,
                       cc.class_id, cls.class_name,
                       cc.teacher_id, u.name AS teacher_name,
                       cc.semester, cc.status
                FROM course_class cc
                LEFT JOIN course c ON cc.course_id = c.course_id
                LEFT JOIN `class` cls ON cc.class_id = cls.class_id
                LEFT JOIN sys_user u ON cc.teacher_id = u.account
                """ + where + """
                ORDER BY cc.course_class_id
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
        String courseId = stringValue(request.get("course_id"));
        String classId = stringValue(request.get("class_id"));
        String teacherId = stringValue(request.get("teacher_id"));
        String semester = stringValue(request.get("semester"));
        String status = normalizeStatus(stringValue(request.get("status")), true);
        if (!StringUtils.hasText(courseId) || !StringUtils.hasText(classId)
                || !StringUtils.hasText(teacherId) || !StringUtils.hasText(semester)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireCourse(courseId);
        requireClass(classId);
        requireTeacher(teacherId);

        String courseClassId = "cc-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO course_class (course_class_id, course_id, class_id, teacher_id, semester, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, courseClassId, courseId, classId, teacherId, semester, status);
        writeOperationLog(actor, "CREATE_COURSE_CLASS");

        return Map.of(
                "course_class_id", courseClassId,
                "course_id", courseId,
                "class_id", classId,
                "teacher_id", teacherId,
                "status", status
        );
    }

    @Transactional
    public Map<String, Object> update(String courseClassId, Map<String, Object> request, User actor) {
        if (!StringUtils.hasText(courseClassId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String status = normalizeStatus(stringValue(request.get("status")), true);
        requireCourseClass(courseClassId);

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE course_class SET status = ?");
        params.add(status);
        appendTeacherUpdate(sql, params, request.get("teacher_id"));
        appendSemesterUpdate(sql, params, request.get("semester"));
        sql.append(" WHERE course_class_id = ?");
        params.add(courseClassId);
        jdbcTemplate.update(sql.toString(), params.toArray());
        writeOperationLog(actor, "UPDATE_COURSE_CLASS");

        return Map.of(
                "course_class_id", courseClassId,
                "status", status
        );
    }

    private String buildWhere(String courseId, String teacherId, String classId, String status,
                              User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(courseId)) {
            where.append("AND cc.course_id = ?\n");
            params.add(courseId.trim());
        }
        if (StringUtils.hasText(teacherId)) {
            where.append("AND cc.teacher_id = ?\n");
            params.add(teacherId.trim());
        }
        if (StringUtils.hasText(classId)) {
            where.append("AND cc.class_id = ?\n");
            params.add(classId.trim());
        }
        if (StringUtils.hasText(status)) {
            where.append("AND cc.status = ?\n");
            params.add(status);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("AND cc.teacher_id = ?\n");
            params.add(actor.getAccount());
        }
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM student_profile sp
                        WHERE sp.class_id = cc.class_id AND sp.student_id = ?
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

    private void requireCourse(String courseId) {
        requireExists("SELECT COUNT(*) FROM course WHERE course_id = ?", courseId);
    }

    private void requireClass(String classId) {
        requireExists("SELECT COUNT(*) FROM `class` WHERE class_id = ?", classId);
    }

    private void requireTeacher(String teacherId) {
        requireExists("SELECT COUNT(*) FROM sys_user WHERE account = ? AND role = 'TEACHER'", teacherId);
    }

    private void requireCourseClass(String courseClassId) {
        requireExists("SELECT COUNT(*) FROM course_class WHERE course_class_id = ?", courseClassId);
    }

    private void requireExists(String sql, String id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void appendTeacherUpdate(StringBuilder sql, List<Object> params, Object value) {
        if (value == null) {
            return;
        }
        String teacherId = stringValue(value);
        if (!StringUtils.hasText(teacherId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireTeacher(teacherId);
        sql.append(", teacher_id = ?");
        params.add(teacherId);
    }

    private void appendSemesterUpdate(StringBuilder sql, List<Object> params, Object value) {
        if (value == null) {
            return;
        }
        String semester = stringValue(value);
        if (!StringUtils.hasText(semester)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        sql.append(", semester = ?");
        params.add(semester);
    }

    private void writeOperationLog(User actor, String operationType) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, user_id, role, module, operation_type, operation_result, operation_time)
                VALUES (?, ?, ?, 'COURSE_CLASS', ?, 'SUCCESS', CURRENT_TIMESTAMP)
                """, "op-" + UUID.randomUUID(), String.valueOf(actor.getId()), actor.getRole(), operationType);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
