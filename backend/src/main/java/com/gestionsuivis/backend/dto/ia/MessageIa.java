package com.gestionsuivis.backend.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message d'une conversation, au format {@code ChatMessage} de FastAPI :
 * {@code { "role": "user", "content": "..." }}.
 *
 * @param role    émetteur du message
 * @param contenu texte du message
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageIa(
        RoleMessageIa role,
        @JsonProperty("content") String contenu) {

    public static MessageIa utilisateur(String contenu) {
        return new MessageIa(RoleMessageIa.USER, contenu);
    }

    public static MessageIa assistant(String contenu) {
        return new MessageIa(RoleMessageIa.ASSISTANT, contenu);
    }
}
