package com.example.swp391_fa26_vegeai_project.modules.user.entity;

import com.example.swp391_fa26_vegeai_project.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.ROLE_USER;

    private Double heightCm;
    private Double weightKg;
    private Double bmi;
    private Integer dailyCalorieTarget;
    private String dietaryPreferences; // VEGAN, VEGETARIAN, LACTO_OVO
    private String allergies; // Comma-separated list: PEANUTS, SOY, GLUTEN
}
