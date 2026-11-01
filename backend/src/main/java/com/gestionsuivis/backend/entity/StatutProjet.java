package com.gestionsuivis.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Statuts de projet du cahier des charges.
 * <p>
 * Correspondance JSON : {@code active}, {@code completed} et {@code on_hold} existent
 * déjà dans le frontend ; {@code cancelled} est une valeur proposée pour ANNULE.
 * La valeur frontend {@code archived} n'a pas d'équivalent et est refusée.
 */
public enum StatutProjet {

    EN_COURS("active"),
    TERMINE("completed"),
    EN_ATTENTE("on_hold"),
    ANNULE("cancelled");

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

    /** Un projet clos n'est plus considéré comme en retard. */
    public boolean estClos() {
        return this == TERMINE || this == ANNULE;
    }
}
