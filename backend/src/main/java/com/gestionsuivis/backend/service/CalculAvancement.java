package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Avancement d'un projet ({@code projects.progress}) : moyenne de l'avancement de ses
 * activités, une activité terminée comptant pour 100 % ; 0 % pour un projet sans
 * activité. Recalculé et enregistré à chaque création, modification ou suppression
 * d'activité.
 */
@Component
@RequiredArgsConstructor
public class CalculAvancement {

    private final ActiviteRepository activites;

    /** Recalcule l'avancement du projet (dans la transaction courante) et le renvoie. */
    public int recalculer(Projet projet) {
        Double moyenne = activites.avancementMoyen(projet.getId());
        int avancement = moyenne == null ? 0 : (int) Math.max(0, Math.min(100, Math.round(moyenne)));
        projet.setAvancement(avancement);
        return avancement;
    }
}
