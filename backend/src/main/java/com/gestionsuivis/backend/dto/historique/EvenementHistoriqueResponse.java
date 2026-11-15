package com.gestionsuivis.backend.dto.historique;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Une action de l'historique ({@code audit_logs}).
 *
 * @param action      code technique ({@code project.updated}, {@code activity.created}...)
 * @param description phrase lisible en français (« Projet modifié : statut (Archivé) »)
 * @param author      auteur de l'action ; {@code null} pour une action automatique
 * @param fields      noms (API) des champs modifiés, vide sinon
 * @param status      nouveau statut quand l'action l'a changé, sinon {@code null}
 */
public record EvenementHistoriqueResponse(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        String description,
        Auteur author,
        List<String> fields,
        String status,
        Instant createdAt) {

    /** Auteur : identifiant et nom seulement (jamais l'email). */
    public record Auteur(UUID id, String name) {
    }
}
