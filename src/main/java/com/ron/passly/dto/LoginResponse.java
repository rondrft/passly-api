package com.ron.passly.dto;

import com.ron.passly.model.Roles;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LoginResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String token;
    private List<Roles> roles;
}
