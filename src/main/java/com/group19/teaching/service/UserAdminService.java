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

    public Map<String, Object> meProfile(User actor) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT avatar_url, nickname, gender, location, school, motto
                FROM user_profile_ext
                WHERE user_id = ?
                LIMIT 1
                """, actor.getAccount());
        return profileResponse(actor, rows.isEmpty() ? Map.of() : rows.get(0));
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
    public Map<String, Object> registerStudent(Map<String, Object> request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String account = stringValue(request.get("account"));
        String password = stringValue(request.get("password"));
        String name = stringValue(request.get("name"));
        String studentNo = stringValue(request.get("student_no"));
        String classCode = normalizeClassCode(request.get("class_code"));
        if (!validUserText(account, 64) || !StringUtils.hasText(password) || !validUserText(name, 64)
                || !validUserText(studentNo, 64) || !validUserText(classCode, 64)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (userRepository.findByAccount(account).isPresent()) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }

        Map<String, Object> classInfo = findEnabledClassByCode(classCode);
        User user = new User();
        user.setAccount(account);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name);
        user.setRole("STUDENT");
        user.setStatus("ENABLED");
        user.setPermissionScope("ALL");
        userRepository.save(user);

        jdbcTemplate.update("""
                INSERT INTO student_profile
                  (student_id, user_id, student_no, major_id, class_id, target_job_id, enrollment_year)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  user_id = VALUES(user_id),
                  student_no = VALUES(student_no),
                  major_id = VALUES(major_id),
                  class_id = VALUES(class_id)
                """, account, account, studentNo, stringValue(classInfo.get("major_id")),
                stringValue(classInfo.get("class_id")), null, null);

        return Map.of(
                "user_id", String.valueOf(user.getId()),
                "account", account,
                "name", name,
                "role", "STUDENT",
                "status", "ENABLED",
                "student_no", studentNo,
                "class_id", stringValue(classInfo.get("class_id")),
                "class_code", classCode
        );
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

    @Transactional
    public Map<String, Object> updateMeProfile(User actor, Map<String, Object> request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String avatarUrl = optionalText(request.get("avatar_url"), 500);
        String nickname = optionalText(request.get("nickname"), 100);
        String gender = optionalText(request.get("gender"), 20);
        String location = optionalText(request.get("location"), 100);
        String school = optionalText(request.get("school"), 100);
        String motto = optionalText(request.get("motto"), 300);

        jdbcTemplate.update("""
                INSERT INTO user_profile_ext
                  (profile_id, user_id, avatar_url, nickname, gender, location, school, motto, updated_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE
                  avatar_url = VALUES(avatar_url),
                  nickname = VALUES(nickname),
                  gender = VALUES(gender),
                  location = VALUES(location),
                  school = VALUES(school),
                  motto = VALUES(motto),
                  updated_time = NOW()
                """, "profile-ext-" + UUID.randomUUID(), actor.getAccount(), avatarUrl, nickname,
                gender, location, school, motto);
        return meProfile(actor);
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

    private Map<String, Object> findEnabledClassByCode(String classCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT class_id, major_id, class_code
                FROM `class`
                WHERE class_code = ? AND status = '启用'
                LIMIT 1
                """, classCode);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> userResponse(User user) {
        return Map.of(
                "user_id", String.valueOf(user.getId()),
                "role", user.getRole(),
                "status", user.getStatus(),
                "permission_scope", user.getPermissionScope()
        );
    }

    private Map<String, Object> profileResponse(User actor, Map<String, Object> ext) {
        return Map.of(
                "user_id", String.valueOf(actor.getId()),
                "account", actor.getAccount(),
                "name", stringValue(actor.getName()),
                "role", actor.getRole(),
                "avatar_url", stringValue(ext.get("avatar_url")),
                "nickname", stringValue(ext.get("nickname")),
                "gender", stringValue(ext.get("gender")),
                "location", stringValue(ext.get("location")),
                "school", stringValue(ext.get("school")),
                "motto", stringValue(ext.get("motto"))
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

    private String optionalText(Object value, int maxLength) {
        String text = stringValue(value);
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return text;
    }

    private String normalizeClassCode(Object value) {
        return stringValue(value).toUpperCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
