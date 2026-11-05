package com.gestionsuivis.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/auth/register} (champs de {@code RegisterPage}).
 * L'inscription libre est fermée par défaut ({@code AUTH_REGISTRATION_ENABLED}) ;
 * un compte inscrit reçoit toujours le rôle {@code member}.
 * <p>
 * {@code organization} (slug) est obligatoire s'il existe plusieurs organisations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InscriptionRequest(
        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères.")
        String name,

        @NotBlank(message = "L'email est obligatoire.")
        @Email(message = "L'email n'est pas valide.")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères.")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères.")
        String password,

        @Size(max = 100, message = "L'identifiant d'organisation ne doit pas dépasser 100 caractères.")
        String organization) {

    public InscriptionRequest(String name, String email, String password) {
        this(name, email, password, null);
    }

    @Override
    public String toString() {
        return "InscriptionRequest[name=" + name + ", email=" + email + ", password=***, organization="
                + organization + "]";
    }
}
