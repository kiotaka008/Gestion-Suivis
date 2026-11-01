package com.gestionsuivis.backend.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Réponse de {@code POST /api/ai/generate-report} ({@code GenerateReportResponse} de FastAPI).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RapportIaReponse(
        String title,
        String content,
        Map<String, String> sections,
        UsageIa usage,
        @JsonProperty("model") String modele) {
}
