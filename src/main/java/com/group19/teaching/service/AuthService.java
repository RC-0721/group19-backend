package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.dto.LoginRequest;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.domain.vo.LoginResponse;
import com.group19.teaching.repository.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final String ENABLED = "ENABLED";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Map<String, User> sessions = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        User user = sessions.get(normalizeToken(token));
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        boolean allowed = Arrays.stream(roles).anyMatch(role -> role.equalsIgnoreCase(user.getRole()));
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    public void logout(String token) {
        if (!StringUtils.hasText(token) || sessions.remove(normalizeToken(token)) == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
    }

    private String normalizeToken(String token) {
        String value = token.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }
}
