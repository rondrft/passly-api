package com.ron.passly.dto;

import com.ron.passly.model.Roles;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthUser {
    private UUID id;
    private String email;
    private String password;
    private List<Roles> roles;
    private String firstName;
    private String lastName;
}
