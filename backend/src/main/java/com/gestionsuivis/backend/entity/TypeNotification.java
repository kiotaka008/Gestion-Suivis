package com.gestionsuivis.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types de notification, valeurs JSON identiques au frontend
 * ({@code Notification.type} dans {@code src/types/models.ts}).
 * Les affectations et fins d'activité utilisent {@code activity}, les échéances {@code deadline}.
 */
public enum TypeNotification {

    PROJET("project"),
    ACTIVITE("activity"),
    COMMENTAIRE("comment"),
    ECHEANCE("deadline"),
    IA("ai"),
    UTILISATEUR("user");

    private final String json;

    TypeNotification(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static TypeNotification depuisJson(String valeur) {
        return ValeursJson.depuis(TypeNotification.class, values(), TypeNotification::json, valeur);
    }
}
