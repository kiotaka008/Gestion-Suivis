package com.gestionsuivis.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Statuts d'activité du cahier des charges.
 * <p>
 * Correspondance JSON : {@code todo}, {@code in_progress} et {@code done} existent
 * déjà dans le frontend ; {@code overdue} est une valeur proposée pour EN_RETARD.
 * La valeur frontend {@code blocked} n'a pas d'équivalent et est refusée.
 */
public enum StatutActivite {

    A_FAIRE("todo"),
    EN_COURS("in_progress"),
    TERMINE("done"),
    EN_RETARD("overdue");

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
}
