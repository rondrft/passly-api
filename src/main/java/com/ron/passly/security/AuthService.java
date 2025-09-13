package com.ron.passly.security;

import com.ron.passly.dto.LoginRequest;
import com.ron.passly.dto.LoginResponse;
import com.ron.passly.dto.RegisterRequest;
import com.ron.passly.exception.InvalidCredentialsException;
import com.ron.passly.model.Roles;
import com.ron.passly.model.User;
import com.ron.passly.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService  {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RiskAssessmentService riskAssessmentService;

    public LoginResponse register(RegisterRequest registerRequest) {

        //Convert DTO to Entity
        User user = new User();
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        // Asignar rol por defecto
        user.setRoles(List.of(Roles.USER));

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
            User user = userService.findByEmail(request.getEmail()).orElse(null);
            if (user == null) {

                riskAssessmentService.recordFailedAttempt(clientId);
                log.warn("Login failed: User not found - Email: {} from IP: {}",
                        request.getEmail(), clientId);
                throw new InvalidCredentialsException();
            }

            // Verify password
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                riskAssessmentService.recordFailedAttempt(clientId);

                log.warn("Login failed: Wrong password for user: {} from IP: {}",
                        request.getEmail(), clientId);
                throw new InvalidCredentialsException();
            }

            riskAssessmentService.recordSuccessfulAttempt(clientId);
            log.info("Login successful for user: {} from IP: {}", request.getEmail(), clientId);

            String token = jwtService.generateToken(user);

            return LoginResponse.builder()
                    .id(user.getId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
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
