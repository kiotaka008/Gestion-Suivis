package com.gestionsuivis.backend.dto.assistant;

import java.util.Map;

/** Rapport rédigé par l'IA à partir des indicateurs calculés par le backend. */
public record RapportIaResponse(
        String reportType,
        String title,
        String content,
        Map<String, String> sections,
        String model) {
}
