package com.vegeai.backend.modules.user.service;

import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserDataInitializerTest {

    @Test
    void existingUserUpdatedPasswordIsNotOverwrittenOnStartup() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "encoded:" + inv.getArgument(0));

        User existingMember = User.builder()
                .username("member")
                .email("member@vegeai.com")
                .passwordHash("user-updated-bcrypt-hash")
                .fullName("Trần Mai Anh")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .phoneNumber("0908123456")
                .heightCm(165.0)
                .weightKg(54.0)
                .dietaryPreferences("VEGAN")
                .build();

        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("member@vegeai.com")).thenReturn(Optional.of(existingMember));

        UserDataInitializer initializer = new UserDataInitializer(userRepository, passwordEncoder);
        initializer.run();

        assertThat(existingMember.getPasswordHash()).isEqualTo("user-updated-bcrypt-hash");
        verify(userRepository, never()).save(existingMember);
    }
}
