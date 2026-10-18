package com.guilhermetonelli.carteiradigital.user;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email, UUID walletId) {
}
