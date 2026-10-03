package com.vegeai.backend.modules.user.repository;

import com.vegeai.backend.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    /** Login by username OR email (case-insensitive) */
    default Optional<User> findByUsernameOrEmailIgnoreCase(String identifier) {
        Optional<User> byUsername = findByUsernameIgnoreCase(identifier);
        return byUsername.isPresent() ? byUsername : findByEmailIgnoreCase(identifier);
    }

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByPhoneNumber(String phoneNumber);
}
