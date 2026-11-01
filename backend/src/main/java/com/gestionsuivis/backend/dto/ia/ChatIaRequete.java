package com.gestionsuivis.backend.dto.ia;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Requête envoyée à {@code POST /api/ai/chat}, au format {@code ChatRequest} de FastAPI :
 * <pre>
 * {
 *   "messages": [ { "role": "user", "content": "..." } ],
 *   "user_id": "...",
 *   "conversation_id": "...",
 *   "temperature": 0.7
 * }
 * </pre>
 * Les champs absents ({@code null}) ne sont pas envoyés. Le champ {@code stream},
 * ignoré par {@code /api/ai/chat}, n'est jamais envoyé.
 * <p>
 * Les contraintes de FastAPI (1 à 50 messages, 32 000 caractères par message,
 * identifiants de 64 caractères, température entre 0 et 2) sont vérifiées par
 * le client avant l'envoi.
 *
 * @param messages                 historique complet, du plus ancien au plus récent
 * @param identifiantUtilisateur   identifiant de l'utilisateur connecté (traçabilité)
 * @param identifiantConversation  identifiant de la conversation côté backend
 * @param temperature              créativité du modèle, {@code null} pour la valeur par défaut
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatIaRequete(
        List<MessageIa> messages,
        @JsonProperty("user_id") String identifiantUtilisateur,
        @JsonProperty("conversation_id") String identifiantConversation,
        Double temperature) {

    public ChatIaRequete {
        messages = messages == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public static ChatIaRequete de(List<MessageIa> messages, String identifiantUtilisateur,
                                   String identifiantConversation) {
        return new ChatIaRequete(messages, identifiantUtilisateur, identifiantConversation, null);
    }
}
