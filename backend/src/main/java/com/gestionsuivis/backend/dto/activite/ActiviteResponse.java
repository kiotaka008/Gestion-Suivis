package com.gestionsuivis.backend.dto.activite;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutActivite;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Activité renvoyée par l'API, au format {@code Activity} du frontend
 * ({@code src/types/models.ts}), complété par le nom du projet, la date de
 * début, les heures prévues et réelles et l'indicateur de retard.
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
        BigDecimal plannedHours,
        BigDecimal actualHours,
        @JsonProperty("isOverdue") boolean isOverdue,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {
}
