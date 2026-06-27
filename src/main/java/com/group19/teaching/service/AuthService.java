package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.dto.LoginRequest;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.domain.vo.LoginResponse;
import com.group19.teaching.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String ENABLED = "ENABLED";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

        // Temporary token until request authentication is introduced.
        String token = UUID.randomUUID().toString();
        // Menu values are placeholders for the first frontend contract.
        return new LoginResponse(token, String.valueOf(user.getId()), user.getName(), user.getRole(), List.of());
    }
}
