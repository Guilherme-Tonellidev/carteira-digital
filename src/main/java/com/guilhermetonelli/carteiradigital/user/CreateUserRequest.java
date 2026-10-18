package com.guilhermetonelli.carteiradigital.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 255) String email,
        // 72 é o limite de bytes do BCrypt
        @NotBlank @Size(min = 8, max = 72) String password) {
}
