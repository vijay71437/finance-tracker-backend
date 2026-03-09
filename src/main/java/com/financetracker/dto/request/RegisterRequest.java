package com.financetracker.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

// ---- AUTH ----

@Data
public class RegisterRequest {
    @NotBlank @Email(message = "Invalid email format")
    private String email;

    @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$",
            message = "Password must contain uppercase, lowercase, digit, and special character")
    private String password;

    @NotBlank @Size(min = 2, max = 100)
    private String fullName;

    @Size(max = 20)
    private String phone;

    @Size(min = 3, max = 3)
    private String currency = "USD";

    private String timezone = "UTC";
}