package com.gestionsuivis.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Validation de l'émetteur et de l'audience des jetons d'identité Google. */
class VerificateurGoogleTest {

    private final OAuth2TokenValidator<Jwt> validateur = VerificateurGoogle.validateurEmetteurEtAudience("mon-client");

    @Test
    void emetteurGoogleEtBonneAudience_acceptes() {
        assertThat(validateur.validate(jwt("https://accounts.google.com", "mon-client")).hasErrors()).isFalse();
        assertThat(validateur.validate(jwt("accounts.google.com", "mon-client")).hasErrors()).isFalse();
    }

    @Test
    void autreEmetteurOuAutreAudience_refuses() {
        assertThat(validateur.validate(jwt("https://pirate.example", "mon-client")).hasErrors()).isTrue();
        assertThat(validateur.validate(jwt("https://accounts.google.com", "autre-client")).hasErrors()).isTrue();
    }

    @Test
    void nonConfigure_estInactif() {
        assertThat(new VerificateurGoogle(null, null).estActive()).isFalse();
        assertThat(new VerificateurGoogle("id", null).clientId()).contains("id");
    }

    private static Jwt jwt(String emetteur, String audience) {
        return Jwt.withTokenValue("t").header("alg", "RS256").issuer(emetteur).audience(List.of(audience))
                .claim("email", "a@b.c").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
