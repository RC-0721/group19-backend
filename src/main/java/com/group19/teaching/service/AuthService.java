package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.dto.LoginRequest;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.domain.vo.LoginResponse;
import com.group19.teaching.repository.UserRepository;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final String ENABLED = "ENABLED";
    private static final int EXPIRES_IN_SECONDS = 7200;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, User> sessions = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    public LoginResponse login(LoginRequest request) {
        String account = request.account().trim();
        String role = request.role().trim().toUpperCase(Locale.ROOT);
        User user = userRepository.findByAccount(account)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAILED));

        if (!ENABLED.equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!role.equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }

        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        // Menu values are placeholders for the first frontend contract.
        return new LoginResponse(token, String.valueOf(user.getId()), user.getName(), user.getRole(), List.of());
    }

    public User requireRole(String token, String... roles) {
        User user = requireUser(token);
        boolean allowed = Arrays.stream(roles).anyMatch(role -> role.equalsIgnoreCase(user.getRole()));
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    public User requireUser(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        String normalizedToken = normalizeToken(token);
        User user = sessions.get(normalizedToken);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        User current = userRepository.findById(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAILED));
        if (!ENABLED.equalsIgnoreCase(current.getStatus())) {
            sessions.remove(normalizedToken);
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        sessions.put(normalizedToken, current);
        return current;
    }

    public Map<String, Object> currentUser(String token) {
        User user = requireUser(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", String.valueOf(user.getId()));
        result.put("account", user.getAccount());
        result.put("name", user.getName());
        result.put("role", user.getRole());
        result.put("status", user.getStatus());
        result.put("permission_scope", user.getPermissionScope());
        result.put("menus", List.of());
        result.put("profile", profile(user));
        return result;
    }

    public Map<String, Object> refresh(String token) {
        User user = requireUser(token);
        User current = userRepository.findById(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAILED));
        if (!ENABLED.equalsIgnoreCase(current.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }

        String newToken = UUID.randomUUID().toString();
        sessions.remove(normalizeToken(token));
        sessions.put(newToken, current);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", newToken);
        result.put("expires_in", EXPIRES_IN_SECONDS);
        result.put("user_id", String.valueOf(current.getId()));
        result.put("role", current.getRole());
        return result;
    }

    public void logout(String token) {
        if (!StringUtils.hasText(token) || sessions.remove(normalizeToken(token)) == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
    }

    private Map<String, Object> profile(User user) {
        if (!"STUDENT".equalsIgnoreCase(user.getRole())) {
            return Map.of("account", user.getAccount(), "role", user.getRole());
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT student_id, student_no, major_id, class_id, target_job_id, enrollment_year
                FROM student_profile
                WHERE user_id = ?
                LIMIT 1
                """, user.getAccount());
        return rows.isEmpty() ? Map.of("student_id", user.getAccount()) : rows.get(0);
    }

    private String normalizeToken(String token) {
        String value = token.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }
}
