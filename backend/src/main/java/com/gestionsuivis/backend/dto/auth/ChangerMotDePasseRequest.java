package com.gestionsuivis.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/auth/change-password}, champs de {@code SecuritySection}
 * du frontend (la confirmation reste vérifiée côté frontend).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangerMotDePasseRequest(
        @NotBlank(message = "Le mot de passe actuel est obligatoire.")
        @Size(max = 100, message = "Le mot de passe ne doit pas dépasser 100 caractères.")
        String currentPassword,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire.")
        @Size(min = 8, max = 100, message = "Le nouveau mot de passe doit contenir entre 8 et 100 caractères.")
        String newPassword) {

    @Override
    public String toString() {
        return "ChangerMotDePasseRequest[currentPassword=***, newPassword=***]";
    }
}
