package com.gestionsuivis.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authentifie le navigateur à partir du cookie HttpOnly {@code access_token}.
 * <p>
 * Placé après le filtre CSRF : les requêtes authentifiées par cookie restent donc
 * soumises à la vérification du jeton {@code X-XSRF-TOKEN}. Les requêtes portant un
 * en-tête {@code Authorization} sont laissées au filtre « resource server ».
 * Un cookie invalide, expiré ou d'un compte désactivé laisse la requête anonyme :
 * les routes protégées répondent alors 401.
 */
@Slf4j
public class CookieJwtFilter extends OncePerRequestFilter {

    private final JwtDecoder decodeur;
    private final ConvertisseurJwtUtilisateur convertisseur;
    private final SecurityContextHolderStrategy contextes = SecurityContextHolder.getContextHolderStrategy();

    public CookieJwtFilter(JwtDecoder decodeur, ConvertisseurJwtUtilisateur convertisseur) {
        this.decodeur = decodeur;
        this.convertisseur = convertisseur;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        String jeton = JetonResolver.jetonCookie(requete);
        if (jeton != null && requete.getHeader(HttpHeaders.AUTHORIZATION) == null
                && contextes.getContext().getAuthentication() == null) {
            try {
                AbstractAuthenticationToken authentification = convertisseur.convert(decodeur.decode(jeton));
                SecurityContext contexte = contextes.createEmptyContext();
                contexte.setAuthentication(authentification);
                contextes.setContext(contexte);
            } catch (JwtException | AuthenticationException e) {
                log.debug("Cookie d'authentification refusé : {}", e.getClass().getSimpleName());
                contextes.clearContext();
            }
        }
        chaine.doFilter(requete, reponse);
    }
}
