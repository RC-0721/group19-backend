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
public class ProfileService {

    private final JdbcTemplate jdbcTemplate;

    public ProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> classAnalysis(String classId, String courseId, String jobId, User actor) {
        if (!StringUtils.hasText(classId) || blankParam(courseId) || blankParam(jobId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireClass(classId);
        if (StringUtils.hasText(jobId)) {
            requireJob(jobId);
        }
        requireTeacherClass(classId, courseId, actor.getAccount());

        List<Map<String, Object>> evidences = classEvidences(classId, courseId, jobId);
        double avg = evidences.stream()
                .map(row -> row.get("score"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(0);
        long knowledgeCount = evidences.stream()
                .map(row -> stringValue(row.get("knowledge_id")))
                .filter(StringUtils::hasText)
                .count();
        long skillCount = evidences.stream()
                .map(row -> stringValue(row.get("skill_id")))
                .filter(StringUtils::hasText)
                .distinct()
                .count();
        String knowledgeMastery = knowledgeCount == 0
                ? "数据不足"
                : "班级知识平均掌握度 " + Math.round(avg) + "，知识证据 " + knowledgeCount + " 条";
        String skillMastery = skillCount == 0
                ? "数据不足"
                : "岗位技能平均掌握度 " + Math.round(avg) + "，覆盖技能 " + skillCount + " 项";
        return Map.of(
                "knowledge_mastery", knowledgeMastery,
                "skill_mastery", skillMastery,
                "evidences", evidences,
                "recommendations", recommendations(evidences, knowledgeCount, skillCount)
        );
    }

    @Transactional
    public Map<String, Object> get(String studentId, String jobId, User actor) {
        return profile(studentId, jobId, actor, false, "初始画像");
    }

    @Transactional
    public Map<String, Object> refresh(String studentId, String jobId, User actor) {
        Map<String, Object> profile = profile(studentId, jobId, actor, true, "周期更新");
        logOperation(actor, "REFRESH_PROFILE");
        return Map.of(
                "profile_id", profile.get("profile_id"),
                "profile_status", profile.get("profile_status"),
                "updated_time", profile.get("updated_time")
        );
    }

    @Transactional
    public Map<String, Object> listRecommendations(
            String studentId,
            String jobId,
            Integer pageNo,
            Integer pageSize,
            User actor) {
        if (!StringUtils.hasText(studentId) && "STUDENT".equalsIgnoreCase(actor.getRole())) {
            studentId = actor.getAccount();
        }
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> profile = profile(studentId, jobId, actor, false, "初始画像");
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM learning_recommendation
                WHERE profile_id = ?
                """, Integer.class, profile.get("profile_id"));
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT recommend_id, student_id, profile_id, target_weakness, recommend_content, status, created_time
                FROM learning_recommendation
                WHERE profile_id = ?
                ORDER BY created_time DESC, recommend_id
                LIMIT ? OFFSET ?
                """, profile.get("profile_id"), pageSize, (pageNo - 1) * pageSize);
        return Map.of("records", records, "total", total == null ? 0 : total,
                "page_no", pageNo, "page_size", pageSize);
    }

    private Map<String, Object> profile(
            String studentId,
            String jobId,
            User actor,
            boolean refreshLearningEvidence,
            String readyStatus) {
        if (!StringUtils.hasText(studentId) || !StringUtils.hasText(jobId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if ("STUDENT".equalsIgnoreCase(actor.getRole()) && !actor.getAccount().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            requireTeacherStudent(studentId, actor.getAccount());
        }
        requireJob(jobId);
        if (refreshLearningEvidence) {
            upsertLearningEvidence(studentId);
        }
        List<Map<String, Object>> evidences = jdbcTemplate.queryForList("""
                SELECT evidence_id, student_id, source_type, source_id, knowledge_id, skill_id, score
                FROM ability_evidence
                WHERE student_id = ?
                ORDER BY evidence_id
                """, studentId);
        double avg = evidences.stream()
                .map(row -> row.get("score"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(0);
        String knowledgeMastery = evidences.isEmpty() ? "数据不足" : "平均掌握度 " + Math.round(avg) + "，证据 " + evidences.size() + " 条";
        String skillMastery = evidences.isEmpty() ? "数据不足" : "岗位技能掌握度 " + Math.round(avg);
        String profileId = profileId(studentId, jobId);
        LocalDateTime now = LocalDateTime.now();
        String profileStatus = evidences.isEmpty() ? "数据不足" : readyStatus;
        if (profileExists(profileId)) {
            jdbcTemplate.update("""
                    UPDATE ability_profile
                    SET profile_status = ?, knowledge_mastery = ?, skill_mastery = ?, updated_time = ?
                    WHERE profile_id = ?
                    """, profileStatus, knowledgeMastery, skillMastery, Timestamp.valueOf(now), profileId);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO ability_profile
                      (profile_id, student_id, job_id, profile_status, knowledge_mastery, skill_mastery, updated_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, profileId, studentId, jobId, profileStatus, knowledgeMastery, skillMastery,
                    Timestamp.valueOf(now));
        }
        String recommendId = recommendationId(profileId);
        jdbcTemplate.update("""
                INSERT INTO learning_recommendation
                  (recommend_id, student_id, profile_id, target_weakness, recommend_content, status, created_time)
                VALUES (?, ?, ?, ?, ?, '待完成', ?)
                ON DUPLICATE KEY UPDATE target_weakness = VALUES(target_weakness),
                    recommend_content = VALUES(recommend_content), status = VALUES(status)
                """, recommendId, studentId, profileId, "项目化表达和岗位技能证据",
                "优先复训 AI 面试，并补做岗位相关题目和项目验证记录", Timestamp.valueOf(now));
        List<Map<String, Object>> recommendations = jdbcTemplate.queryForList("""
                SELECT recommend_id, target_weakness, recommend_content, status, created_time
                FROM learning_recommendation
                WHERE profile_id = ?
                ORDER BY created_time DESC, recommend_id
                """, profileId);
        return Map.of(
                "profile_id", profileId,
                "profile_status", profileStatus,
                "knowledge_mastery", knowledgeMastery,
                "skill_mastery", skillMastery,
                "evidences", evidences,
                "recommendations", recommendations,
                "updated_time", now
        );
    }

    private void upsertLearningEvidence(String studentId) {
        jdbcTemplate.update("""
                INSERT INTO ability_evidence (evidence_id, student_id, source_type, source_id, knowledge_id, score)
                SELECT CONCAT('evidence-', pr.record_id), pr.student_id, 'PRACTICE', pr.record_id,
                       MIN(qkr.knowledge_id), pr.score
                FROM practice_record pr
                LEFT JOIN question_knowledge_relation qkr ON pr.question_id = qkr.question_id
                WHERE pr.student_id = ?
                GROUP BY pr.record_id, pr.student_id, pr.score
                ON DUPLICATE KEY UPDATE knowledge_id = VALUES(knowledge_id), score = VALUES(score)
                """, studentId);
        jdbcTemplate.update("""
                INSERT INTO ability_evidence (evidence_id, student_id, source_type, source_id, score)
                SELECT CONCAT('evidence-', hr.review_id), hs.student_id, 'HOMEWORK', hr.review_id, hr.teacher_score
                FROM homework_review hr
                JOIN homework_submit hs ON hr.submit_id = hs.submit_id
                WHERE hs.student_id = ? AND hr.teacher_score IS NOT NULL
                ON DUPLICATE KEY UPDATE score = VALUES(score)
                """, studentId);
    }

    private void logOperation(User actor, String operationType) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, user_id, role, module, operation_type, operation_result, operation_time)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """, "log-" + UUID.randomUUID(), actor.getAccount(), actor.getRole(),
                "PROFILE", operationType, "SUCCESS");
    }

    private void requireJob(String jobId) {
        if (jdbcTemplate.queryForList("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", jobId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireClass(String classId) {
        if (jdbcTemplate.queryForList("SELECT class_id FROM class WHERE class_id = ? LIMIT 1", classId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private String profileId(String studentId, String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT profile_id
                FROM ability_profile
                WHERE student_id = ? AND job_id = ?
                LIMIT 1
                """, studentId, jobId);
        return rows.isEmpty() ? "profile-" + UUID.randomUUID() : stringValue(rows.get(0).get("profile_id"));
    }

    private boolean profileExists(String profileId) {
        return !jdbcTemplate.queryForList("SELECT profile_id FROM ability_profile WHERE profile_id = ? LIMIT 1", profileId).isEmpty();
    }

    private String recommendationId(String profileId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT recommend_id
                FROM learning_recommendation
                WHERE profile_id = ?
                ORDER BY recommend_id
                LIMIT 1
                """, profileId);
        return rows.isEmpty() ? "recommend-" + UUID.randomUUID() : stringValue(rows.get(0).get("recommend_id"));
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

    private void requireTeacherClass(String classId, String courseId, String teacherId) {
        Integer count;
        if (StringUtils.hasText(courseId)) {
            count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM course_class
                    WHERE class_id = ? AND course_id = ? AND teacher_id = ?
                    """, Integer.class, classId, courseId, teacherId);
        } else {
            count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM course_class
                    WHERE class_id = ? AND teacher_id = ?
                    """, Integer.class, classId, teacherId);
        }
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private List<Map<String, Object>> classEvidences(String classId, String courseId, String jobId) {
        List<Object> params = new ArrayList<>();
        params.add(classId);
        String sql = """
                SELECT ae.student_id, ae.source_type, ae.source_id, ae.knowledge_id, ae.skill_id, ae.score
                FROM ability_evidence ae
                JOIN student_profile sp ON ae.student_id = sp.student_id
                LEFT JOIN knowledge_point kp ON ae.knowledge_id = kp.knowledge_id
                LEFT JOIN job_skill_standard jss ON ae.skill_id = jss.skill_id
                WHERE sp.class_id = ?
                """;
        if (StringUtils.hasText(courseId)) {
            sql += " AND (ae.knowledge_id IS NULL OR kp.course_id = ?)";
            params.add(courseId);
        }
        if (StringUtils.hasText(jobId)) {
            sql += " AND jss.job_id = ?";
            params.add(jobId);
        }
        sql += " ORDER BY ae.student_id, ae.evidence_id";
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    private List<Map<String, Object>> recommendations(List<Map<String, Object>> evidences, long knowledgeCount, long skillCount) {
        if (evidences.isEmpty()) {
            return List.of(Map.of("recommend_content", "班级能力证据不足，先组织一次课程练习或项目评价。"));
        }
        if (knowledgeCount == 0) {
            return List.of(Map.of("recommend_content", "优先补充课程知识点练习，形成可追踪的知识掌握证据。"));
        }
        if (skillCount == 0) {
            return List.of(Map.of("recommend_content", "优先安排岗位技能任务，把课程表现映射到岗位技能标准。"));
        }
        return List.of(Map.of("recommend_content", "围绕低分证据集中讲评，再安排岗位相关练习巩固。"));
    }

    private boolean blankParam(String value) {
        return value != null && !StringUtils.hasText(value);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
