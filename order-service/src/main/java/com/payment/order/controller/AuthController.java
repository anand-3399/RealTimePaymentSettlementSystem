package com.payment.order.controller;

import com.payment.order.dto.*;
import com.payment.order.entity.RefreshToken;
import com.payment.order.security.JwtUtils;
import com.payment.order.service.RefreshTokenService;
import com.payment.order.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/registeruser")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        userService.registerUser(registerRequest);
        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/loginuser")
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(userService.authenticateUser(loginRequest));
    }

    @PostMapping("/refreshtoken")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    // 1. Generate NEW Access Token
                    String token = jwtUtils.generateTokenFromUsername(user.getUsername());
                    
                    // 2. Generate NEW Refresh Token (Rotation)
                    // The createRefreshToken method internally deletes the old one
                    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getUsername());
                    
                    return ResponseEntity.ok(new TokenRefreshResponse(token, newRefreshToken.getToken()));
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    @PostMapping("/password-reset-request")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        String token = userService.createPasswordResetToken(request.getEmail());
        // In production, this token would be sent via email
        return ResponseEntity.ok("Password reset token generated: " + token);
    }

    @PostMapping("/password-reset-confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm request) {
        userService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Password has been reset successfully!");
    }
}
