package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseService {

    private final JdbcTemplate jdbcTemplate;

    public CourseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> detail(String courseId, String courseClassId, User actor) {
        List<Map<String, Object>> courseRows = jdbcTemplate.queryForList("""
                SELECT c.course_id, cc.course_class_id, c.course_name, c.course_type,
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
                        SELECT chapter_id, chapter_name, sort_order AS sort_no
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
                        SELECT task_id, title, task_type, status
                        FROM pre_task
                        WHERE course_id = ?
                        ORDER BY task_id
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

    private void requireCourseScope(Map<String, Object> course, User actor) {
        if ("TEACHER".equalsIgnoreCase(actor.getRole())
                && !Objects.equals(String.valueOf(course.get("teacher_id")), actor.getAccount())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
