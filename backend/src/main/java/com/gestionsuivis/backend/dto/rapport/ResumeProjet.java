package com.gestionsuivis.backend.dto.rapport;

import com.gestionsuivis.backend.entity.StatutProjet;

import java.util.UUID;

/** Ligne du tableau récapitulatif des projets ({@code reportsProjectsSummary} du frontend). */
public record ResumeProjet(UUID id, String name, int progress, StatutProjet status) {
}
