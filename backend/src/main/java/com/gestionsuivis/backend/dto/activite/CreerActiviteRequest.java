package com.gestionsuivis.backend.dto.activite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutActivite;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /api/activities}. Compatible avec {@code CreateActivityModal}
 * du frontend ({@code title, description, projectId, priority, dueDate, status}),
 * avec en plus l'affectation, la date de début, les heures prévues et l'avancement.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreerActiviteRequest(
        @NotBlank(message = "Le titre de l'activité est obligatoire.")
        @Size(max = 200, message = "Le titre ne doit pas dépasser 200 caractères.")
        String title,

        @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères.")
        String description,

        @NotNull(message = "Le projet est obligatoire.")
        UUID projectId,

        StatutActivite status,
        Priorite priority,
        UUID assigneeId,
        LocalDate startDate,
        LocalDate dueDate,

        @DecimalMin(value = "0", message = "Les heures prévues doivent être positives.")
        @DecimalMax(value = "99999", message = "Les heures prévues sont trop élevées.")
        BigDecimal plannedHours,

        @Min(value = 0, message = "L'avancement doit être compris entre 0 et 100.")
        @Max(value = 100, message = "L'avancement doit être compris entre 0 et 100.")
        Integer progress) {
}
