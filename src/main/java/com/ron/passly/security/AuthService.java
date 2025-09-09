package com.ron.passly.security;

import com.ron.passly.dto.LoginRequest;
import com.ron.passly.dto.LoginResponse;
import com.ron.passly.dto.RegisterRequest;
import com.ron.passly.exception.CustomException;
import com.ron.passly.exception.InvalidCredentialsException;
import com.ron.passly.exception.UserNotFoundException;
import com.ron.passly.model.User;
import com.ron.passly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@RequiredArgsConstructor
public class AuthService  {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse register(RegisterRequest registerRequest) {

        //Convert DTO to Entity
        User user = new User();
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

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

    public LoginResponse login(LoginRequest request) {

        //Find by Email
        User user = userService.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException(request.getEmail()));

        //Check Password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);

        return LoginResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .token(token)
                .build();

    }

}
