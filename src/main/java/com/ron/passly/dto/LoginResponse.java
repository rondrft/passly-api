package com.ron.passly.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LoginResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String token;
}
