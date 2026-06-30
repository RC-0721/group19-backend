package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class JobService {

    private final JdbcTemplate jdbcTemplate;

    public JobService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(String jobId, String techId, Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Object> params = new ArrayList<>();
        String where = "WHERE 1 = 1\n";
        if (StringUtils.hasText(jobId)) {
            where += "AND jss.job_id = ?\n";
            params.add(jobId);
        }
        if (StringUtils.hasText(techId)) {
            where += "AND jss.tech_id = ?\n";
            params.add(techId);
        }
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM job_skill_standard jss
                JOIN job_direction jd ON jss.job_id = jd.job_id
                JOIN tech_stack ts ON jss.tech_id = ts.tech_id
                """ + where, Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT jd.job_id, jd.job_name, jd.job_description, jd.difficulty_level, jd.status,
                       ts.tech_id, ts.tech_name, ts.category, ts.level,
                       jss.skill_id, jss.skill_name, jss.ability_level, jss.evidence_requirement
                FROM job_skill_standard jss
                JOIN job_direction jd ON jss.job_id = jd.job_id
                JOIN tech_stack ts ON jss.tech_id = ts.tech_id
                """ + where + """
                ORDER BY jd.job_id, ts.tech_id, jss.skill_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of("records", records, "total", total == null ? 0 : total,
                "page_no", pageNo, "page_size", pageSize);
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> request) {
        String jobName = stringValue(request.get("job_name"));
        String jobDescription = stringValue(request.get("job_description"));
        String difficultyLevel = stringValue(request.get("difficulty_level"));
        String techName = stringValue(request.get("tech_name"));
        String skillName = stringValue(request.get("skill_name"));
        String abilityLevel = stringValue(request.get("ability_level"));
        String evidenceRequirement = stringValue(request.get("evidence_requirement"));
        if (!StringUtils.hasText(jobName) || !StringUtils.hasText(jobDescription)
                || !StringUtils.hasText(difficultyLevel) || !StringUtils.hasText(techName)
                || !StringUtils.hasText(skillName) || !StringUtils.hasText(abilityLevel)
                || !StringUtils.hasText(evidenceRequirement)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String jobId = findId("SELECT job_id FROM job_direction WHERE job_name = ? LIMIT 1", jobName);
        if (!StringUtils.hasText(jobId)) {
            jobId = "job-" + UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO job_direction (job_id, job_name, job_description, difficulty_level, status)
                    VALUES (?, ?, ?, ?, '启用')
                    """, jobId, jobName, jobDescription, difficultyLevel);
        } else {
            jdbcTemplate.update("""
                    UPDATE job_direction
                    SET job_description = ?, difficulty_level = ?, status = '启用'
                    WHERE job_id = ?
                    """, jobDescription, difficultyLevel, jobId);
        }
        String techId = findId("SELECT tech_id FROM tech_stack WHERE tech_name = ? LIMIT 1", techName);
        if (!StringUtils.hasText(techId)) {
            techId = "tech-" + UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO tech_stack (tech_id, tech_name, category, level, description)
                    VALUES (?, ?, '后端开发', ?, ?)
                    """, techId, techName, abilityLevel, skillName);
        }
        String skillId = findId("""
                SELECT skill_id
                FROM job_skill_standard
                WHERE job_id = ? AND tech_id = ? AND skill_name = ?
                LIMIT 1
                """, jobId, techId, skillName);
        if (!StringUtils.hasText(skillId)) {
            skillId = "skill-" + UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO job_skill_standard
                      (skill_id, job_id, tech_id, skill_name, ability_level, evidence_requirement)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, skillId, jobId, techId, skillName, abilityLevel, evidenceRequirement);
        } else {
            jdbcTemplate.update("""
                    UPDATE job_skill_standard
                    SET ability_level = ?, evidence_requirement = ?
                    WHERE skill_id = ?
                    """, abilityLevel, evidenceRequirement, skillId);
        }
        return Map.of("job_id", jobId, "tech_id", techId, "skill_id", skillId);
    }

    private String findId(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? "" : stringValue(rows.get(0).values().iterator().next());
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
