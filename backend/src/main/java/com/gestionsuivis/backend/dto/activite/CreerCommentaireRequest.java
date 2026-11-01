package com.gestionsuivis.backend.dto.activite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps de {@code POST /api/activities/{id}/comments}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreerCommentaireRequest(
        @NotBlank(message = "Le commentaire ne peut pas être vide.")
        @Size(max = 5000, message = "Le commentaire ne doit pas dépasser 5000 caractères.")
        String content) {
}
