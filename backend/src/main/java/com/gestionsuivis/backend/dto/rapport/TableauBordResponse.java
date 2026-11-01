package com.gestionsuivis.backend.dto.rapport;

import com.gestionsuivis.backend.dto.activite.ActiviteResponse;
import com.gestionsuivis.backend.dto.projet.ProjetResponse;

import java.util.List;

/**
 * Statistiques du tableau de bord ({@code DashboardPage}), sur le périmètre de
 * l'utilisateur connecté. Les graphiques reprennent les formats de
 * {@code src/mocks/dashboardStats.ts}.
 */
public record TableauBordResponse(
        long totalProjects,
        long activeProjects,
        long completedProjects,
        long overdueProjects,
        long totalActivities,
        long completedActivities,
        long overdueActivities,
        GraphiqueSeries monthlyEvolution,
        Repartition statusDistribution,
        GraphiqueSeries tasksPerWeek,
        List<ProjetResponse> upcomingDeadlines,
        List<ActiviteResponse> recentActivities) {
}
