package com.gestionsuivis.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/auth/register} (champs de {@code RegisterPage}).
 * L'inscription libre est fermée par défaut ({@code AUTH_REGISTRATION_ENABLED}) ;
 * un compte inscrit reçoit toujours le rôle EMPLOYE.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InscriptionRequest(
        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères.")
        String name,

        @NotBlank(message = "L'email est obligatoire.")
        @Email(message = "L'email n'est pas valide.")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères.")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères.")
        String password) {

    @Override
    public String toString() {
        return "InscriptionRequest[name=" + name + ", email=" + email + ", password=***]";
    }
}
