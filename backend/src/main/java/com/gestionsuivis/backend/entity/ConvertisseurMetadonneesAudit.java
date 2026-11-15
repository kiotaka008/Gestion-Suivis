package com.gestionsuivis.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Colonne JSONB {@code audit_logs.metadata} ↔ {@code Map<String, Object>}.
 * Le texte JSON est converti en {@code jsonb} à l'écriture ({@code ?::jsonb}) et relu en texte
 * ({@code metadata::text}), comme les types ENUM PostgreSQL du projet.
 * Une valeur illisible est ignorée (journal en lecture seule : l'historique reste consultable).
 */
@Slf4j
@Converter
public class ConvertisseurMetadonneesAudit implements AttributeConverter<Map<String, Object>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> valeur) {
        return valeur == null || valeur.isEmpty() ? null : JSON.writeValueAsString(valeur);
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> lu = JSON.readValue(json, TYPE);
            return lu == null ? Map.of() : lu;
        } catch (JacksonException e) {
            log.warn("Métadonnées d'audit illisibles ignorées");
            return Map.of();
        }
    }
}
