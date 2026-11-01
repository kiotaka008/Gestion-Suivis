package com.gestionsuivis.backend.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Réponse de {@code POST /api/ai/chat}, au format {@code ChatResponse} de FastAPI :
 * <pre>
 * {
 *   "message": { "role": "assistant", "content": "..." },
 *   "conversation_id": "...",
 *   "usage": { "prompt_tokens": 103, "completion_tokens": 40, "total_tokens": 517 },
 *   "model": "gemini-...",
 *   "created_at": "2026-10-09T11:31:24"
 * }
 * </pre>
 * FastAPI produit {@code created_at} avec {@code datetime.utcnow()}, donc sans
 * fuseau horaire : la valeur est conservée telle quelle et interprétée en UTC
 * par {@link #horodatageUtc()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatIaReponse(
        MessageIa message,
        @JsonProperty("conversation_id") String identifiantConversation,
        UsageIa usage,
        @JsonProperty("model") String modele,
        @JsonProperty("created_at") String horodatage) {

    /**
     * Date de la réponse en UTC. Accepte une date sans fuseau (format actuel de
     * FastAPI) ou avec fuseau ; vide si la valeur est absente ou illisible.
     */
    public Optional<OffsetDateTime> horodatageUtc() {
        if (horodatage == null || horodatage.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(horodatage));
        } catch (DateTimeParseException sansFuseau) {
            try {
                return Optional.of(LocalDateTime.parse(horodatage).atOffset(ZoneOffset.UTC));
            } catch (DateTimeParseException illisible) {
                return Optional.empty();
            }
        }
    }
}
