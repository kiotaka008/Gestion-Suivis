package com.gestionsuivis.backend.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réponse de {@code POST /api/ai/analyze-risks} ({@code AnalyzeRisksResponse} de FastAPI).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyseRisquesIaReponse(
        @JsonProperty("overall_risk") String risqueGlobal,
        List<RisqueIa> risks,
        String summary,
        UsageIa usage,
        @JsonProperty("model") String modele) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RisqueIa(String level, String title, String description, String recommendation) {
    }
}
