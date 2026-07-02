package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.dto.LoginRequest;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.domain.vo.LoginResponse;
import com.group19.teaching.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthLogoutServiceTest {

    private static final String DEMO_PASSWORD_HASH = "$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC";

    @Test
    void logoutInvalidatesIssuedToken() {
        AuthService authService = new AuthService(new Repository(), new BCryptPasswordEncoder(),
                org.mockito.Mockito.mock(JdbcTemplate.class));
        LoginResponse response = authService.login(new LoginRequest("student001", "123456", "STUDENT"));

        authService.logout("Bearer " + response.token());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.requireRole(response.token(), "STUDENT"));
        assertEquals(ErrorCode.AUTH_FAILED, exception.errorCode());
    }

    @Test
    void logoutRejectsMissingToken() {
        AuthService authService = new AuthService(new Repository(), new BCryptPasswordEncoder(),
                org.mockito.Mockito.mock(JdbcTemplate.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.logout(null));

        assertEquals(ErrorCode.AUTH_FAILED, exception.errorCode());
    }

    private static final class Repository implements UserRepository {
        private final User user = user();

        @Override
        public Optional<User> findByAccount(String account) {
            return "student001".equals(account) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.of(user).filter(value -> id.equals(value.getId()));
        }

        @Override
        public void save(User user) {
            throw new UnsupportedOperationException("not needed in auth logout tests");
        }

        @Override
        public void updateUserState(User user) {
            throw new UnsupportedOperationException("not needed in auth logout tests");
        }
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setAccount("student001");
        user.setPasswordHash(DEMO_PASSWORD_HASH);
        user.setName("学生一");
        user.setRole("STUDENT");
        user.setStatus("ENABLED");
        user.setPermissionScope("ALL");
        return user;
    }
}
