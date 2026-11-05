package com.gestionsuivis.backend.dto.activite;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutActivite;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Activité renvoyée par l'API, au format {@code Activity} du frontend
 * ({@code src/types/models.ts}), complété par le nom du projet, la date de début et
 * l'indicateur de retard. {@code completedAt} est approximatif (dernière modification
 * d'une activité terminée : le schéma ne conserve pas de date d'achèvement).
 */
public record ActiviteResponse(
        UUID id,
        UUID projectId,
        String projectName,
        String title,
        String description,
        StatutActivite status,
        Priorite priority,
        int progress,
        UUID assigneeId,
        UtilisateurResponse assignee,
        LocalDate startDate,
        LocalDate dueDate,
        @JsonProperty("isOverdue") boolean isOverdue,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {
}
