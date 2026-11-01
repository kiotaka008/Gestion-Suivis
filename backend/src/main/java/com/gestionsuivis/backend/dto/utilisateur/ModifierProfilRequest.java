package com.gestionsuivis.backend.dto.utilisateur;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Modification de son propre profil ({@code PATCH /api/users/me}), champs de
 * {@code ProfileSection} du frontend. Le rôle et le département ne sont pas modifiables ici.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModifierProfilRequest(
        @Size(min = 1, max = 150, message = "Le nom doit contenir entre 1 et 150 caractères.")
        String name,

        @Email(message = "L'email n'est pas valide.")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères.")
        String email,

        @Size(max = 1000, message = "La bio ne doit pas dépasser 1000 caractères.")
        String bio,

        @Size(max = 500, message = "L'adresse de l'avatar ne doit pas dépasser 500 caractères.")
        String avatarUrl) {
}
