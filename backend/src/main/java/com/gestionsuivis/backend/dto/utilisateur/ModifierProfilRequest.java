package com.gestionsuivis.backend.dto.utilisateur;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Modification de son propre profil ({@code PATCH /api/users/me}), champs de
 * {@code ProfileSection} du frontend. Le rôle n'est pas modifiable ici ; un champ
 * {@code bio} éventuel est ignoré (absent du schéma).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModifierProfilRequest(
        // Absent ou null : non modifié. Fourni : au moins un caractère autre qu'un espace
        // (même règle que trim() du service et que la contrainte BTRIM(name) <> '' de la base).
        @Pattern(regexp = "(?s).*[^\\x00-\\x20].*", message = "Le nom ne peut pas être vide.")
        @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères.")
        String name,

        @Email(message = "L'email n'est pas valide.")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères.")
        String email,

        @Size(max = 200000, message = "La photo de profil est trop volumineuse (200 000 caractères maximum).")
        String avatarUrl) {
}
