package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TeacherDockingService {

    private final JdbcTemplate jdbcTemplate;

    public TeacherDockingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> options(User actor) {
        requireTeacher(actor);
        String teacherId = actor.getAccount();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courses", jdbcTemplate.queryForList("""
                SELECT DISTINCT c.course_id, c.course_name, c.status
                FROM course c
                JOIN course_class cc ON cc.course_id = c.course_id
                WHERE cc.teacher_id = ?
                ORDER BY c.course_name
                """, teacherId));
        result.put("course_classes", jdbcTemplate.queryForList("""
                SELECT cc.course_class_id, cc.course_id, cc.class_id, cl.class_name, cc.semester, cc.status
                FROM course_class cc
                LEFT JOIN `class` cl ON cl.class_id = cc.class_id
                WHERE cc.teacher_id = ?
                ORDER BY cc.semester DESC, cl.class_name
                """, teacherId));
        result.put("chapters", jdbcTemplate.queryForList("""
                SELECT DISTINCT ch.chapter_id, ch.course_id, ch.chapter_name,
                       ch.sort_order AS chapter_order, ch.status
                FROM chapter ch
                JOIN course_class cc ON cc.course_id = ch.course_id
                WHERE cc.teacher_id = ?
                ORDER BY ch.course_id, ch.sort_order
                """, teacherId));
        result.put("materials", jdbcTemplate.queryForList("""
                SELECT DISTINCT m.material_id, m.course_id, m.chapter_id, m.file_name, m.file_type, m.parse_status
                FROM course_material m
                JOIN course_class cc ON cc.course_id = m.course_id
                WHERE cc.teacher_id = ?
                ORDER BY m.material_id DESC
                """, teacherId));
        result.put("jobs", jdbcTemplate.queryForList("""
                SELECT job_id, job_name, status
                FROM job_direction
                WHERE status = '启用'
                ORDER BY job_name
                """));
        return result;
    }

    public Map<String, Object> dashboard(User actor) {
        requireTeacher(actor);
        String teacherId = actor.getAccount();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_count", count("""
                SELECT COUNT(DISTINCT course_id)
                FROM course_class
                WHERE teacher_id = ?
                """, teacherId));
        result.put("course_class_count", count("""
                SELECT COUNT(*)
                FROM course_class
                WHERE teacher_id = ?
                """, teacherId));
        result.put("student_count", count("""
                SELECT COUNT(DISTINCT sp.student_id)
                FROM course_class cc
                JOIN student_profile sp ON sp.class_id = cc.class_id
                WHERE cc.teacher_id = ?
                """, teacherId));
        result.put("material_count", count("""
                SELECT COUNT(DISTINCT m.material_id)
                FROM course_material m
                JOIN course_class cc ON cc.course_id = m.course_id
                WHERE cc.teacher_id = ?
                """, teacherId));
        result.put("question_count", count("""
                SELECT COUNT(DISTINCT q.question_id)
                FROM question q
                JOIN question_knowledge_relation qkr ON q.question_id = qkr.question_id
                JOIN knowledge_point kp ON qkr.knowledge_id = kp.knowledge_id
                JOIN course_class cc ON cc.course_id = kp.course_id
                WHERE cc.teacher_id = ?
                """, teacherId));
        result.put("available_job_count", count("""
                SELECT COUNT(*)
                FROM job_direction
                WHERE status = '启用'
                """));
        result.put("project_task_count", count("""
                SELECT COUNT(DISTINCT pt.project_task_id)
                FROM project_task pt
                JOIN course_class cc ON cc.course_id = pt.course_id
                WHERE cc.teacher_id = ?
                """, teacherId));
        result.put("homework_count", count("""
                SELECT COUNT(DISTINCT h.homework_id)
                FROM homework h
                JOIN course_class cc ON cc.course_class_id = h.course_class_id
                WHERE cc.teacher_id = ?
                """, teacherId));
        result.put("pending_homework_review_count", count("""
                SELECT COUNT(DISTINCT hs.submit_id)
                FROM homework_submit hs
                JOIN homework h ON hs.homework_id = h.homework_id
                JOIN course_class cc ON cc.course_class_id = h.course_class_id
                WHERE cc.teacher_id = ? AND hs.submit_status = '待批改'
                """, teacherId));
        result.put("pending_material_review_count", pendingMaterialReviewCount(teacherId));
        result.put("recent_homeworks", recentHomeworks(teacherId));
        result.put("recent_projects", recentProjects(teacherId));
        return result;
    }

    public Map<String, Object> profileAnalysisOptions(User actor) {
        requireTeacher(actor);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                  cc.course_class_id,
                  cc.course_id,
                  c.course_name,
                  cc.class_id,
                  cl.class_name,
                  sp.target_job_id AS job_id,
                  jd.job_name,
                  COUNT(DISTINCT sp.student_id) AS student_count,
                  COUNT(DISTINCT ae.evidence_id) AS evidence_count
                FROM course_class cc
                JOIN course c ON c.course_id = cc.course_id
                LEFT JOIN `class` cl ON cl.class_id = cc.class_id
                JOIN student_profile sp ON sp.class_id = cc.class_id
                LEFT JOIN job_direction jd ON jd.job_id = sp.target_job_id
                LEFT JOIN ability_evidence ae ON ae.student_id = sp.student_id
                WHERE cc.teacher_id = ?
                  AND sp.target_job_id IS NOT NULL
                  AND sp.target_job_id <> ''
                GROUP BY cc.course_class_id, cc.course_id, c.course_name, cc.class_id, cl.class_name,
                         sp.target_job_id, jd.job_name
                ORDER BY evidence_count DESC, student_count DESC
                """, actor.getAccount());
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        int recommendedIndex = -1;
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> item = new LinkedHashMap<>(rows.get(index));
            item.put("recommended", false);
            items.add(item);
            if (recommendedIndex < 0 && intValue(item.get("evidence_count")) > 0) {
                recommendedIndex = index;
            }
        }
        if (recommendedIndex < 0) {
            for (int index = 0; index < items.size(); index++) {
                if (intValue(items.get(index).get("student_count")) > 0) {
                    recommendedIndex = index;
                    break;
                }
            }
        }
        if (recommendedIndex >= 0) {
            items.get(recommendedIndex).put("recommended", true);
        }
        return Map.of("items", items);
    }

    private List<Map<String, Object>> recentHomeworks(String teacherId) {
        return jdbcTemplate.queryForList("""
                SELECT h.homework_id, h.title, h.course_class_id, cl.class_name, h.deadline, h.status
                FROM homework h
                JOIN course_class cc ON cc.course_class_id = h.course_class_id
                LEFT JOIN `class` cl ON cl.class_id = cc.class_id
                WHERE cc.teacher_id = ?
                ORDER BY h.deadline DESC, h.homework_id
                LIMIT 5
                """, teacherId);
    }

    private List<Map<String, Object>> recentProjects(String teacherId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT pt.project_task_id, pt.title AS project_name, pt.course_id, c.course_name,
                       pt.job_id, jd.job_name, pt.status
                FROM project_task pt
                JOIN course_class cc ON cc.course_id = pt.course_id
                LEFT JOIN course c ON c.course_id = pt.course_id
                LEFT JOIN job_direction jd ON jd.job_id = pt.job_id
                WHERE cc.teacher_id = ?
                ORDER BY pt.project_task_id DESC
                LIMIT 5
                """, teacherId);
    }

    private int pendingMaterialReviewCount(String teacherId) {
        int auditCount = count("""
                SELECT COUNT(DISTINCT maa.audit_id)
                FROM material_ai_audit maa
                JOIN course_material m ON maa.material_id = m.material_id
                JOIN course_class cc ON cc.course_id = m.course_id
                WHERE cc.teacher_id = ? AND maa.review_status = '待复核'
                """, teacherId);
        int scoreCount = count("""
                SELECT COUNT(DISTINCT cs.score_id)
                FROM content_score cs
                JOIN course_material m ON cs.source_type = 'MATERIAL' AND cs.source_id = m.material_id
                JOIN course_class cc ON cc.course_id = m.course_id
                WHERE cc.teacher_id = ? AND cs.review_status = '待复核'
                """, teacherId);
        return auditCount + scoreCount;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void requireTeacher(User actor) {
        if (actor == null || !"TEACHER".equalsIgnoreCase(actor.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
