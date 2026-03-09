package com.financetracker.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Standard API envelope for all responses.
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Transaction created",
 *   "data": { ... },
 *   "timestamp": "2024-03-15T10:30:00Z"
 * }
 * </pre>
 *
 * Error responses additionally include an {@code error} field with details.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final ErrorDetail error;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder().success(true).message(message).data(data).build();
    }

    public static <T> ApiResponse<T> error(String message, ErrorDetail errorDetail) {
        return ApiResponse.<T>builder().success(false).message(message).error(errorDetail).build();
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private final String code;
        private final String field;
        private final Object details;
    }
}