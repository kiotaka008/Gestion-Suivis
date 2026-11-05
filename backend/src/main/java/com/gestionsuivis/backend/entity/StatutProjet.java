package com.gestionsuivis.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Statuts de projet : type PostgreSQL {@code project_status}
 * ({@code active | on_hold | completed | archived}), valeurs identiques au frontend.
 */
public enum StatutProjet {

    EN_COURS("active"),
    EN_ATTENTE("on_hold"),
    TERMINE("completed"),
    ARCHIVE("archived");

    private final String json;

    StatutProjet(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static StatutProjet depuisJson(String valeur) {
        return ValeursJson.depuis(StatutProjet.class, values(), StatutProjet::json, valeur);
    }

    /** Un projet clos (terminé ou archivé) n'est plus considéré comme en retard. */
    public boolean estClos() {
        return this == TERMINE || this == ARCHIVE;
    }

    public static class Convertisseur extends ConvertisseurEnumPg<StatutProjet> {
        public Convertisseur() {
            super(StatutProjet.class, StatutProjet::json);
        }
    }
}
