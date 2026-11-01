package com.gestionsuivis.backend.dto.rapport;

import java.util.List;

/**
 * Données de graphique multi-séries, au format des données fictives du frontend
 * ({@code { labels: [...], datasets: [{ label, data: [...] }] }}).
 */
public record GraphiqueSeries(List<String> labels, List<Serie> datasets) {

    public record Serie(String label, List<Long> data) {
    }
}
