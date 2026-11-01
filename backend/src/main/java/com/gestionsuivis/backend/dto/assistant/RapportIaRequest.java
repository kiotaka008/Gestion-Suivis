package com.gestionsuivis.backend.dto.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Corps de {@code POST /api/ai/reports}. {@code reportType} reprend les valeurs du
 * service IA ({@code weekly | monthly | project | productivity}) ; {@code projectId}
 * est obligatoire pour {@code project}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RapportIaRequest(
        @NotNull(message = "Le type de rapport est obligatoire.")
        @Pattern(regexp = "weekly|monthly|project|productivity",
                message = "Le type de rapport doit valoir weekly, monthly, project ou productivity.")
        String reportType,

        UUID projectId) {
}
