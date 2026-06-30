package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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

    @Transactional
    public Map<String, Object> get(String studentId, String jobId, User actor) {
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
        if (profileExists(profileId)) {
            jdbcTemplate.update("""
                    UPDATE ability_profile
                    SET profile_status = ?, knowledge_mastery = ?, skill_mastery = ?, updated_time = ?
                    WHERE profile_id = ?
                    """, evidences.isEmpty() ? "数据不足" : "初始画像", knowledgeMastery, skillMastery,
                    Timestamp.valueOf(now), profileId);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO ability_profile
                      (profile_id, student_id, job_id, profile_status, knowledge_mastery, skill_mastery, updated_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, profileId, studentId, jobId, evidences.isEmpty() ? "数据不足" : "初始画像",
                    knowledgeMastery, skillMastery, Timestamp.valueOf(now));
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
                "knowledge_mastery", knowledgeMastery,
                "skill_mastery", skillMastery,
                "evidences", evidences,
                "recommendations", recommendations
        );
    }

    private void requireJob(String jobId) {
        if (jdbcTemplate.queryForList("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", jobId).isEmpty()) {
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
                FROM course_class
                WHERE teacher_id = ? AND ? = 'student001'
                """, Integer.class, teacherId, studentId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
