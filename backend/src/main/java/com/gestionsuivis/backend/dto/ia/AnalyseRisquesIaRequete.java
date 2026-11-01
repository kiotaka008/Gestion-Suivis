package com.gestionsuivis.backend.dto.ia;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Requête envoyée à {@code POST /api/ai/analyze-risks}, au format
 * {@code AnalyzeRisksRequest} de FastAPI. Le prompt du service IA lit les clés
 * {@code title}, {@code status} et {@code due_date} de chaque activité.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyseRisquesIaRequete(
        @JsonProperty("project_name") String nomProjet,
        @JsonProperty("project_description") String descriptionProjet,
        List<Map<String, Object>> activities,
        String language) {
}
