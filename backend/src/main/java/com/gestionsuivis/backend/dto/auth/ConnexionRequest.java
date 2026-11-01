package com.gestionsuivis.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/auth/login}, identique à {@code LoginPayload} du
 * frontend ({@code src/services/authService.ts}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnexionRequest(
        @NotBlank(message = "L'email est obligatoire.")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères.")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(max = 100, message = "Le mot de passe ne doit pas dépasser 100 caractères.")
        String password) {

    @Override
    public String toString() {
        return "ConnexionRequest[email=" + email + ", password=***]";
    }
}
