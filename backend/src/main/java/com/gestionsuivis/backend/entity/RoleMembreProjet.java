package com.gestionsuivis.backend.entity;

/**
 * Rôle d'un membre dans un projet : type PostgreSQL {@code project_member_role}
 * ({@code owner | member}). Le chef de projet ({@code projects.owner_id}) est membre
 * avec le rôle {@code owner}.
 */
public enum RoleMembreProjet {

    PROPRIETAIRE("owner"),
    MEMBRE("member");

    private final String valeur;

    RoleMembreProjet(String valeur) {
        this.valeur = valeur;
    }

    public String valeur() {
        return valeur;
    }

    public static class Convertisseur extends ConvertisseurEnumPg<RoleMembreProjet> {
        public Convertisseur() {
            super(RoleMembreProjet.class, RoleMembreProjet::valeur);
        }
    }
}
