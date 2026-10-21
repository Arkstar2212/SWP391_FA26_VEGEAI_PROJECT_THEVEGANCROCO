package com.vegeai.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Centralized error codes following the auth-service specification.
 * Numeric codes mirror the auth-service document for cross-service consistency.
 */
@Getter
public enum ErrorCode {

    // ── Generic ──────────────────────────────────────────────────────────────
    INTERNAL_SERVER_ERROR(1000, "An unexpected error occurred.", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR(1001, "Request validation failed.", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(1002, "Invalid request.", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON(1003, "Malformed JSON request body.", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(1004, "HTTP method not allowed for this endpoint.", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(1005, "Content-Type not supported. Please use application/json.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    DATA_INTEGRITY_VIOLATION(1006, "Database constraint violation.", HttpStatus.CONFLICT),

    // ── Authentication / Token ────────────────────────────────────────────────
    UNAUTHORIZED(1100, "Access denied. Authentication required.", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(1101, "Invalid username/email or password.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1201, "Access token has expired.", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(1202, "Access token is invalid.", HttpStatus.UNAUTHORIZED),
    TOKEN_BLACKLISTED(1203, "Access token has been revoked.", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_NOT_FOUND(1207, "Refresh token not found.", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REVOKED(1208, "Refresh token has been revoked.", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED(1209, "Refresh token has expired.", HttpStatus.UNAUTHORIZED),

    // ── Account Status ────────────────────────────────────────────────────────
    ACCOUNT_PENDING_VERIFICATION(1200, "Account is pending email verification.", HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED(1205, "Account is locked. Please contact support.", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(1204, "Account has been disabled.", HttpStatus.FORBIDDEN),

    // ── Registration ──────────────────────────────────────────────────────────
    USERNAME_ALREADY_EXISTS(1300, "Username is already taken.", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(1301, "Email is already registered.", HttpStatus.CONFLICT),
    PHONE_ALREADY_EXISTS(1302, "Phone number is already in use.", HttpStatus.CONFLICT),
    PASSWORD_MISMATCH(1303, "Passwords do not match.", HttpStatus.BAD_REQUEST),

    // ── OTP ───────────────────────────────────────────────────────────────────
    OTP_INVALID(1210, "Invalid OTP code.", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(1211, "OTP has expired.", HttpStatus.BAD_REQUEST),
    OTP_ALREADY_USED(1212, "OTP has already been used.", HttpStatus.BAD_REQUEST),
    OTP_RESEND_TOO_SOON(1213, "Please wait before requesting a new OTP.", HttpStatus.TOO_MANY_REQUESTS),

    // ── Rate Limiting ─────────────────────────────────────────────────────────
    TOO_MANY_REQUESTS(1400, "Too many requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS),
    LOGIN_ATTEMPTS_EXCEEDED(1401, "Too many failed login attempts. Try again in 15 minutes.", HttpStatus.TOO_MANY_REQUESTS),
    GUEST_AI_QUOTA_EXCEEDED(1402, "Guest trial limit reached (3 requests per 24h). Please register or log in to continue.", HttpStatus.TOO_MANY_REQUESTS),

    // ── User / Resource ───────────────────────────────────────────────────────
    USER_NOT_FOUND(1500, "User not found.", HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(1501, "Resource not found.", HttpStatus.NOT_FOUND),
    FORBIDDEN(1600, "You do not have permission to perform this action.", HttpStatus.FORBIDDEN),
    OLD_PASSWORD_INCORRECT(1304, "Current password is incorrect.", HttpStatus.BAD_REQUEST),
    SAME_PASSWORD_ERROR(1305, "New password cannot be the same as current password.", HttpStatus.BAD_REQUEST),
    ADMIN_IMMUNITY_VIOLATION(1601, "Admin immunity: Cannot modify another administrator account.", HttpStatus.FORBIDDEN),
    CANNOT_MODIFY_SELF_STATUS(1602, "You cannot modify your own administrative status or role.", HttpStatus.FORBIDDEN),

    // ── Fridge & Inventory ──────────────────────────────────────────────────
    FRIDGE_ITEM_NOT_FOUND(1801, "Fridge item not found or does not belong to you.", HttpStatus.NOT_FOUND),

    // ── Nutrition & Health Profile ───────────────────────────────────────────
    NUTRITION_PROFILE_NOT_FOUND(2001, "Nutrition profile not found. Please set up your nutrition profile first.", HttpStatus.NOT_FOUND),
    NUTRITION_AGE_OUT_OF_RANGE(2002, "Age must be between 13 and 100 to calculate nutrition targets.", HttpStatus.BAD_REQUEST),
    PROFILE_INCOMPLETE(2004, "Vui lòng thiết lập sở thích ăn chay và hồ sơ dinh dưỡng trước khi thực hiện tính năng này.", HttpStatus.FORBIDDEN),

    // ── Recipes & Tags ────────────────────────────────────────────────────────
    RECIPE_NOT_FOUND(2101, "Recipe not found.", HttpStatus.NOT_FOUND),
    TAG_NOT_FOUND(2102, "Tag not found.", HttpStatus.NOT_FOUND),
    TAG_ALREADY_EXISTS(2103, "Tag with this code already exists.", HttpStatus.CONFLICT),
    COOKBOOK_RECIPE_NOT_FOUND(2104, "Cookbook recipe not found or does not belong to you.", HttpStatus.NOT_FOUND),
    BLOG_POST_NOT_FOUND(2201, "Blog post not found.", HttpStatus.NOT_FOUND),
    BLOG_STATE_CONFLICT(2202, "This action is not allowed for the current blog post status.", HttpStatus.CONFLICT),
    QUESTION_NOT_FOUND(2301, "Question not found.", HttpStatus.NOT_FOUND),
    VIDEO_NOT_FOUND(2401, "Video not found.", HttpStatus.NOT_FOUND),
    VIDEO_COMMENT_NOT_FOUND(2402, "Video comment not found.", HttpStatus.NOT_FOUND),
    COMMUNITY_CONFLICT(2501, "Content changed. Reload before updating or deleting.", HttpStatus.CONFLICT),
    PLACES_NOT_CONFIGURED(2601, "Places provider is not configured.", HttpStatus.SERVICE_UNAVAILABLE),
    PLACES_PROVIDER_UNAVAILABLE(2602, "OpenStreetMap place search is temporarily unavailable.", HttpStatus.SERVICE_UNAVAILABLE),


    // ── Email ─────────────────────────────────────────────────────────────────
    EMAIL_SEND_FAILED(1901, "Failed to send email. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE),

    // ── Image & Vision AI ────────────────────────────────────────────────────
    IMAGE_EMPTY(1701, "Image file must not be empty.", HttpStatus.BAD_REQUEST),
    IMAGE_TOO_LARGE(1702, "Image size exceeds maximum limit of 5MB.", HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_IMAGE_FORMAT(1703, "Unsupported image format. Allowed formats: JPG, PNG, WEBP.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    AI_PROCESSING_ERROR(1704, "Failed to analyze image with AI model.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_IMAGE(1705, "File không phải ảnh JPG, PNG hoặc WEBP hợp lệ.", HttpStatus.BAD_REQUEST),
    AI_NOT_CONFIGURED(1706, "AI chưa được cấu hình. Vui lòng liên hệ quản trị viên.", HttpStatus.SERVICE_UNAVAILABLE),
    AI_AUTH_FAILED(1707, "Gemini từ chối API key hoặc quyền truy cập. Vui lòng liên hệ quản trị viên.", HttpStatus.SERVICE_UNAVAILABLE),
    AI_QUOTA_EXCEEDED(1708, "Gemini đã hết hạn mức hoặc đang giới hạn tốc độ. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
    AI_UNAVAILABLE(1709, "Không thể kết nối Gemini hoặc model không khả dụng. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
    AI_RESPONSE_INVALID(1710, "Gemini chưa trả về kết quả hợp lệ. Vui lòng thử lại.", HttpStatus.BAD_GATEWAY),
    PLACES_UNAVAILABLE(2601, "Dịch vụ bản đồ đang bận hoặc không kết nối được. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
    PLACES_INVALID_RESPONSE(2602, "Dịch vụ bản đồ trả dữ liệu không hợp lệ. Vui lòng thử lại.", HttpStatus.BAD_GATEWAY);

    private final int code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
