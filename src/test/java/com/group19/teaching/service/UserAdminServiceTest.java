package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.dto.LoginRequest;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserAdminServiceTest {

    private static final String DEMO_PASSWORD_HASH = "$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC";

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final UserAdminService userAdminService = new UserAdminService(userRepository, jdbcTemplate);

    @Test
    void updateUserUpdatesStateAndWritesOperationLog() {
        User target = user(2L, "student001", "STUDENT", "ENABLED");
        User actor = user(9L, "admin001", "EDU_ADMIN", "ENABLED");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        Map<String, Object> result = userAdminService.updateUser(2L, Map.of(
                "role", "TEACHER",
                "status", "DISABLED",
                "permission_scope", "class-cs-2026"
        ), actor);

        assertEquals("2", result.get("user_id"));
        assertEquals("TEACHER", target.getRole());
        assertEquals("DISABLED", target.getStatus());
        assertEquals("class-cs-2026", target.getPermissionScope());
        verify(userRepository).updateUserState(target);
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                org.mockito.ArgumentMatchers.any(), eq("9"), eq("EDU_ADMIN"));
    }

    @Test
    void updateUserRejectsInvalidRole() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAdminService.updateUser(2L, Map.of(
                        "role", "GUEST",
                        "status", "ENABLED",
                        "permission_scope", "ALL"
                ), user(9L, "admin001", "EDU_ADMIN", "ENABLED")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void updateUserRejectsInvalidStatus() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAdminService.updateUser(2L, Map.of(
                        "role", "STUDENT",
                        "status", "LOCKED",
                        "permission_scope", "ALL"
                ), user(9L, "admin001", "EDU_ADMIN", "ENABLED")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void updateUserRejectsMissingUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAdminService.updateUser(404L, Map.of(
                        "role", "STUDENT",
                        "status", "ENABLED",
                        "permission_scope", "ALL"
                ), user(9L, "admin001", "EDU_ADMIN", "ENABLED")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void loginRejectsUserAfterAdminDisablesAccount() {
        MutableUserRepository repository = new MutableUserRepository();
        repository.save(user(1L, "student001", "STUDENT", "ENABLED"));
        UserAdminService service = new UserAdminService(repository, jdbcTemplate);
        AuthService authService = new AuthService(repository, new BCryptPasswordEncoder());

        service.updateUser(1L, Map.of(
                "role", "STUDENT",
                "status", "DISABLED",
                "permission_scope", "ALL"
        ), user(9L, "admin001", "EDU_ADMIN", "ENABLED"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("student001", "123456", "STUDENT")));
        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    private static User user(Long id, String account, String role, String status) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setPasswordHash(DEMO_PASSWORD_HASH);
        user.setName(account);
        user.setRole(role);
        user.setStatus(status);
        user.setPermissionScope("ALL");
        return user;
    }

    private static class MutableUserRepository implements UserRepository {

        private final Map<String, User> byAccount = new HashMap<>();
        private final Map<Long, User> byId = new HashMap<>();

        void save(User user) {
            byAccount.put(user.getAccount(), user);
            byId.put(user.getId(), user);
        }

        @Override
        public Optional<User> findByAccount(String account) {
            return Optional.ofNullable(byAccount.get(account));
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void updateUserState(User user) {
            save(user);
        }
    }
}
