package com.gestionsuivis.backend.entity;

import com.gestionsuivis.backend.dto.ia.RoleMessageIa;

import java.util.Locale;

/**
 * Rôle d'un message IA ↔ type PostgreSQL {@code ai_message_role} ({@code user | assistant}).
 * Les messages {@code system} ne sont jamais enregistrés.
 */
public class ConvertisseurRoleMessageIa extends ConvertisseurEnumPg<RoleMessageIa> {

    public ConvertisseurRoleMessageIa() {
        super(RoleMessageIa.class, role -> role.name().toLowerCase(Locale.ROOT));
    }
}
