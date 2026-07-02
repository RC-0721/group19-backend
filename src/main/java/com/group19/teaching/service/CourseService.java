package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CourseService {

    private static final List<String> COURSE_STATUSES = List.of("已发布", "停用");
    private static final List<String> CHAPTER_STATUSES = List.of("启用", "停用");

    private final JdbcTemplate jdbcTemplate;

    public CourseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(String keyword, String majorId, String status,
                                    Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        String normalizedStatus = normalizeStatus(status, COURSE_STATUSES, false);

        List<Object> params = new ArrayList<>();
        String where = buildCourseWhere(keyword, majorId, normalizedStatus, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course c " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT c.course_id, c.course_name, c.course_code, c.major_id, m.major_name,
                       c.credit, c.course_type, c.course_goal, c.standard_id, c.status
                FROM course c
                LEFT JOIN major m ON c.major_id = m.major_id
                """ + where + """
                ORDER BY c.course_id
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
        String courseName = stringValue(request.get("course_name"));
        String courseCode = stringValue(request.get("course_code"));
        String majorId = stringValue(request.get("major_id"));
        Double credit = doubleValue(request.get("credit"));
        String status = normalizeStatus(stringValue(request.get("status")), COURSE_STATUSES, true);
        if (!StringUtils.hasText(courseName) || !StringUtils.hasText(courseCode)
                || !StringUtils.hasText(majorId) || credit == null || credit < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireMajor(majorId);

        String courseId = "course-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO course
                  (course_id, course_name, course_code, major_id, credit, course_type, course_goal, standard_id, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                courseId,
                courseName,
                courseCode,
                majorId,
                credit,
                stringValue(request.get("course_type")),
                stringValue(request.get("course_goal")),
                stringValue(request.get("standard_id")),
                status);
        writeOperationLog(actor, "CREATE_COURSE");

        return Map.of(
                "course_id", courseId,
                "course_name", courseName,
                "status", status
        );
    }

    @Transactional
    public Map<String, Object> update(String courseId, Map<String, Object> request, User actor) {
        if (!StringUtils.hasText(courseId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String status = normalizeStatus(stringValue(request.get("status")), COURSE_STATUSES, true);
        requireCourse(courseId);

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE course SET status = ?");
        params.add(status);
        appendUpdate(sql, params, "course_name", request.get("course_name"), false);
        appendUpdate(sql, params, "course_code", request.get("course_code"), false);
        appendMajorUpdate(sql, params, request.get("major_id"));
        appendCreditUpdate(sql, params, request.get("credit"));
        appendUpdate(sql, params, "course_type", request.get("course_type"), true);
        appendUpdate(sql, params, "course_goal", request.get("course_goal"), true);
        appendUpdate(sql, params, "standard_id", request.get("standard_id"), true);
        sql.append(" WHERE course_id = ?");
        params.add(courseId);
        jdbcTemplate.update(sql.toString(), params.toArray());
        writeOperationLog(actor, "UPDATE_COURSE");

        return Map.of(
                "course_id", courseId,
                "status", status
        );
    }

    public Map<String, Object> detail(String courseId, String courseClassId, User actor) {
        List<Map<String, Object>> courseRows = jdbcTemplate.queryForList("""
                SELECT c.course_id, cc.course_class_id, cc.class_id, c.course_name, c.course_type,
                       c.course_goal, cc.teacher_id, cc.teacher_id AS teacher_name, cc.semester, cc.status
                FROM course c
                JOIN course_class cc ON c.course_id = cc.course_id
                WHERE c.course_id = ? AND cc.course_class_id = ?
                LIMIT 1
                """, courseId, courseClassId);
        if (courseRows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        requireCourseScope(courseRows.get(0), actor);
        return Map.of(
                "course", courseRows.get(0),
                "chapters", jdbcTemplate.queryForList("""
                        SELECT chapter_id, chapter_name, parent_id, sort_order AS sort_no, status
                        FROM chapter
                        WHERE course_id = ?
                        ORDER BY sort_order
                        """, courseId),
                "materials", jdbcTemplate.queryForList("""
                        SELECT material_id, chapter_id, file_name, file_type, parse_status
                        FROM course_material
                        WHERE course_id = ?
                        ORDER BY material_id
                        """, courseId),
                "pre_tasks", jdbcTemplate.queryForList("""
                        SELECT pre_task_id AS task_id, title, task_type, status
                        FROM pre_task
                        WHERE course_id = ?
                        ORDER BY pre_task_id
                        """, courseId),
                "homeworks", jdbcTemplate.queryForList("""
                        SELECT homework_id, title, status
                        FROM homework
                        WHERE course_id = ?
                        ORDER BY homework_id
                        """, courseId),
                "questions", jdbcTemplate.queryForList("""
                        SELECT q.question_id, q.stem AS title, q.question_type, q.difficulty
                        FROM question q
                        JOIN question_knowledge_relation qkr ON q.question_id = qkr.question_id
                        JOIN knowledge_point kp ON qkr.knowledge_id = kp.knowledge_id
                        WHERE kp.course_id = ? AND q.audit_status = '已发布'
                        ORDER BY q.question_id
                        LIMIT 10
                        """, courseId),
                "project_tasks", jdbcTemplate.queryForList("""
                        SELECT project_task_id, title, status
                        FROM project_task
                        WHERE course_id = ?
                        ORDER BY project_task_id
                        """, courseId)
        );
    }

    public Map<String, Object> listChapters(String courseId, User actor) {
        requireCourseAccess(courseId, actor);
        return Map.of("records", jdbcTemplate.queryForList("""
                SELECT chapter_id, course_id, parent_id, chapter_name, sort_order, status
                FROM chapter
                WHERE course_id = ?
                ORDER BY sort_order, chapter_id
                """, courseId));
    }

    @Transactional
    public Map<String, Object> createChapter(String courseId, Map<String, Object> request, User actor) {
        requireCourseWriteAccess(courseId, actor);
        String chapterName = stringValue(request.get("chapter_name"));
        Integer sortOrder = intValue(request.get("sort_order"));
        String parentId = stringValue(request.get("parent_id"));
        String status = normalizeStatus(stringValue(request.get("status")), CHAPTER_STATUSES, true);
        if (!StringUtils.hasText(chapterName) || sortOrder == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireParentChapter(courseId, parentId);

        String chapterId = "chapter-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO chapter (chapter_id, course_id, parent_id, chapter_name, sort_order, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, chapterId, courseId, blankToNull(parentId), chapterName, sortOrder, status);
        writeOperationLog(actor, "CREATE_CHAPTER");

        return Map.of(
                "chapter_id", chapterId,
                "course_id", courseId,
                "status", status
        );
    }

    @Transactional
    public Map<String, Object> updateChapter(String courseId, String chapterId,
                                             Map<String, Object> request, User actor) {
        requireCourseWriteAccess(courseId, actor);
        if (!StringUtils.hasText(chapterId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String status = normalizeStatus(stringValue(request.get("status")), CHAPTER_STATUSES, true);
        requireChapter(courseId, chapterId);

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE chapter SET status = ?");
        params.add(status);
        appendUpdate(sql, params, "chapter_name", request.get("chapter_name"), false);
        appendParentUpdate(sql, params, courseId, chapterId, request.get("parent_id"));
        appendSortUpdate(sql, params, request.get("sort_order"));
        sql.append(" WHERE course_id = ? AND chapter_id = ?");
        params.add(courseId);
        params.add(chapterId);
        jdbcTemplate.update(sql.toString(), params.toArray());
        writeOperationLog(actor, "UPDATE_CHAPTER");

        return Map.of(
                "chapter_id", chapterId,
                "status", status
        );
    }

    private String buildCourseWhere(String keyword, String majorId, String status, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(keyword)) {
            where.append("AND c.course_name LIKE ?\n");
            params.add("%" + keyword.trim() + "%");
        }
        if (StringUtils.hasText(majorId)) {
            where.append("AND c.major_id = ?\n");
            params.add(majorId.trim());
        }
        if (StringUtils.hasText(status)) {
            where.append("AND c.status = ?\n");
            params.add(status);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM course_class cc
                        WHERE cc.course_id = c.course_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        }
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM course_class cc
                        JOIN student_profile sp ON cc.class_id = sp.class_id
                        WHERE cc.course_id = c.course_id AND sp.student_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        }
        return where.toString();
    }

    private void requireCourseScope(Map<String, Object> course, User actor) {
        if ("TEACHER".equalsIgnoreCase(actor.getRole())
                && !Objects.equals(String.valueOf(course.get("teacher_id")), actor.getAccount())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM student_profile
                    WHERE student_id = ? AND class_id = ?
                    """, Integer.class, actor.getAccount(), stringValue(course.get("class_id")));
            if (count == null || count == 0) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }

    private void requireCourseWriteAccess(String courseId, User actor) {
        requireCourse(courseId);
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM course_class
                    WHERE course_id = ? AND teacher_id = ?
                    """, Integer.class, courseId, actor.getAccount());
            if (count == null || count == 0) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }

    private void requireCourseAccess(String courseId, User actor) {
        requireCourse(courseId);
        if ("EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            return;
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM course_class
                    WHERE course_id = ? AND teacher_id = ?
                    """, Integer.class, courseId, actor.getAccount());
            if (count == null || count == 0) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM course_class cc
                JOIN student_profile sp ON cc.class_id = sp.class_id
                WHERE cc.course_id = ? AND sp.student_id = ?
                """, Integer.class, courseId, actor.getAccount());
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireMajor(String majorId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM major WHERE major_id = ?",
                Integer.class, majorId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireCourse(String courseId) {
        if (!StringUtils.hasText(courseId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course WHERE course_id = ?",
                Integer.class, courseId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireChapter(String courseId, String chapterId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM chapter
                WHERE course_id = ? AND chapter_id = ?
                """, Integer.class, courseId, chapterId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireParentChapter(String courseId, String parentId) {
        if (StringUtils.hasText(parentId)) {
            requireChapter(courseId, parentId);
        }
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private String normalizeStatus(String status, List<String> allowed, boolean required) {
        if (!StringUtils.hasText(status)) {
            if (required) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            return "";
        }
        String value = status.trim();
        if (!allowed.contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return value;
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

    private void appendCreditUpdate(StringBuilder sql, List<Object> params, Object value) {
        if (value == null) {
            return;
        }
        Double credit = doubleValue(value);
        if (credit == null || credit < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        sql.append(", credit = ?");
        params.add(credit);
    }

    private void appendParentUpdate(StringBuilder sql, List<Object> params,
                                    String courseId, String chapterId, Object value) {
        if (value == null) {
            return;
        }
        String parentId = stringValue(value);
        if (parentId.equals(chapterId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireParentChapter(courseId, parentId);
        sql.append(", parent_id = ?");
        params.add(blankToNull(parentId));
    }

    private void appendSortUpdate(StringBuilder sql, List<Object> params, Object value) {
        if (value == null) {
            return;
        }
        Integer sortOrder = intValue(value);
        if (sortOrder == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        sql.append(", sort_order = ?");
        params.add(sortOrder);
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
                VALUES (?, ?, ?, 'COURSE', ?, 'SUCCESS', CURRENT_TIMESTAMP)
                """, "op-" + UUID.randomUUID(), String.valueOf(actor.getId()), actor.getRole(), operationType);
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
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

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
