package com.gestionsuivis.backend.dto.projet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutProjet;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Corps de {@code PATCH /api/projects/{id}} ({@code Partial<Project>} du frontend).
 * Un champ absent ou {@code null} n'est pas modifié ; {@code memberIds}, s'il est
 * fourni, remplace la liste des membres.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModifierProjetRequest(
        @Size(min = 1, max = 200, message = "Le nom doit contenir entre 1 et 200 caractères.")
        String name,

        @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères.")
        String description,

        StatutProjet status,
        Priorite priority,
        LocalDate startDate,
        LocalDate dueDate,
        UUID ownerId,
        UUID departmentId,
        List<UUID> memberIds) {
}
