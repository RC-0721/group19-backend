package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserAdminService userAdminService = new UserAdminService(userRepository, jdbcTemplate, passwordEncoder);

    @Test
    void listUsersReturnsPagedRecordsWithoutPasswordHash() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM sys_user"), eq(Integer.class),
                eq("STUDENT"), eq("ENABLED"), eq("%学生%"), eq("%学生%")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(contains("FROM sys_user"),
                eq("STUDENT"), eq("ENABLED"), eq("%学生%"), eq("%学生%"), eq(10), eq(0)))
                .thenReturn(java.util.List.of(Map.of(
                        "user_id", "1",
                        "account", "student001",
                        "name", "学生一",
                        "role", "STUDENT",
                        "status", "ENABLED",
                        "permission_scope", "ALL"
                )));

        Map<String, Object> result = userAdminService.list("student", "enabled", "学生", 1, 10);

        assertEquals(1, result.get("total"));
        Map<?, ?> record = (Map<?, ?>) ((java.util.List<?>) result.get("records")).get(0);
        assertEquals("student001", record.get("account"));
        assertFalse(record.containsKey("password_hash"));
        verify(jdbcTemplate).queryForList(contains("AND (account LIKE ? OR name LIKE ?)"),
                eq("STUDENT"), eq("ENABLED"), eq("%学生%"), eq("%学生%"), eq(10), eq(0));
    }

    @Test
    void listUsersRejectsInvalidRole() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAdminService.list("GUEST", null, null, 1, 10));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void listUsersRejectsInvalidPage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAdminService.list(null, null, null, 0, 10));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

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
                org.mockito.ArgumentMatchers.any(), eq("9"), eq("EDU_ADMIN"), eq("UPDATE_USER"));
    }

    @Test
    void createUserCreatesEncodedAccountProfileAndOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN", "ENABLED");
        when(userRepository.findByAccount("student002")).thenReturn(Optional.empty());
        org.mockito.Mockito.doAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return null;
        }).when(userRepository).save(any(User.class));

        Map<String, Object> result = userAdminService.createUser(Map.of(
                "account", "student002",
                "password", "123456",
                "name", "学生二",
                "role", "STUDENT",
                "status", "ENABLED",
                "permission_scope", "ALL",
                "profile", Map.of(
                        "student_no", "2026002",
                        "major_id", "major-cs",
                        "class_id", "class-cs-2026",
                        "target_job_id", "job-java-backend",
                        "enrollment_year", "2026"
                )
        ), actor);

        assertEquals("10", result.get("user_id"));
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "student002".equals(saved.getAccount())
                        && passwordEncoder.matches("123456", saved.getPasswordHash())
                        && "学生二".equals(saved.getName())));
        verify(jdbcTemplate).update(contains("INSERT INTO student_profile"),
                eq("student002"), eq("student002"), eq("2026002"), eq("major-cs"),
                eq("class-cs-2026"), eq("job-java-backend"), eq("2026"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                org.mockito.ArgumentMatchers.any(), eq("9"), eq("EDU_ADMIN"), eq("CREATE_USER"));
    }

    @Test
    void createUserRejectsDuplicateAccount() {
        when(userRepository.findByAccount("student001")).thenReturn(Optional.of(user(1L, "student001", "STUDENT", "ENABLED")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAdminService.createUser(Map.of(
                        "account", "student001",
                        "password", "123456",
                        "name", "学生一",
                        "role", "STUDENT",
                        "status", "ENABLED",
                        "permission_scope", "ALL"
                ), user(9L, "admin001", "EDU_ADMIN", "ENABLED")));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserSyncsStudentProfileWhenProfileIsProvided() {
        User target = user(2L, "student001", "STUDENT", "ENABLED");
        User actor = user(9L, "admin001", "EDU_ADMIN", "ENABLED");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        userAdminService.updateUser(2L, Map.of(
                "role", "STUDENT",
                "status", "ENABLED",
                "permission_scope", "ALL",
                "profile", Map.of("student_no", "2026999", "class_id", "class-cs-2026")
        ), actor);

        verify(jdbcTemplate).update(contains("INSERT INTO student_profile"),
                eq("student001"), eq("student001"), eq("2026999"), eq(null),
                eq("class-cs-2026"), eq(null), eq(null));
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
        UserAdminService service = new UserAdminService(repository, jdbcTemplate, passwordEncoder);
        AuthService authService = new AuthService(repository, new BCryptPasswordEncoder(), jdbcTemplate);

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

        @Override
        public Optional<User> findByAccount(String account) {
            return Optional.ofNullable(byAccount.get(account));
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void save(User user) {
            byAccount.put(user.getAccount(), user);
            byId.put(user.getId(), user);
        }

        @Override
        public void updateUserState(User user) {
            save(user);
        }
    }
}
