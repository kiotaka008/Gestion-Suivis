package com.gestionsuivis.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Priorités du cahier des charges, valeurs JSON identiques au frontend
 * ({@code low | medium | high | urgent}).
 */
public enum Priorite {

    BASSE("low"),
    MOYENNE("medium"),
    HAUTE("high"),
    URGENTE("urgent");

    private final String json;

    Priorite(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static Priorite depuisJson(String valeur) {
        return ValeursJson.depuis(Priorite.class, values(), Priorite::json, valeur);
    }
}
