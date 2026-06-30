package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.dto.LoginRequest;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.domain.vo.LoginResponse;
import com.group19.teaching.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String DEMO_PASSWORD_HASH = "$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC";

    private AuthService authService;

    @BeforeEach
    void setUp() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(new InMemoryUserRepository(Map.of(
                "student001", user(1L, "student001", DEMO_PASSWORD_HASH, "学生一", "STUDENT", "ENABLED"),
                "disabled001", user(2L, "disabled001", DEMO_PASSWORD_HASH, "禁用用户", "STUDENT", "DISABLED")
        )), passwordEncoder);
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

    private static User user(Long id, String account, String passwordHash, String name, String role, String status) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setPasswordHash(passwordHash);
        user.setName(name);
        user.setRole(role);
        user.setStatus(status);
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
        public void updateUserState(User user) {
            throw new UnsupportedOperationException("not needed in auth tests");
        }
    }
}

