package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProfileServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final ProfileService profileService = new ProfileService(jdbcTemplate);

    @Test
    void getRejectsOtherStudent() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileService.get("student001", "job-java-backend", user("student002", "STUDENT")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void getRejectsTeacherOutOfScope() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("teacher002"), eq("student001"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileService.get("student001", "job-java-backend", user("teacher002", "TEACHER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void getCreatesProfile() {
        when(jdbcTemplate.queryForList("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", "job-java-backend"))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend")));
        when(jdbcTemplate.queryForList(anyString(), eq("student001"))).thenReturn(List.of(Map.of(
                "evidence_id", "evidence-1",
                "score", 82.0
        )));
        when(jdbcTemplate.queryForList(anyString(), eq("student001"), eq("job-java-backend"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.startsWith("profile-"))).thenReturn(List.of());

        Map<String, Object> result = profileService.get("student001", "job-java-backend", user("student001", "STUDENT"));

        assertEquals(true, String.valueOf(result.get("profile_id")).startsWith("profile-"));
        assertEquals(1, ((List<?>) result.get("evidences")).size());
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
