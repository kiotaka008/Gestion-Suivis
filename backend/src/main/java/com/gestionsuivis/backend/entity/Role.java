package com.gestionsuivis.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Rôles du cahier des charges. Valeur JSON alignée sur le frontend React
 * ({@code src/types/models.ts} : {@code admin | manager | member}).
 */
public enum Role {

    ADMIN("admin"),
    MANAGER("manager"),
    EMPLOYE("member");

    private final String json;

    Role(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static Role depuisJson(String valeur) {
        return ValeursJson.depuis(Role.class, values(), Role::json, valeur);
    }
}
