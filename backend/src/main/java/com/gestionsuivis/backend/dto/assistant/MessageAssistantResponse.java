package com.gestionsuivis.backend.dto.assistant;

import com.gestionsuivis.backend.dto.ia.RoleMessageIa;

import java.time.Instant;
import java.util.UUID;

/**
 * Message d'une conversation, au format {@code AIMessageType} du frontend
 * ({@code id, role: 'user' | 'assistant', content, createdAt}).
 */
public record MessageAssistantResponse(UUID id, RoleMessageIa role, String content, Instant createdAt) {
}
