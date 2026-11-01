package com.gestionsuivis.backend.dto.assistant;

import java.util.UUID;

/** Réponse de {@code POST /api/ai/chat} : la conversation et la réponse de l'assistant. */
public record MessageChatResponse(UUID conversationId, MessageAssistantResponse message) {
}
