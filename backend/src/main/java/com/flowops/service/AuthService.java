package com.flowops.service;

import com.flowops.dto.auth.LoginRequest;
import com.flowops.dto.auth.LoginResponse;
import com.flowops.dto.auth.UserSummary;
import com.flowops.entity.User;
import com.flowops.exception.InvalidCredentialsException;
import com.flowops.repository.UserRepository;
import com.flowops.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndActiveTrue(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("E-mail ou senha invalidos"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("E-mail ou senha invalidos");
        }

        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getCompany().getId(),
                user.getRole().name()
        );

        UserSummary summary = new UserSummary(
                user.getUuid(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCompany().getId(),
                user.getCompany().getName()
        );

        return LoginResponse.of(token, summary);
    }
}
