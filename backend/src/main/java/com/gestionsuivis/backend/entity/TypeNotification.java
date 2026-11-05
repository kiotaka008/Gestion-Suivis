package com.gestionsuivis.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types de notification : type PostgreSQL {@code notification_type}
 * ({@code project | activity | comment | deadline | ai | user}), valeurs identiques au frontend.
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

    public static class Convertisseur extends ConvertisseurEnumPg<TypeNotification> {
        public Convertisseur() {
            super(TypeNotification.class, TypeNotification::json);
        }
    }
}
