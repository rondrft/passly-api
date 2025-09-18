package com.ron.passly.repository;

import com.ron.passly.dto.AuthUser;
import com.ron.passly.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    @Query("SELECT new com.ron.passly.dto.AuthUser(u.id, u.email, u.password, u.roles, u.firstName, u.lastName) " +
            "FROM User u WHERE u.email = :email")
    Optional<AuthUser> findAuthDataByEmail(@Param("email") String email);
}
