package com.ron.passly.service;

import com.ron.passly.dto.AuthUser;
import com.ron.passly.dto.LoginRequest;
import com.ron.passly.dto.LoginResponse;
import com.ron.passly.dto.RegisterRequest;
import com.ron.passly.exception.InvalidCredentialsException;
import com.ron.passly.model.Roles;
import com.ron.passly.model.User;
import com.ron.passly.model.UserEncryptionKey;
import com.ron.passly.security.JwtService;
import com.ron.passly.security.RiskAssessmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService  {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RiskAssessmentService riskAssessmentService;
    private final EncryptionService encryptionService;

    @Transactional
    public LoginResponse register(RegisterRequest registerRequest) {

        //Convert DTO to Entity
        User user = new User();
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        // Default role
        user.setRoles(List.of(Roles.USER));

        String dek = encryptionService.generateRandomKey();
        String salt = encryptionService.generateSalt();
        String kek = encryptionService.deriveKeyFromPassword(registerRequest.getPassword(), salt);
        String encryptedDek = encryptionService.encrypt(dek, kek);

        UserEncryptionKey userEncryptionKey = UserEncryptionKey.builder()
                .encryptedKey(encryptedDek)
                .salt(salt)
                .build();

        user.setEncryptionKey(userEncryptionKey);

        //Call UserService
        User savedUser = userService.createUser(user);

        //Create Token
        String token = jwtService.generateToken(savedUser);

        return LoginResponse.builder()
                .id(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .token(token)
                .build();
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {

        String clientId = getClientId(httpRequest);

        if (request.getEmail() == null || request.getEmail().trim().isEmpty() ||
                request.getPassword() == null || request.getPassword().trim().isEmpty()) {

            riskAssessmentService.recordFailedAttempt(clientId);
            log.warn("Login failed: Empty credentials from IP: {}", clientId);
            throw new InvalidCredentialsException();
        }

        try {
            // Find User
            AuthUser authUser = userService.findAuthDataByEmail(request.getEmail()).orElse(null);
            if (authUser == null) {

                riskAssessmentService.recordFailedAttempt(clientId);
                log.warn("Login failed: User not found - Email: {} from IP: {}",
                        request.getEmail(), clientId);
                throw new InvalidCredentialsException();
            }

            // Verify password
            if (!passwordEncoder.matches(request.getPassword(), authUser.getPassword())) {
                riskAssessmentService.recordFailedAttempt(clientId);

                log.warn("Login failed: Wrong password for user: {} from IP: {}",
                        request.getEmail(), clientId);
                throw new InvalidCredentialsException();
            }

            riskAssessmentService.recordSuccessfulAttempt(clientId);
            log.info("Login successful for user: {} from IP: {}", request.getEmail(), clientId);

            String token = jwtService.generateToken(authUser);

            return LoginResponse.builder()
                    .id(authUser.getId())
                    .firstName(authUser.getFirstName())
                    .lastName(authUser.getLastName())
                    .email(authUser.getEmail())
                    .token(token)
                    .build();

        } catch (InvalidCredentialsException ex) {

            throw ex;
        } catch (Exception ex) {
            riskAssessmentService.recordFailedAttempt(clientId);
            log.error("Unexpected error during login for IP: {}", clientId, ex);
            throw new InvalidCredentialsException();
        }
    }

    // OBTAIN IP
    private String getClientId(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
