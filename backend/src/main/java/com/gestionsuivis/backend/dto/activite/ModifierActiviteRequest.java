package com.gestionsuivis.backend.dto.activite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutActivite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code PATCH /api/activities/{id}} ({@code Partial<Activity>} du frontend).
 * Un champ absent ou {@code null} n'est pas modifié.
 * <p>
 * La personne affectée (sans droit de gestion) ne peut modifier que
 * {@code status} et {@code progress}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModifierActiviteRequest(
        @Size(min = 1, max = 255, message = "Le titre doit contenir entre 1 et 255 caractères.")
        String title,

        @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères.")
        String description,

        StatutActivite status,
        Priorite priority,
        UUID assigneeId,
        LocalDate startDate,
        LocalDate dueDate,

        @Min(value = 0, message = "L'avancement doit être compris entre 0 et 100.")
        @Max(value = 100, message = "L'avancement doit être compris entre 0 et 100.")
        Integer progress) {

    /** {@code true} si la requête ne touche que les champs autorisés à la personne affectée. */
    public boolean limiteeAuSuivi() {
        return title == null && description == null && priority == null && assigneeId == null
                && startDate == null && dueDate == null;
    }
}
