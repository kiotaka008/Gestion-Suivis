package com.gestionsuivis.backend.dto.projet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutProjet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Projet renvoyé par l'API, au format {@code Project} du frontend
 * ({@code src/types/models.ts}), complété par l'indicateur de retard.
 * {@code progress} est la colonne {@code projects.progress}, recalculée par le
 * backend à partir des activités (une activité terminée compte pour 100 %).
 */
public record ProjetResponse(
        UUID id,
        String name,
        String description,
        StatutProjet status,
        Priorite priority,
        int progress,
        UUID ownerId,
        UtilisateurResponse owner,
        List<UUID> memberIds,
        List<UtilisateurResponse> members,
        LocalDate startDate,
        LocalDate dueDate,
        @JsonProperty("isOverdue") boolean isOverdue,
        Instant createdAt,
        Instant updatedAt) {
}
