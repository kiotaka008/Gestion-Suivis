package com.gestionsuivis.backend.dto.assistant;

import com.gestionsuivis.backend.dto.suivi.IndicateurRisqueResponse;

import java.util.List;
import java.util.UUID;

/**
 * Réponse de {@code POST /api/ai/projects/{id}/risk-analysis} : synthèse rédigée
 * par l'IA et indicateurs calculés par le backend.
 */
public record AnalyseRisquesResponse(
        UUID projectId,
        String overallRisk,
        List<Risque> risks,
        String summary,
        IndicateurRisqueResponse indicators,
        String model) {

    public record Risque(String level, String title, String description, String recommendation) {
    }
}
