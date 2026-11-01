package com.gestionsuivis.backend.dto.departement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/** Corps de {@code PATCH /api/departments/{id}} ; un champ {@code null} n'est pas modifié. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModifierDepartementRequest(
        @Size(min = 1, max = 150, message = "Le nom doit contenir entre 1 et 150 caractères.")
        String name,

        @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères.")
        String description) {
}
