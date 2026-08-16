package com.gymlet.web.dto;

/** Request/response bodies for the lightweight username + PIN auth. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record AuthRequest(String username, String pin) {
    }

    public record AuthResponse(String token, String username, String name) {
    }

    public record MeDto(String username, String name) {
    }

    /** Tells the login screen whether a pre-auth legacy account is waiting to be claimed. */
    public record AuthStatusDto(String legacyUsername) {
    }
}
