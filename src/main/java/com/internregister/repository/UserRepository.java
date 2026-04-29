package com.internregister.repository;

import com.internregister.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    java.util.List<User> findByUsername(String username);

    java.util.List<User> findByEmail(String email);

    java.util.List<User> findByUsernameIn(java.util.List<String> usernames);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    java.util.List<User> findByRole(User.Role role);
}
