package com.ron.passly.repository;

import com.ron.passly.model.UserEncryptionKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserEncryptionKeyRepository extends JpaRepository<UserEncryptionKey, UUID> {

    Optional<UserEncryptionKey> findByUserId(UUID userId);

}
