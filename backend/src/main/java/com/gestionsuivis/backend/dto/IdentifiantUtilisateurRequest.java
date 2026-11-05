package com.gestionsuivis.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corps {@code { "userId": "..." }} utilisé pour ajouter un membre à un projet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentifiantUtilisateurRequest(
        @NotNull(message = "L'identifiant de l'utilisateur est obligatoire.")
        UUID userId) {
}
