package com.gestionsuivis.backend.dto.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Corps de {@code POST /api/ai/chat}. Sans {@code conversationId}, une nouvelle
 * conversation est créée. La limite de 8000 caractères est celle du filtre
 * anti-injection du service IA.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageChatRequest(
        UUID conversationId,

        @NotBlank(message = "Le message ne peut pas être vide.")
        @Size(max = 8000, message = "Le message ne doit pas dépasser 8000 caractères.")
        String content) {
}
