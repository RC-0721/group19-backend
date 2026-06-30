package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserAdminService {

    private static final List<String> ROLES = List.of("STUDENT", "TEACHER", "EDU_ADMIN");
    private static final List<String> STATUSES = List.of("ENABLED", "DISABLED");

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public UserAdminService(UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> updateUser(Long userId, Map<String, Object> request, User actor) {
        String role = upperValue(request.get("role"));
        String status = upperValue(request.get("status"));
        String permissionScope = stringValue(request.get("permission_scope"));
        if (userId == null || !ROLES.contains(role) || !STATUSES.contains(status)
                || !StringUtils.hasText(permissionScope)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        user.setRole(role);
        user.setStatus(status);
        user.setPermissionScope(permissionScope);
        userRepository.updateUserState(user);

        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, user_id, role, module, operation_type, operation_result, operation_time)
                VALUES (?, ?, ?, 'USER', 'UPDATE_USER', 'SUCCESS', CURRENT_TIMESTAMP)
                """, "op-" + UUID.randomUUID(), String.valueOf(actor.getId()), actor.getRole());

        return Map.of(
                "user_id", String.valueOf(user.getId()),
                "role", user.getRole(),
                "status", user.getStatus(),
                "permission_scope", user.getPermissionScope()
        );
    }

    private String upperValue(Object value) {
        return stringValue(value).toUpperCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
