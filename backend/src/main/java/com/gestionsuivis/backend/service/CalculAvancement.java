package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.repository.ActiviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Avancement d'un projet : moyenne de l'avancement de ses activités, une activité
 * terminée comptant pour 100 % ; 0 % pour un projet sans activité.
 */
@Component
@RequiredArgsConstructor
public class CalculAvancement {

    private final ActiviteRepository activites;

    public Map<UUID, Integer> parProjet(Collection<UUID> projets) {
        Map<UUID, Integer> resultat = new HashMap<>();
        if (projets.isEmpty()) {
            return resultat;
        }
        for (Object[] ligne : activites.avancementMoyenParProjet(projets)) {
            UUID projet = (UUID) ligne[0];
            Number moyenne = (Number) ligne[1];
            resultat.put(projet, moyenne == null ? 0 : (int) Math.round(moyenne.doubleValue()));
        }
        return resultat;
    }

    public int pourProjet(UUID projet) {
        return parProjet(java.util.List.of(projet)).getOrDefault(projet, 0);
    }
}
