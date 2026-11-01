package com.gestionsuivis.backend.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Consommation de jetons renvoyée par FastAPI ({@code TokenUsage}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageIa(
        @JsonProperty("prompt_tokens") int jetonsPrompt,
        @JsonProperty("completion_tokens") int jetonsReponse,
        @JsonProperty("total_tokens") int jetonsTotal) {
}
