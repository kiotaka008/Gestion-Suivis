package com.gestionsuivis.backend.service.rapport;

import com.gestionsuivis.backend.dto.suivi.IndicateurRisqueResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Projet;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Évaluation du risque d'un projet (logique pure, testable sans base). Score :
 * <ul>
 *   <li>projet en retard : +3 ;</li>
 *   <li>taux d'activités en retard ≥ 50 % : +3, ≥ 25 % : +2, &gt; 0 : +1 ;</li>
 *   <li>avancement inférieur de 40 points ou plus à l'avancement attendu : +2, de 20 points : +1 ;</li>
 *   <li>échéance dans 7 jours ou moins avec un avancement &lt; 80 % : +1.</li>
 * </ul>
 * Niveau : ≥ 5 {@code critical}, ≥ 3 {@code high}, ≥ 1 {@code medium}, sinon {@code low}
 * (niveaux de {@code RiskItem} du service IA).
 */
public final class EvaluationRisque {

    private EvaluationRisque() {
    }

    public static IndicateurRisqueResponse evaluer(Projet projet, int avancement, Collection<Activite> activites,
                                                   LocalDate aujourdHui) {
        long total = activites.size();
        long enRetard = activites.stream().filter(a -> a.estEnRetard(aujourdHui)).count();
        double tauxRetard = Indicateurs.pourcentage(enRetard, total);

        Integer avancementAttendu = null;
        if (projet.getDateDebut() != null && projet.getDateEcheance() != null
                && projet.getDateEcheance().isAfter(projet.getDateDebut())) {
            long duree = ChronoUnit.DAYS.between(projet.getDateDebut(), projet.getDateEcheance());
            long ecoule = ChronoUnit.DAYS.between(projet.getDateDebut(), aujourdHui);
            avancementAttendu = (int) Math.max(0, Math.min(100, Math.round(ecoule * 100.0 / duree)));
        }
        Long joursRestants = projet.getDateEcheance() == null ? null
                : ChronoUnit.DAYS.between(aujourdHui, projet.getDateEcheance());

        int score = 0;
        List<String> raisons = new ArrayList<>();
        if (projet.estEnRetard(aujourdHui)) {
            score += 3;
            raisons.add("L'échéance du projet est dépassée.");
        }
        if (tauxRetard >= 50) {
            score += 3;
        } else if (tauxRetard >= 25) {
            score += 2;
        } else if (enRetard > 0) {
            score += 1;
        }
        if (enRetard > 0) {
            raisons.add(enRetard + " activité(s) en retard sur " + total + " (" + tauxRetard + " %).");
        }
        if (avancementAttendu != null && !projet.getStatut().estClos()) {
            int ecart = avancementAttendu - avancement;
            if (ecart >= 40) {
                score += 2;
            } else if (ecart >= 20) {
                score += 1;
            }
            if (ecart >= 20) {
                raisons.add("Avancement de " + avancement + " % pour " + avancementAttendu + " % attendu.");
            }
        }
        if (joursRestants != null && joursRestants >= 0 && joursRestants <= 7 && avancement < 80
                && !projet.getStatut().estClos()) {
            score += 1;
            raisons.add("Échéance dans " + joursRestants + " jour(s) avec " + avancement + " % d'avancement.");
        }

        String niveau = score >= 5 ? "critical" : score >= 3 ? "high" : score >= 1 ? "medium" : "low";
        return new IndicateurRisqueResponse(projet.getId(), projet.getNom(), projet.getStatut(), avancement,
                avancementAttendu, total, enRetard, tauxRetard, joursRestants, niveau, List.copyOf(raisons));
    }
}
