package com.payment.order.service;

import com.payment.order.dto.*;
import com.payment.order.entity.PasswordResetToken;
import com.payment.order.entity.RefreshToken;
import com.payment.order.entity.User;
import com.payment.order.repository.PasswordResetTokenRepository;
import com.payment.order.repository.UserRepository;
import com.payment.order.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    public void registerUser(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Error: Username is already taken!");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Error: Email is already in use!");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(encoder.encode(request.getPassword()))
                .accountNumber(request.getAccountNumber())
                .build();

        userRepository.save(user);
        org.slf4j.LoggerFactory.getLogger(UserService.class).info("Audit: New user registered | username: {}", user.getUsername());
    }

    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(loginRequest.getUsername());

        org.slf4j.LoggerFactory.getLogger(UserService.class).info("Audit: User authenticated successfully | username: {}", loginRequest.getUsername());
        return new JwtResponse(jwt, refreshToken.getToken(), loginRequest.getUsername());
    }

    public String createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        passwordResetTokenRepository.findByUser(user).ifPresent(passwordResetTokenRepository::delete);

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .build();

        passwordResetTokenRepository.save(resetToken);
        return token;
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid password reset token"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new RuntimeException("Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);
    }
}
