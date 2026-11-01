package com.gestionsuivis.backend.dto.activite;

import java.time.Instant;
import java.util.UUID;

/** Commentaire d'une activité (nouveau type, absent du frontend actuel). */
public record CommentaireResponse(
        UUID id,
        UUID activityId,
        UUID authorId,
        String authorName,
        String content,
        Instant createdAt) {
}
