package com.gestionsuivis.backend.dto.utilisateur;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gestionsuivis.backend.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Création d'un utilisateur par un administrateur ({@code POST /api/users}).
 * Correspond à {@code CreateUserModal} du frontend, avec en plus le mot de passe
 * initial (obligatoire) et le département (facultatif).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreerUtilisateurRequest(
        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères.")
        String name,

        @NotBlank(message = "L'email est obligatoire.")
        @Email(message = "L'email n'est pas valide.")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères.")
        String email,

        @NotNull(message = "Le rôle est obligatoire.")
        Role role,

        @NotBlank(message = "Le mot de passe initial est obligatoire.")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères.")
        String password,

        UUID departmentId,

        @JsonProperty("isActive") Boolean isActive) {
}
