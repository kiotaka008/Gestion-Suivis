package com.gestionsuivis.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Force la génération du jeton CSRF à chaque requête afin que le cookie
 * {@code XSRF-TOKEN} soit toujours présent pour le frontend, y compris dès la
 * première réponse (par exemple le 401 de {@code GET /api/auth/me}).
 * Axios lit ce cookie et renvoie automatiquement l'en-tête {@code X-XSRF-TOKEN}.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        CsrfToken jeton = (CsrfToken) requete.getAttribute(CsrfToken.class.getName());
        if (jeton != null) {
            jeton.getToken();
        }
        chaine.doFilter(requete, reponse);
    }
}
