package com.gestionsuivis.backend.dto.assistant;

import java.time.Instant;
import java.util.UUID;

/** Conversation IA de l'utilisateur connecté. */
public record ConversationResponse(
        UUID id,
        String title,
        String preview,
        long messageCount,
        Instant createdAt,
        Instant updatedAt) {
}
