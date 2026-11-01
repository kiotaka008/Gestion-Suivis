package com.gestionsuivis.backend.dto.departement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Corps de {@code POST /api/departments}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreerDepartementRequest(
        @NotBlank(message = "Le nom du département est obligatoire.")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères.")
        String name,

        @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères.")
        String description,

        UUID managerId) {
}
