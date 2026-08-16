package com.gymlet.web;

import com.gymlet.service.AuthService;
import com.gymlet.web.dto.AuthDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@RequestBody AuthDtos.AuthRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@RequestBody AuthDtos.AuthRequest req) {
        return authService.login(req);
    }

    /** One-time claim of the pre-auth legacy account (keeps existing data). */
    @PostMapping("/claim")
    public AuthDtos.AuthResponse claim(@RequestBody AuthDtos.AuthRequest req) {
        return authService.claim(req);
    }

    @GetMapping("/status")
    public AuthDtos.AuthStatusDto status() {
        return authService.status();
    }

    @GetMapping("/me")
    public AuthDtos.MeDto me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(bearer(authorization));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(bearer(authorization));
        return Map.of("ok", "true");
    }

    private String bearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }
}
