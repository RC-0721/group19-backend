package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TrainingPlanService {

    private static final List<String> PLAN_STATUSES = List.of("草稿", "启用", "停用");

    private final JdbcTemplate jdbcTemplate;

    public TrainingPlanService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(String majorId, String status, String keyword,
                                    Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        String normalizedStatus = normalize(status, PLAN_STATUSES, false);
        List<Object> params = new ArrayList<>();
        String where = planWhere(majorId, normalizedStatus, keyword, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_plan tp " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT tp.plan_id, tp.major_id, m.major_name, tp.plan_name, tp.total_credits,
                       tp.version, tp.status, tp.created_time, tp.updated_time
                FROM training_plan tp
                LEFT JOIN major m ON tp.major_id = m.major_id
                """ + where + """
                ORDER BY tp.major_id, tp.version DESC, tp.plan_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return page(records, total, pageNo, pageSize);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String majorId = stringValue(request.get("major_id"));
        String planName = stringValue(request.get("plan_name"));
        Double totalCredits = doubleValue(request.get("total_credits"));
        String version = stringValue(request.get("version"));
        String status = normalize(stringValue(request.get("status")), PLAN_STATUSES, true);
        if (!StringUtils.hasText(majorId) || !StringUtils.hasText(planName)
                || totalCredits == null || totalCredits < 0 || !StringUtils.hasText(version)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireMajor(majorId);

        String planId = "training-plan-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO training_plan
                  (plan_id, major_id, plan_name, total_credits, version, status, created_time, updated_time)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, planId, majorId, planName, totalCredits, version, status);
        writeOperationLog(actor, "CREATE_TRAINING_PLAN");
        return Map.of("plan_id", planId, "major_id", majorId, "status", status);
    }

    public Map<String, Object> detail(String planId, User actor) {
        Map<String, Object> plan = plan(planId);
        requirePlanScope(plan, actor);
        return Map.of(
                "plan", plan,
                "courses", planCourses(planId),
                "prerequisites", prerequisitesForPlan(planId)
        );
    }

    public Map<String, Object> listCourses(String planId, User actor) {
        Map<String, Object> plan = plan(planId);
        requirePlanScope(plan, actor);
        return Map.of("records", planCourses(planId));
    }

    @Transactional
    public Map<String, Object> addCourse(String planId, Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requirePlan(planId);
        String courseId = stringValue(request.get("course_id"));
        Boolean required = booleanValue(request.get("is_required"));
        Integer semester = intValue(request.get("suggested_semester"));
        if (!StringUtils.hasText(courseId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireCourse(courseId);
        Double credit = request.containsKey("credit") ? doubleValue(request.get("credit")) : courseCredit(courseId);
        if (semester == null || semester < 1 || semester > 12 || credit == null || credit < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        String id = "tpc-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO training_plan_course
                  (id, plan_id, course_id, is_required, suggested_semester, credit)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE is_required=VALUES(is_required),
                  suggested_semester=VALUES(suggested_semester), credit=VALUES(credit)
                """, id, planId, courseId, required == null || required, semester, credit);
        writeOperationLog(actor, "UPSERT_TRAINING_PLAN_COURSE");
        return Map.of("plan_id", planId, "course_id", courseId, "suggested_semester", semester);
    }

    @Transactional
    public Map<String, Object> addPrerequisite(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String courseId = stringValue(request.get("course_id"));
        String prerequisiteCourseId = stringValue(request.get("prerequisite_course_id"));
        if (!StringUtils.hasText(courseId) || !StringUtils.hasText(prerequisiteCourseId)
                || courseId.equals(prerequisiteCourseId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireCourse(courseId);
        requireCourse(prerequisiteCourseId);
        if (wouldCreateCycle(courseId, prerequisiteCourseId)) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }

        String id = "course-prereq-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO course_prerequisite
                  (id, course_id, prerequisite_course_id, relation_note)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE relation_note=VALUES(relation_note)
                """, id, courseId, prerequisiteCourseId, stringValue(request.get("relation_note")));
        writeOperationLog(actor, "UPSERT_COURSE_PREREQUISITE");
        return Map.of("course_id", courseId, "prerequisite_course_id", prerequisiteCourseId);
    }

    private String planWhere(String majorId, String status, String keyword, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        append(where, params, "tp.major_id", majorId);
        append(where, params, "tp.status", status);
        if (StringUtils.hasText(keyword)) {
            where.append("AND tp.plan_name LIKE ?\n");
            params.add("%" + keyword.trim() + "%");
        }
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM student_profile sp
                        WHERE sp.student_id = ? AND sp.major_id = tp.major_id
                    )
                    """);
            params.add(actor.getAccount());
        }
        return where.toString();
    }

    private void requirePlanScope(Map<String, Object> plan, User actor) {
        if (!"STUDENT".equalsIgnoreCase(actor.getRole())) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM student_profile
                WHERE student_id = ? AND major_id = ?
                """, Integer.class, actor.getAccount(), stringValue(plan.get("major_id")));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean wouldCreateCycle(String courseId, String prerequisiteCourseId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT course_id, prerequisite_course_id
                FROM course_prerequisite
                """);
        Map<String, List<String>> graph = new HashMap<>();
        for (Map<String, Object> row : rows) {
            graph.computeIfAbsent(stringValue(row.get("course_id")), key -> new ArrayList<>())
                    .add(stringValue(row.get("prerequisite_course_id")));
        }
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(prerequisiteCourseId);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            if (courseId.equals(current)) {
                return true;
            }
            queue.addAll(graph.getOrDefault(current, List.of()));
        }
        return false;
    }

    private Map<String, Object> plan(String planId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT tp.plan_id, tp.major_id, m.major_name, tp.plan_name, tp.total_credits,
                       tp.version, tp.status, tp.created_time, tp.updated_time
                FROM training_plan tp
                LEFT JOIN major m ON tp.major_id = m.major_id
                WHERE tp.plan_id = ?
                LIMIT 1
                """, planId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> planCourses(String planId) {
        return jdbcTemplate.queryForList("""
                SELECT tpc.id, tpc.plan_id, tpc.course_id, c.course_name, tpc.is_required,
                       tpc.suggested_semester, tpc.credit, c.course_type, c.status
                FROM training_plan_course tpc
                JOIN course c ON tpc.course_id = c.course_id
                WHERE tpc.plan_id = ?
                ORDER BY tpc.suggested_semester, tpc.is_required DESC, c.course_id
                """, planId);
    }

    private List<Map<String, Object>> prerequisitesForPlan(String planId) {
        return jdbcTemplate.queryForList("""
                SELECT cp.id, cp.course_id, c.course_name, cp.prerequisite_course_id,
                       pc.course_name AS prerequisite_course_name, cp.relation_note
                FROM course_prerequisite cp
                JOIN training_plan_course tpc ON cp.course_id = tpc.course_id
                JOIN course c ON cp.course_id = c.course_id
                JOIN course pc ON cp.prerequisite_course_id = pc.course_id
                WHERE tpc.plan_id = ?
                ORDER BY cp.course_id, cp.prerequisite_course_id
                """, planId);
    }

    private void requirePlan(String planId) {
        if (!StringUtils.hasText(planId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_plan WHERE plan_id = ?",
                Integer.class, planId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
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
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course WHERE course_id = ?",
                Integer.class, courseId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private Double courseCredit(String courseId) {
        if (!StringUtils.hasText(courseId)) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT credit FROM course WHERE course_id = ? LIMIT 1", courseId);
        if (rows.isEmpty()) {
            return null;
        }
        return doubleValue(rows.get(0).get("credit"));
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private String normalize(String value, List<String> allowed, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            return "";
        }
        String normalized = value.trim();
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private Map<String, Object> page(List<Map<String, Object>> records, Integer total, Integer pageNo, Integer pageSize) {
        return Map.of("records", records, "total", total == null ? 0 : total, "page_no", pageNo, "page_size", pageSize);
    }

    private void writeOperationLog(User actor, String operationType) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, user_id, role, module, operation_type, operation_result, operation_time)
                VALUES (?, ?, ?, 'TRAINING_PLAN', ?, 'SUCCESS', CURRENT_TIMESTAMP)
                """, "op-" + UUID.randomUUID(), String.valueOf(actor.getId()), actor.getRole(), operationType);
    }

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
