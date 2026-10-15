package com.vegeai.backend.common.exception;

import com.vegeai.backend.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler — translates all exceptions into the ApiResponse envelope.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ErrorCode.IMAGE_TOO_LARGE.getCode(), "Ảnh vượt quá giới hạn 5 MB."));
    }

    // ── AppException (typed application errors) ──────────────────────────────
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex, HttpServletRequest request) {
        ErrorCode ec = ex.getErrorCode();
        log.warn("[{}] AppException: code={}, path={}, detail={}",
                ec.name(), ec.getCode(), request.getRequestURI(), ex.getDetail());
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(ApiResponse.error(ec.getCode(), ex.getDetail()));
    }

    // ── Bean Validation (@Valid failures) ────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        String topMessage = errors.values().stream().findFirst()
                .orElse(ErrorCode.VALIDATION_ERROR.getDefaultMessage());

        log.debug("[VALIDATION] path={}, fields={}", request.getRequestURI(), errors.keySet());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(topMessage)
                        .data(errors)
                        .timestamp(java.time.Instant.now().toString())
                        .build());
    }

    // ── Misinput: Malformed JSON or unreadable request body ─────────────────
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("[MISINPUT_MALFORMED_JSON] path={}, error={}", request.getRequestURI(), ex.getMessage());
        String msg = "Malformed JSON request body or unreadable payload.";
        Throwable cause = ex.getCause();
        if (cause instanceof com.fasterxml.jackson.core.JsonParseException) {
            msg = "Malformed JSON syntax: please verify commas, quotes, and brackets.";
        } else if (cause instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException mie) {
            msg = "Invalid data type or missing required value for field: " + mie.getPathReference();
        } else if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            msg = "Invalid request payload format: " + cause.getMessage().split("\n")[0];
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.MALFORMED_JSON.getCode(), msg));
    }

    // ── Misinput: HTTP method not supported (e.g. GET instead of POST) ───────
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        String msg = String.format("HTTP method '%s' is not supported for '%s'. Supported: %s",
                ex.getMethod(), request.getRequestURI(), ex.getSupportedHttpMethods());
        log.warn("[METHOD_NOT_ALLOWED] path={}, method={}", request.getRequestURI(), ex.getMethod());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED.getCode(), msg));
    }

    // ── Misinput: Unsupported media type (e.g. text/plain instead of JSON) ──
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        log.warn("[UNSUPPORTED_MEDIA_TYPE] path={}, mediaType={}", request.getRequestURI(), ex.getContentType());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode(),
                        "Unsupported media type. Content-Type must be 'application/json'."));
    }

    // ── Misinput: Missing required query param ───────────────────────────────
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex) {

        String msg = String.format("Required query parameter '%s' is missing.", ex.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), msg));
    }

    // ── Constraint Violation (@Validated on params) ─────────────────────────
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            jakarta.validation.ConstraintViolationException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(cv ->
                errors.put(cv.getPropertyPath().toString(), cv.getMessage()));
        String topMsg = errors.values().stream().findFirst().orElse("Constraint violation.");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(topMsg)
                        .data(errors)
                        .timestamp(java.time.Instant.now().toString())
                        .build());
    }

    // ── Database constraint violation (unique key collision, etc.) ───────────
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("[DATA_INTEGRITY] path={}, error={}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.DATA_INTEGRITY_VIOLATION.getCode(),
                        "Data conflict or unique constraint violation."));
    }

    // ── Endpoint not found (404) ─────────────────────────────────────────────
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                        "Endpoint not found: " + ex.getResourcePath()));
    }

    // ── Spring Security: access denied ───────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("[FORBIDDEN] path={}", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage()));
    }

    // ── Spring Security: bad credentials ─────────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        ErrorCode.INVALID_CREDENTIALS.getCode(),
                        ErrorCode.INVALID_CREDENTIALS.getDefaultMessage()));
    }

    // ── Type mismatch (path variable, query param) ───────────────────────────
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("Invalid value '%s' for parameter '%s'.", ex.getValue(), ex.getName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), msg));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("[UNHANDLED] path={}, exception={}, message={}",
                request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()));
    }
}
