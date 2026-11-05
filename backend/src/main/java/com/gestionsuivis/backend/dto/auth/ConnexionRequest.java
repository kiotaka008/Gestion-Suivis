package com.gestionsuivis.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/auth/login}, identique à {@code LoginPayload} du
 * frontend ({@code src/services/authService.ts}).
 * <p>
 * {@code organization} (slug de l'organisation, facultatif) n'est nécessaire que si le
 * même email existe dans plusieurs organisations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnexionRequest(
        @NotBlank(message = "L'email est obligatoire.")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères.")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(max = 100, message = "Le mot de passe ne doit pas dépasser 100 caractères.")
        String password,

        @Size(max = 100, message = "L'identifiant d'organisation ne doit pas dépasser 100 caractères.")
        String organization) {

    public ConnexionRequest(String email, String password) {
        this(email, password, null);
    }

    @Override
    public String toString() {
        return "ConnexionRequest[email=" + email + ", password=***, organization=" + organization + "]";
    }
}
