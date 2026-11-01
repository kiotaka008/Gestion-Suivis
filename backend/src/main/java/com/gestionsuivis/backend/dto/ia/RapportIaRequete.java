package com.gestionsuivis.backend.dto.ia;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Requête envoyée à {@code POST /api/ai/generate-report}, au format
 * {@code GenerateReportRequest} de FastAPI ({@code report_type}, {@code data}, {@code language}).
 */
public record RapportIaRequete(
        @JsonProperty("report_type") String typeRapport,
        Map<String, Object> data,
        String language) {
}
