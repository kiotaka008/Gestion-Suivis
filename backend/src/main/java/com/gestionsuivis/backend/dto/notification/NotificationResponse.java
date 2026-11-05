package com.gestionsuivis.backend.dto.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gestionsuivis.backend.entity.TypeNotification;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification, au format {@code Notification} du frontend
 * ({@code id, type, title, message, isRead, createdAt}).
 */
public record NotificationResponse(
        UUID id,
        TypeNotification type,
        String title,
        String message,
        @JsonProperty("isRead") boolean isRead,
        Instant createdAt) {
}
