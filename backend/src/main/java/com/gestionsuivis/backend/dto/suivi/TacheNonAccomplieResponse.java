package com.gestionsuivis.backend.dto.suivi;

import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutActivite;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tâche non accomplie, structurée pour le module IA ({@code /api/suivi/*}).
 * {@code reason} vaut {@code overdue} (échéance dépassée), {@code no_progress}
 * (aucune progression depuis {@code daysWithoutUpdate} jours) ou {@code not_started}.
 */
public record TacheNonAccomplieResponse(
        UUID id,
        String title,
        UUID projectId,
        String projectName,
        UUID assigneeId,
        String assigneeName,
        StatutActivite status,
        Priorite priority,
        int progress,
        LocalDate dueDate,
        Long daysOverdue,
        long daysWithoutUpdate,
        BigDecimal plannedHours,
        BigDecimal actualHours,
        String reason,
        Instant updatedAt) {
}
