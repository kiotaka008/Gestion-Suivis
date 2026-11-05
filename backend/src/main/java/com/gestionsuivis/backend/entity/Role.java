package com.gestionsuivis.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Rôles applicatifs : type PostgreSQL {@code user_role} ({@code admin | manager | member}),
 * valeurs identiques au frontend React ({@code src/types/models.ts}).
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

    public static class Convertisseur extends ConvertisseurEnumPg<Role> {
        public Convertisseur() {
            super(Role.class, Role::json);
        }
    }
}
