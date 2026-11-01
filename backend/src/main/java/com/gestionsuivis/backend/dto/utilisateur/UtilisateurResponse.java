package com.gestionsuivis.backend.dto.utilisateur;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gestionsuivis.backend.entity.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Utilisateur renvoyé par l'API, au format {@code User} du frontend
 * ({@code src/types/models.ts}), complété par le département et la bio.
 * Ne contient jamais le mot de passe ni son hash.
 */
public record UtilisateurResponse(
        UUID id,
        String email,
        String name,
        Role role,
        String avatarUrl,
        @JsonProperty("isActive") boolean isActive,
        Instant createdAt,
        UUID departmentId,
        String departmentName,
        String bio) {
}
