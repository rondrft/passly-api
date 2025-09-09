package com.ron.passly.service;

import com.ron.passly.model.User;
import com.ron.passly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User createUser(User user) {
        userRepository.findByEmail(user.getEmail())
                .ifPresent(u -> {throw new RuntimeException("User with email " + user.getEmail() + " already exists"); });
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User deleteUser(User user) {
        userRepository.delete(user);
        return user;
    }

}
