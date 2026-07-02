package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserAdminService {

    private static final List<String> ROLES = List.of("STUDENT", "TEACHER", "EDU_ADMIN");
    private static final List<String> STATUSES = List.of("ENABLED", "DISABLED");

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository userRepository, JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> list(String role, String status, String keyword, Integer pageNo, Integer pageSize) {
        validatePage(pageNo, pageSize);
        String normalizedRole = normalize(role, ROLES, false);
        String normalizedStatus = normalize(status, STATUSES, false);

        List<Object> params = new ArrayList<>();
        String where = buildWhere(normalizedRole, normalizedStatus, keyword, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT CAST(id AS CHAR) AS user_id, account, name, role, status, permission_scope, created_time, updated_time
                FROM sys_user
                """ + where + """
                ORDER BY id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
    }

    @Transactional
    public Map<String, Object> createUser(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String account = stringValue(request.get("account"));
        String password = stringValue(request.get("password"));
        String name = stringValue(request.get("name"));
        String role = upperValue(request.get("role"));
        String status = upperValue(request.get("status"));
        String permissionScope = stringValue(request.get("permission_scope"));
        if (!validUserText(account, 64) || !StringUtils.hasText(password) || !validUserText(name, 64)
                || !ROLES.contains(role) || !STATUSES.contains(status) || !validUserText(permissionScope, 200)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (userRepository.findByAccount(account).isPresent()) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }

        User user = new User();
        user.setAccount(account);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name);
        user.setRole(role);
        user.setStatus(status);
        user.setPermissionScope(permissionScope);
        userRepository.save(user);

        syncStudentProfile(user, request.get("profile"), true);
        writeOperationLog(actor, "CREATE_USER");
        return userResponse(user);
    }

    @Transactional
    public Map<String, Object> updateUser(Long userId, Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
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

        if (request.containsKey("profile")) {
            syncStudentProfile(user, request.get("profile"), false);
        }
        writeOperationLog(actor, "UPDATE_USER");

        return userResponse(user);
    }

    private void syncStudentProfile(User user, Object profileObject, boolean createDefault) {
        if (!"STUDENT".equalsIgnoreCase(user.getRole())) {
            return;
        }
        Map<?, ?> profile = profileMap(profileObject);
        if (profile.isEmpty() && !createDefault) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String studentId = profileValue(profile, "student_id", user.getAccount());
        String studentNo = profileValue(profile, "student_no", user.getAccount());
        String majorId = profileValue(profile, "major_id", null);
        String classId = profileValue(profile, "class_id", null);
        String targetJobId = profileValue(profile, "target_job_id", null);
        String enrollmentYear = profileValue(profile, "enrollment_year", null);
        if (!validUserText(studentId, 64)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        jdbcTemplate.update("""
                INSERT INTO student_profile
                  (student_id, user_id, student_no, major_id, class_id, target_job_id, enrollment_year)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  user_id = VALUES(user_id),
                  student_no = VALUES(student_no),
                  major_id = VALUES(major_id),
                  class_id = VALUES(class_id),
                  target_job_id = VALUES(target_job_id),
                  enrollment_year = VALUES(enrollment_year)
                """, studentId, user.getAccount(), studentNo, majorId, classId, targetJobId, enrollmentYear);
    }

    private Map<?, ?> profileMap(Object profileObject) {
        if (profileObject == null) {
            return Map.of();
        }
        if (!(profileObject instanceof Map<?, ?> profile)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return profile;
    }

    private String profileValue(Map<?, ?> profile, String key, String defaultValue) {
        String value = stringValue(profile.get(key));
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Map<String, Object> userResponse(User user) {
        return Map.of(
                "user_id", String.valueOf(user.getId()),
                "role", user.getRole(),
                "status", user.getStatus(),
                "permission_scope", user.getPermissionScope()
        );
    }

    private void writeOperationLog(User actor, String operationType) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, user_id, role, module, operation_type, operation_result, operation_time)
                VALUES (?, ?, ?, 'USER', ?, 'SUCCESS', CURRENT_TIMESTAMP)
                """, "op-" + UUID.randomUUID(), String.valueOf(actor.getId()), actor.getRole(), operationType);
    }

    private String upperValue(Object value) {
        return stringValue(value).toUpperCase(Locale.ROOT);
    }

    private String buildWhere(String role, String status, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(role)) {
            where.append("AND role = ?\n");
            params.add(role);
        }
        if (StringUtils.hasText(status)) {
            where.append("AND status = ?\n");
            params.add(status);
        }
        if (StringUtils.hasText(keyword)) {
            where.append("AND (account LIKE ? OR name LIKE ?)\n");
            String value = "%" + keyword.trim() + "%";
            params.add(value);
            params.add(value);
        }
        return where.toString();
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private boolean validUserText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.length() <= maxLength;
    }

    private String normalize(String value, List<String> allowed, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
