package com.gestionsuivis.backend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Lecture du JWT dans le cookie HttpOnly {@value CookieAuthentification#NOM}.
 * <p>
 * Volontairement séparé du résolveur du « resource server » Spring (qui ne lit que
 * l'en-tête {@code Authorization: Bearer}) : Spring dispense de CSRF toute requête
 * dont ce résolveur trouve un jeton. Lire le cookie à cet endroit désactiverait donc
 * la protection CSRF pour le navigateur.
 * <p>
 * Le cookie est ignoré sur les routes publiques d'authentification : un cookie
 * expiré ne doit pas empêcher de se reconnecter ou de se déconnecter.
 */
public final class JetonResolver {

    static final Set<String> ROUTES_SANS_JETON = Set.of(
            "/api/auth/login", "/api/auth/logout", "/api/auth/register", "/api/auth/csrf");

    private JetonResolver() {
    }

    /** Jeton du cookie, ou {@code null} (absent, vide ou route publique d'authentification). */
    public static String jetonCookie(HttpServletRequest requete) {
        if (ROUTES_SANS_JETON.contains(cheminSansContexte(requete))) {
            return null;
        }
        Cookie[] cookies = requete.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CookieAuthentification.NOM.equals(cookie.getName())
                    && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String cheminSansContexte(HttpServletRequest requete) {
        String uri = requete.getRequestURI();
        String contexte = requete.getContextPath();
        return contexte != null && !contexte.isEmpty() && uri.startsWith(contexte) ? uri.substring(contexte.length()) : uri;
    }
}
