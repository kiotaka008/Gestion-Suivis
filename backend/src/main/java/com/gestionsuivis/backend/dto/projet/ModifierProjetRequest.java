package com.gestionsuivis.backend.dto.projet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutProjet;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Corps de {@code PATCH /api/projects/{id}} ({@code Partial<Project>} du frontend).
 * Un champ absent ou {@code null} n'est pas modifié ; {@code memberIds}, s'il est
 * fourni, remplace la liste des membres (le chef de projet reste toujours membre).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModifierProjetRequest(
        // Absent ou null : non modifié. Fourni : au moins un caractère autre qu'un espace
        // (même règle que trim() du service et que la contrainte BTRIM(name) <> '' de la base).
        @Pattern(regexp = "(?s).*[^\\x00-\\x20].*", message = "Le nom du projet ne peut pas être vide.")
        @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères.")
        String name,

        @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères.")
        String description,

        StatutProjet status,
        Priorite priority,
        LocalDate startDate,
        LocalDate dueDate,
        UUID ownerId,
        List<UUID> memberIds) {
}
