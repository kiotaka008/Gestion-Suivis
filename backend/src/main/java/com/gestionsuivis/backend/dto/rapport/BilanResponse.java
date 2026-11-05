package com.gestionsuivis.backend.dto.rapport;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Bilan calculé par le backend (par employé, projet ou global) sur une période.
 * <ul>
 *   <li>{@code completionRate} : activités terminées / activités de la période (%) ;</li>
 *   <li>{@code overdueRate} : activités en retard / activités de la période (%) ;</li>
 *   <li>{@code averageCompletionDays} : durée moyenne entre la création et la dernière
 *       modification des activités terminées (approximation de la durée de réalisation).</li>
 * </ul>
 * Les champs graphiques reprennent les formats des données fictives de {@code ReportsPage}.
 */
public record BilanResponse(
        Perimetre scope,
        Periode period,
        long totalActivities,
        long completedActivities,
        long inProgressActivities,
        long todoActivities,
        long overdueActivities,
        double completionRate,
        double overdueRate,
        Double averageCompletionDays,
        long totalProjects,
        long overdueProjects,
        GraphiqueSeries monthlyEvolution,
        Repartition statusDistribution,
        Repartition priorityDistribution,
        List<ResumeProjet> projectsSummary) {

    /** Périmètre du bilan : {@code global}, {@code user} ou {@code project}. */
    public record Perimetre(String type, UUID id, String name) {
    }

    /** Période du bilan, bornes incluses. */
    public record Periode(String code, LocalDate from, LocalDate to) {
    }
}
