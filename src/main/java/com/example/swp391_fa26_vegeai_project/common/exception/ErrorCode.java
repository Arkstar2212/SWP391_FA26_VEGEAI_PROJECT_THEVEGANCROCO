package com.example.swp391_fa26_vegeai_project.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    USER_NOT_FOUND("USER_NOT_FOUND", "User does not exist", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("EMAIL_EXISTS", "Email already registered", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED_ACCESS("UNAUTHORIZED", "Access denied", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("NOT_FOUND", "Requested resource not found", HttpStatus.NOT_FOUND),
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed", HttpStatus.BAD_REQUEST),
    AI_SERVICE_UNAVAILABLE("AI_UNAVAILABLE", "AI service temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_SERVER_ERROR("INTERNAL_ERROR", "An unexpected server error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
