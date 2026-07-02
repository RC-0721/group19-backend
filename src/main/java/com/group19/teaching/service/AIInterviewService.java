package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AIInterviewService {

    private static final String MODEL = "mock-ai";

    private final JdbcTemplate jdbcTemplate;

    public AIInterviewService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(
            String studentId,
            String jobId,
            String status,
            Integer pageNo,
            Integer pageSize,
            User actor) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            if (StringUtils.hasText(studentId) && !actor.getAccount().equals(studentId.trim())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            studentId = actor.getAccount();
        } else if ("TEACHER".equalsIgnoreCase(actor.getRole()) && StringUtils.hasText(studentId)) {
            requireTeacherStudent(studentId.trim(), actor.getAccount());
        } else if (!"TEACHER".equalsIgnoreCase(actor.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<Object> params = new ArrayList<>();
        String where = buildListWhere(studentId, jobId, status, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_session s " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT s.session_id, s.student_id, s.job_id, s.scene, s.model, s.prompt_version,
                       s.status, s.created_time, r.report_id, r.score
                FROM ai_session s
                LEFT JOIN ai_interview_report r ON s.session_id = r.session_id
                """ + where + """
                ORDER BY s.created_time DESC, s.session_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of("records", records, "total", total == null ? 0 : total,
                "page_no", pageNo, "page_size", pageSize);
    }

    @Transactional
    public Map<String, Object> start(Map<String, Object> request, User actor) {
        String jobId = stringValue(request.get("job_id"));
        String scene = stringValue(request.get("scene"));
        String difficultyLevel = stringValue(request.get("difficulty_level"));
        String promptVersion = stringValue(request.get("prompt_version"));
        if (!StringUtils.hasText(jobId) || !StringUtils.hasText(scene)
                || !StringUtils.hasText(difficultyLevel) || !StringUtils.hasText(promptVersion)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> job = requireJob(jobId);
        String sessionId = "session-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO ai_session (session_id, student_id, job_id, scene, model, prompt_version, status, created_time)
                VALUES (?, ?, ?, ?, ?, ?, '已创建', ?)
                """, sessionId, actor.getAccount(), jobId, scene, MODEL, promptVersion, Timestamp.valueOf(now));
        String firstQuestion = "请结合" + job.get("job_name") + "方向，说明你最熟悉的一项技术实践。";
        jdbcTemplate.update("""
                INSERT INTO ai_call_log (log_id, scene, model, prompt_version, input_summary, output_summary, call_status)
                VALUES (?, ?, ?, ?, ?, ?, '成功')
                """, "ai-log-" + UUID.randomUUID(), scene, MODEL, promptVersion,
                "start:" + jobId + ":" + difficultyLevel, firstQuestion);
        return Map.of("session_id", sessionId, "status", "已创建", "first_question", firstQuestion);
    }

    @Transactional
    public Map<String, Object> sendMessage(String sessionId, Map<String, Object> request, User actor) {
        String content = stringValue(request.get("message_content"));
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> session = requireSession(sessionId);
        if (!actor.getAccount().equals(stringValue(session.get("student_id")))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO ai_message (message_id, session_id, sender_type, message_content, reference_chunk, created_time)
                VALUES (?, ?, 'STUDENT', ?, NULL, ?)
                """, "msg-" + UUID.randomUUID(), sessionId, content, Timestamp.valueOf(now));
        String referenceChunk = firstReferenceChunk();
        String aiContent = "Mock 面试反馈：回答已覆盖基础概念，请补充项目场景、关键取舍和验证结果。";
        String aiMessageId = "msg-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_message (message_id, session_id, sender_type, message_content, reference_chunk, created_time)
                VALUES (?, ?, 'AI', ?, ?, ?)
                """, aiMessageId, sessionId, aiContent, referenceChunk, Timestamp.valueOf(now));
        jdbcTemplate.update("UPDATE ai_session SET status = '已完成' WHERE session_id = ?", sessionId);

        String reportId = reportId(sessionId);
        String jobId = stringValue(session.get("job_id"));
        Double score = 82.0;
        jdbcTemplate.update("""
                INSERT INTO ai_interview_report (report_id, session_id, job_id, score, strength, weakness, suggestion)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE score = VALUES(score), strength = VALUES(strength),
                    weakness = VALUES(weakness), suggestion = VALUES(suggestion)
                """, reportId, sessionId, jobId, score, "能围绕岗位技术作答",
                "项目化表达和细节验证不足", "继续练习 Spring Boot、数据库和缓存场景题");
        jdbcTemplate.update("""
                INSERT INTO ability_evidence (evidence_id, student_id, source_type, source_id, skill_id, score)
                VALUES (?, ?, 'AI_INTERVIEW', ?, ?, ?)
                ON DUPLICATE KEY UPDATE skill_id = VALUES(skill_id), score = VALUES(score)
                """, "evidence-" + UUID.randomUUID(), actor.getAccount(), reportId, firstSkillId(jobId), score);
        jdbcTemplate.update("""
                INSERT INTO ai_call_log (log_id, scene, model, prompt_version, input_summary, output_summary, call_status)
                VALUES (?, ?, ?, ?, ?, ?, '成功')
                """, "ai-log-" + UUID.randomUUID(), stringValue(session.get("scene")), MODEL,
                stringValue(session.get("prompt_version")), content, aiContent);
        return Map.of("message_id", aiMessageId, "message_content", aiContent,
                "reference_chunk", referenceChunk, "status", "已完成");
    }

    public Map<String, Object> report(String sessionId, User actor) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.report_id, r.job_id, r.score, r.strength, r.weakness, r.suggestion, s.student_id
                FROM ai_interview_report r
                JOIN ai_session s ON r.session_id = s.session_id
                WHERE r.session_id = ?
                LIMIT 1
                """, sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> report = rows.get(0);
        String studentId = stringValue(report.get("student_id"));
        if ("STUDENT".equalsIgnoreCase(actor.getRole()) && !actor.getAccount().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            requireTeacherStudent(studentId, actor.getAccount());
        }
        return Map.of(
                "report_id", report.get("report_id"),
                "job_id", report.get("job_id"),
                "score", report.get("score"),
                "strength", report.get("strength"),
                "weakness", report.get("weakness"),
                "suggestion", report.get("suggestion")
        );
    }

    private Map<String, Object> requireJob(String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT job_id, job_name
                FROM job_direction
                WHERE job_id = ? AND status = '启用'
                LIMIT 1
                """, jobId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> requireSession(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT session_id, student_id, job_id, scene, prompt_version
                FROM ai_session
                WHERE session_id = ?
                LIMIT 1
                """, sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private String firstReferenceChunk() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT chunk_text
                FROM knowledge_chunk
                WHERE status = '已发布'
                ORDER BY chunk_id
                LIMIT 1
                """);
        return rows.isEmpty() ? "" : stringValue(rows.get(0).get("chunk_text"));
    }

    private String reportId(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT report_id
                FROM ai_interview_report
                WHERE session_id = ?
                LIMIT 1
                """, sessionId);
        return rows.isEmpty() ? "report-" + UUID.randomUUID() : stringValue(rows.get(0).get("report_id"));
    }

    private String firstSkillId(String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT skill_id
                FROM job_skill_standard
                WHERE job_id = ?
                ORDER BY skill_id
                LIMIT 1
                """, jobId);
        return rows.isEmpty() ? null : stringValue(rows.get(0).get("skill_id"));
    }

    private void requireTeacherStudent(String studentId, String teacherId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM student_profile sp
                JOIN course_class cc ON sp.class_id = cc.class_id
                WHERE sp.student_id = ? AND cc.teacher_id = ?
                """, Integer.class, studentId, teacherId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String buildListWhere(String studentId, String jobId, String status, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if ("TEACHER".equalsIgnoreCase(actor.getRole()) && !StringUtils.hasText(studentId)) {
            where.append("""
                    AND EXISTS (
                      SELECT 1
                      FROM student_profile sp
                      JOIN course_class cc ON sp.class_id = cc.class_id
                      WHERE sp.student_id = s.student_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        }
        append(where, params, "s.student_id", studentId);
        append(where, params, "s.job_id", jobId);
        append(where, params, "s.status", status);
        return where.toString();
    }

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
