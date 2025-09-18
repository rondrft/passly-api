package com.ron.passly.service;

import com.ron.passly.dto.AuthUser;
import com.ron.passly.exception.UserAlreadyExistsException;
import com.ron.passly.exception.UserNotFoundException;
import com.ron.passly.model.User;
import com.ron.passly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User createUser(User user) {
        userRepository.findByEmail(user.getEmail())
                .ifPresent(u -> { throw new UserAlreadyExistsException(user.getEmail()); });
        return userRepository.save(user);
    }
    @Cacheable("users")
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @CacheEvict("users")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Cacheable(value = "auth-cache", key = "#email.toLowerCase()")
    public Optional<AuthUser> findAuthDataByEmail(String email) {
        return userRepository.findAuthDataByEmail(email);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public void deleteUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        userRepository.delete(user);
    }

}
