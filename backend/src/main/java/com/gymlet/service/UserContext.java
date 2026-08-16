package com.gymlet.service;

import com.gymlet.domain.AppUser;
import com.gymlet.web.UnauthorizedException;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user for the current request.
 * The AuthFilter reads the session token and stores the user here per-request;
 * the backend never trusts a userId coming from the frontend.
 */
@Component
public class UserContext {

    private static final ThreadLocal<AppUser> CURRENT = new ThreadLocal<>();

    public static void set(AppUser user) {
        CURRENT.set(user);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public AppUser getUser() {
        AppUser user = CURRENT.get();
        if (user == null) {
            throw new UnauthorizedException("Not logged in");
        }
        return user;
    }

    public Long getUserId() {
        return getUser().getId();
    }
}
