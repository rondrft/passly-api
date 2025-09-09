package com.ron.passly.repository;

import com.ron.passly.model.Password;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordRepository extends JpaRepository<Password, UUID> {
    List<Password> findByUserId(UUID userId);

    Optional<Password> findByNameAndUserId(UUID userId, String name);
}
