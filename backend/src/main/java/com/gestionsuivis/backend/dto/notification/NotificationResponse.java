package com.gestionsuivis.backend.dto.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gestionsuivis.backend.entity.TypeNotification;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification, au format {@code Notification} du frontend
 * ({@code id, type, title, message, isRead, createdAt}), complété par les liens
 * facultatifs vers le projet ou l'activité concernés.
 */
public record NotificationResponse(
        UUID id,
        TypeNotification type,
        String title,
        String message,
        @JsonProperty("isRead") boolean isRead,
        Instant createdAt,
        UUID projectId,
        UUID activityId) {
}
