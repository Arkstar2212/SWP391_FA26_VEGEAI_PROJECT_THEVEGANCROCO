package com.vegeai.backend.modules.user.entity;

/**
 * Application roles. Spring Security will prefix these with ROLE_ automatically
 * when used via hasRole(). We store them without the prefix in DB.
 */
public enum UserRole {
    MEMBER,
    ADMIN
}
