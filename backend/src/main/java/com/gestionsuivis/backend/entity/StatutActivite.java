package com.gestionsuivis.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Statuts d'activité : type PostgreSQL {@code activity_status}
 * ({@code todo | in_progress | done | blocked}), valeurs identiques au frontend.
 * <p>
 * Le retard n'est pas un statut : il est calculé (échéance dépassée et activité non terminée).
 */
public enum StatutActivite {

    A_FAIRE("todo"),
    EN_COURS("in_progress"),
    TERMINE("done"),
    BLOQUE("blocked");

    private final String json;

    StatutActivite(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static StatutActivite depuisJson(String valeur) {
        return ValeursJson.depuis(StatutActivite.class, values(), StatutActivite::json, valeur);
    }

    public static class Convertisseur extends ConvertisseurEnumPg<StatutActivite> {
        public Convertisseur() {
            super(StatutActivite.class, StatutActivite::json);
        }
    }
}
