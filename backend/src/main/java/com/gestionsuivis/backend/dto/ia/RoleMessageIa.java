package com.gestionsuivis.backend.dto.ia;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rôle d'un message échangé avec le service IA.
 * Valeurs JSON identiques à {@code MessageRole} de FastAPI ({@code ai/app/models/schemas.py}).
 */
public enum RoleMessageIa {

    @JsonProperty("system")
    SYSTEM,

    @JsonProperty("user")
    USER,

    @JsonProperty("assistant")
    ASSISTANT
}
