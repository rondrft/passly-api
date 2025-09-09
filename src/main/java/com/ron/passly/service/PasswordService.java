package com.ron.passly.service;

import com.ron.passly.model.Password;
import com.ron.passly.model.User;
import com.ron.passly.repository.PasswordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final PasswordRepository passwordRepository;

    public Password createPassword(User user, Password password) {
        password.setUser(user);
        return passwordRepository.save(password);
    }

    public List<Password> findPasswordsByUserId(UUID userId) {
        return passwordRepository.findByUserId(userId);
    }

    public Password updatePassword(UUID userId, String name, String newEncryptedValue) {
        Password password = passwordRepository.findByNameAndUser_Id(name, userId)
                .orElseThrow(() -> new RuntimeException("Password not found for " + name));

        password.setEncryptedValue(newEncryptedValue);

        return passwordRepository.save(password);
    }

    public void deletePassword(UUID id) {
        passwordRepository.deleteById(id);
    }

}
