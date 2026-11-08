package com.gestionsuivis.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Vérifie les jetons d'identité « Se connecter avec Google » (Google Identity Services).
 * <p>
 * Le frontend obtient un jeton d'identité (JWT signé par Google) et l'envoie au backend,
 * qui vérifie la signature (clés publiques de Google), l'émetteur, l'audience (notre
 * identifiant client), l'expiration et que l'email est vérifié par Google.
 * <p>
 * Configuration : variable {@value #VAR_CLIENT_ID} (identifiant client OAuth « Application
 * Web » de la Google Cloud Console). Absente : la connexion Google est désactivée.
 */
@Slf4j
@Component
public class VerificateurGoogle {

    public static final String VAR_CLIENT_ID = "GOOGLE_CLIENT_ID";
    static final String URL_CLES = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> EMETTEURS = Set.of("accounts.google.com", "https://accounts.google.com");

    /** Identité Google vérifiée. */
    public record IdentiteGoogle(String email, String nom) {
    }

    private final String clientId;
    private final JwtDecoder decodeur;

    @Autowired
    public VerificateurGoogle(Environment environnement) {
        String valeur = environnement.getProperty(VAR_CLIENT_ID);
        this.clientId = valeur == null || valeur.isBlank() ? null : valeur.trim();
        this.decodeur = clientId == null ? null : creerDecodeur(clientId);
        log.info("Connexion Google {}", clientId == null ? "désactivée (" + VAR_CLIENT_ID + " absent)" : "activée");
    }

    /** Décodeur fourni (tests). */
    public VerificateurGoogle(String clientId, JwtDecoder decodeur) {
        this.clientId = clientId;
        this.decodeur = decodeur;
    }

    public boolean estActive() {
        return clientId != null;
    }

    public Optional<String> clientId() {
        return Optional.ofNullable(clientId);
    }

    /**
     * @return l'identité si le jeton est valide et l'email vérifié, vide sinon
     * @throws IllegalStateException si la connexion Google n'est pas configurée
     */
    public Optional<IdentiteGoogle> verifier(String jetonIdentite) {
        if (!estActive()) {
            throw new IllegalStateException("Connexion Google non configurée.");
        }
        try {
            Jwt jwt = decodeur.decode(jetonIdentite);
            String email = jwt.getClaimAsString("email");
            Object verifie = jwt.getClaims().get("email_verified");
            boolean emailVerifie = Boolean.TRUE.equals(verifie) || "true".equals(String.valueOf(verifie));
            if (email == null || email.isBlank() || !emailVerifie) {
                return Optional.empty();
            }
            String nom = jwt.getClaimAsString("name");
            return Optional.of(new IdentiteGoogle(email, nom == null || nom.isBlank() ? email : nom));
        } catch (JwtException | IllegalArgumentException e) {
            log.info("Jeton Google refusé : {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static JwtDecoder creerDecodeur(String clientId) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withJwkSetUri(URL_CLES).build();
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), validateurEmetteurEtAudience(clientId)));
        return decodeur;
    }

    static OAuth2TokenValidator<Jwt> validateurEmetteurEtAudience(String clientId) {
        return jwt -> {
            String emetteur = jwt.getClaimAsString("iss");
            if (!EMETTEURS.contains(emetteur)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Émetteur invalide.", null));
            }
            if (jwt.getAudience() == null || !jwt.getAudience().contains(clientId)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience invalide.", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }
}
