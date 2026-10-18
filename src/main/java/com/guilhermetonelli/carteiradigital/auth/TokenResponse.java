package com.guilhermetonelli.carteiradigital.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
