package com.vegeai.backend.modules.user.entity;

/**
 * Account lifecycle status.
 * PENDING_VERIFICATION → (verify OTP) → ACTIVE
 * ACTIVE → (admin action) → LOCKED | DISABLED
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    LOCKED,
    DISABLED
}
