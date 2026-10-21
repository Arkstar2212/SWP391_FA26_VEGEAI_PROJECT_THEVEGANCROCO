package com.vegeai.backend.modules.user.repository;

import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

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

    long countByStatus(AccountStatus status);

    long countByRole(UserRole role);

    // Used to deliver moderation-queue activity notices without exposing admin addresses.
    java.util.List<User> findAllByRoleAndStatus(UserRole role, AccountStatus status);

    long countByCreatedAtAfter(LocalDateTime dateTime);
}
