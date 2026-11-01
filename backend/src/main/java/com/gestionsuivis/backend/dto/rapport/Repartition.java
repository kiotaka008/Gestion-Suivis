package com.gestionsuivis.backend.dto.rapport;

import java.util.List;

/**
 * Répartition simple, au format des données fictives du frontend
 * ({@code { labels: [...], data: [...] }}).
 */
public record Repartition(List<String> labels, List<Long> data) {
}
