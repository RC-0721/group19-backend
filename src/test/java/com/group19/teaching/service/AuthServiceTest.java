package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.dto.LoginRequest;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.domain.vo.LoginResponse;
import com.group19.teaching.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String DEMO_PASSWORD_HASH = "$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC";

    private AuthService authService;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        jdbcTemplate = mock(JdbcTemplate.class);
        authService = new AuthService(new InMemoryUserRepository(Map.of(
                "student001", user(1L, "student001", DEMO_PASSWORD_HASH, "学生一", "STUDENT", "ENABLED", "ALL"),
                "teacher001", user(3L, "teacher001", DEMO_PASSWORD_HASH, "教师一", "TEACHER", "ENABLED", "class-cs-2026"),
                "disabled001", user(2L, "disabled001", DEMO_PASSWORD_HASH, "禁用用户", "STUDENT", "DISABLED", "ALL")
        )), passwordEncoder, jdbcTemplate);
    }

    @Test
    void loginReturnsTokenWhenCredentialIsValid() {
        LoginResponse response = authService.login(new LoginRequest("student001", "123456", "STUDENT"));

        assertFalse(response.token().isBlank());
        assertEquals("1", response.userId());
        assertEquals("学生一", response.name());
        assertEquals("STUDENT", response.role());
    }

    @Test
    void requireRoleAcceptsIssuedToken() {
        LoginResponse response = authService.login(new LoginRequest("student001", "123456", "STUDENT"));

        assertEquals("student001", authService.requireRole("Bearer " + response.token(), "STUDENT").getAccount());
    }

    @Test
    void currentUserReturnsStudentProfileWithoutPasswordHash() {
        when(jdbcTemplate.queryForList(anyString(), eq("student001"))).thenReturn(List.of(Map.of(
                "student_id", "student001",
                "class_id", "class-cs-2026"
        )));
        LoginResponse response = authService.login(new LoginRequest("student001", "123456", "STUDENT"));

        Map<String, Object> result = authService.currentUser(response.token());

        assertEquals("student001", result.get("account"));
        assertEquals("ALL", result.get("permission_scope"));
        assertFalse(result.containsKey("password_hash"));
        assertEquals("student001", ((Map<?, ?>) result.get("profile")).get("student_id"));
    }

    @Test
    void currentUserReturnsTeacherProfileFallback() {
        LoginResponse response = authService.login(new LoginRequest("teacher001", "123456", "TEACHER"));

        Map<String, Object> result = authService.currentUser(response.token());

        assertEquals("TEACHER", result.get("role"));
        assertEquals("class-cs-2026", result.get("permission_scope"));
        assertEquals("teacher001", ((Map<?, ?>) result.get("profile")).get("account"));
    }

    @Test
    void refreshIssuesNewTokenAndInvalidatesOldOne() {
        LoginResponse response = authService.login(new LoginRequest("student001", "123456", "STUDENT"));

        Map<String, Object> result = authService.refresh("Bearer " + response.token());

        String newToken = String.valueOf(result.get("token"));
        assertNotEquals(response.token(), newToken);
        assertEquals(7200, result.get("expires_in"));
        assertEquals("1", result.get("user_id"));
        assertEquals("student001", authService.requireRole(newToken, "STUDENT").getAccount());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.requireRole(response.token(), "STUDENT"));
        assertEquals(ErrorCode.AUTH_FAILED, exception.errorCode());
    }

    @Test
    void refreshRejectsDisabledAccount() {
        LoginResponse response = authService.login(new LoginRequest("student001", "123456", "STUDENT"));
        authService.requireUser(response.token()).setStatus("DISABLED");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.refresh(response.token()));

        assertEquals(ErrorCode.AUTH_FAILED, exception.errorCode());
    }

    @Test
    void requireRoleRejectsMissingToken() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.requireRole(null, "STUDENT"));

        assertEquals(ErrorCode.AUTH_FAILED, exception.errorCode());
    }

    @Test
    void requireRoleRejectsWrongRole() {
        LoginResponse response = authService.login(new LoginRequest("student001", "123456", "STUDENT"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.requireRole(response.token(), "TEACHER"));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void loginRejectsWrongPassword() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("student001", "wrong", "STUDENT")));

        assertEquals(ErrorCode.AUTH_FAILED, exception.errorCode());
    }

    @Test
    void loginRejectsWrongRole() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("student001", "123456", "TEACHER")));

        assertEquals(ErrorCode.AUTH_FAILED, exception.errorCode());
    }

    @Test
    void loginRejectsDisabledUser() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("disabled001", "123456", "STUDENT")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    private static User user(Long id, String account, String passwordHash, String name, String role, String status,
            String permissionScope) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setPasswordHash(passwordHash);
        user.setName(name);
        user.setRole(role);
        user.setStatus(status);
        user.setPermissionScope(permissionScope);
        return user;
    }

    private record InMemoryUserRepository(Map<String, User> users) implements UserRepository {

        @Override
        public Optional<User> findByAccount(String account) {
            return Optional.ofNullable(new HashMap<>(users).get(account));
        }

        @Override
        public Optional<User> findById(Long id) {
            return users.values().stream()
                    .filter(user -> id.equals(user.getId()))
                    .findFirst();
        }

        @Override
        public void save(User user) {
            throw new UnsupportedOperationException("not needed in auth tests");
        }

        @Override
        public void updateUserState(User user) {
            throw new UnsupportedOperationException("not needed in auth tests");
        }
    }
}

