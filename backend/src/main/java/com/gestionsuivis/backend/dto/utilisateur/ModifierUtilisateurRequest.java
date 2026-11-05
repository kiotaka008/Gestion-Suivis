package com.gestionsuivis.backend.dto.utilisateur;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestionsuivis.backend.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Modification partielle d'un utilisateur par un administrateur
 * ({@code PATCH /api/users/{id}}). Un champ absent ou {@code null} n'est pas modifié.
 * L'activation passe par les routes dédiées {@code /activate} et {@code /deactivate}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModifierUtilisateurRequest(
        @Size(min = 1, max = 255, message = "Le nom doit contenir entre 1 et 255 caractères.")
        String name,

        @Email(message = "L'email n'est pas valide.")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères.")
        String email,

        Role role,

        @Size(max = 2000, message = "L'adresse de l'avatar ne doit pas dépasser 2000 caractères.")
        String avatarUrl) {
}
