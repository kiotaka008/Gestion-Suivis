package com.gestionsuivis.backend.dto.suivi;

import com.gestionsuivis.backend.entity.StatutProjet;

import java.util.List;
import java.util.UUID;

/**
 * Indicateur de risque d'un projet, calculé par le backend.
 * {@code riskLevel} utilise les niveaux du service IA ({@code low | medium | high | critical}).
 * {@code expectedProgress} est l'avancement attendu au prorata du temps écoulé entre
 * {@code startDate} et {@code dueDate} ({@code null} si ces dates manquent).
 */
public record IndicateurRisqueResponse(
        UUID projectId,
        String projectName,
        StatutProjet status,
        int progress,
        Integer expectedProgress,
        long totalActivities,
        long overdueActivities,
        double overdueRate,
        Long daysRemaining,
        String riskLevel,
        List<String> reasons) {
}
