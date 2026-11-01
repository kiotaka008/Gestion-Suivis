package com.gestionsuivis.backend.dto.projet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutProjet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Corps de {@code POST /api/projects}. Compatible avec {@code CreateProjectModal}
 * du frontend ({@code name, description, priority, dueDate, status, progress}) ;
 * {@code progress} est ignoré car calculé. Le chef de projet est par défaut
 * l'utilisateur connecté ; le département d'un MANAGER est imposé.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreerProjetRequest(
        @NotBlank(message = "Le nom du projet est obligatoire.")
        @Size(max = 200, message = "Le nom ne doit pas dépasser 200 caractères.")
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
