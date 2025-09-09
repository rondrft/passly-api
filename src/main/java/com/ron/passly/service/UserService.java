package com.ron.passly.service;

import com.ron.passly.model.User;
import com.ron.passly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User createUser(User user) {
        userRepository.findByEmail(user.getEmail())
                .ifPresent(u -> {throw new RuntimeException("User with email " + user.getEmail() + " already exists"); });
        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User deleteUser(User user) {
        userRepository.delete(user);
        return user;
    }

}
