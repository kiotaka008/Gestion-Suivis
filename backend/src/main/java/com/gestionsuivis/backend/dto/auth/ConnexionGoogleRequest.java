package com.gestionsuivis.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/auth/google} : jeton d'identité renvoyé par Google Identity
 * Services ({@code credential}) et, si l'email existe dans plusieurs organisations, le
 * slug de l'organisation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnexionGoogleRequest(
        @NotBlank(message = "Le jeton Google est obligatoire.")
        @Size(max = 8192, message = "Le jeton Google est trop long.")
        String credential,

        @Size(max = 100, message = "L'identifiant d'organisation ne doit pas dépasser 100 caractères.")
        String organization) {

    @Override
    public String toString() {
        return "ConnexionGoogleRequest[credential=***, organization=" + organization + "]";
    }
}
