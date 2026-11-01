package com.gestionsuivis.backend.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * Cookie HttpOnly qui transporte le JWT. Il n'est pas lisible en JavaScript et
 * n'est envoyé que vers {@code /api}.
 */
public final class CookieAuthentification {

    public static final String NOM = "access_token";
    public static final String CHEMIN = "/api";

    private CookieAuthentification() {
    }

    public static ResponseCookie creer(String jeton, Duration duree, boolean securise) {
        return ResponseCookie.from(NOM, jeton)
                .httpOnly(true)
                .secure(securise)
                .sameSite("Lax")
                .path(CHEMIN)
                .maxAge(duree)
                .build();
    }

    public static ResponseCookie supprimer(boolean securise) {
        return ResponseCookie.from(NOM, "")
                .httpOnly(true)
                .secure(securise)
                .sameSite("Lax")
                .path(CHEMIN)
                .maxAge(Duration.ZERO)
                .build();
    }
}
