package com.gestionsuivis.backend.service.rapport;

import com.gestionsuivis.backend.dto.rapport.BilanResponse;
import com.gestionsuivis.backend.dto.suivi.IndicateurRisqueResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.ParametreInvalideException;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Indicateurs des bilans (taux de réalisation, taux de retard, écart d'heures),
 * périodes et évaluation du risque : logique pure, sans base de données.
 */
class CalculsRapportTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2025, 10, 15);

    private final Utilisateur employe = Donnees.utilisateur("Tiavina", Role.EMPLOYE, null);
    private final Projet projet = Donnees.projet("Refonte", employe, null);

    // ---------------------------------------------------------------- indicateurs

    @Test
    void indicateurs_categoriesDisjointesEtTaux() {
        Activite terminee = Donnees.activite("A", projet, employe, StatutActivite.TERMINE, AUJOURDHUI.minusDays(3));
        terminee.setDateFin(Instant.parse("2025-03-03T08:00:00Z"));
        List<Activite> activites = List.of(
                terminee,
                Donnees.activite("B", projet, employe, StatutActivite.EN_COURS, AUJOURDHUI.minusDays(1)), // en retard
                Donnees.activite("C", projet, employe, StatutActivite.EN_COURS, AUJOURDHUI.plusDays(5)),
                Donnees.activite("D", projet, employe, StatutActivite.A_FAIRE, null),
                Donnees.activite("E", projet, employe, StatutActivite.EN_RETARD, AUJOURDHUI.plusDays(2)));

        Indicateurs indicateurs = Indicateurs.calculer(activites, AUJOURDHUI);

        assertThat(indicateurs.total()).isEqualTo(5);
        assertThat(indicateurs.terminees()).isEqualTo(1);
        assertThat(indicateurs.enRetard()).isEqualTo(2);
        assertThat(indicateurs.enCours()).isEqualTo(1);
        assertThat(indicateurs.aFaire()).isEqualTo(1);
        assertThat(indicateurs.tauxRealisation()).isEqualTo(20.0);
        assertThat(indicateurs.tauxRetard()).isEqualTo(40.0);
        assertThat(indicateurs.dureeMoyenneJours()).isEqualTo(2.0);
    }

    @Test
    void indicateurs_ecartEntreHeuresPrevuesEtReelles() {
        List<Activite> activites = List.of(
                Donnees.avecHeures(Donnees.activite("A", projet, employe, StatutActivite.EN_COURS, null), "10", "12.5"),
                Donnees.avecHeures(Donnees.activite("B", projet, employe, StatutActivite.EN_COURS, null), "6", "4"),
                Donnees.avecHeures(Donnees.activite("C", projet, employe, StatutActivite.EN_COURS, null), null, "1"));

        Indicateurs indicateurs = Indicateurs.calculer(activites, AUJOURDHUI);

        assertThat(indicateurs.heuresPrevues()).isEqualByComparingTo(new BigDecimal("16"));
        assertThat(indicateurs.heuresReelles()).isEqualByComparingTo(new BigDecimal("17.5"));
        assertThat(indicateurs.ecartHeures()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(indicateurs.tauxEcartHeures()).isEqualTo(9.4);
    }

    @Test
    void indicateurs_ensembleVide_tauxNuls() {
        Indicateurs indicateurs = Indicateurs.calculer(List.of(), AUJOURDHUI);

        assertThat(indicateurs.tauxRealisation()).isZero();
        assertThat(indicateurs.tauxRetard()).isZero();
        assertThat(indicateurs.tauxEcartHeures()).isNull();
        assertThat(indicateurs.dureeMoyenneJours()).isNull();
    }

    // ---------------------------------------------------------------- périodes

    @Test
    void periodes_duCahierDesChargesEtDuFrontend() {
        assertThat(Periodes.resoudre("week", null, null, AUJOURDHUI))   // mercredi 15/10/2025
                .isEqualTo(new BilanResponse.Periode("week", LocalDate.of(2025, 10, 13), LocalDate.of(2025, 10, 19)));
        assertThat(Periodes.resoudre("month", null, null, AUJOURDHUI))
                .isEqualTo(new BilanResponse.Periode("month", LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31)));
        assertThat(Periodes.resoudre("year", null, null, AUJOURDHUI))
                .isEqualTo(new BilanResponse.Periode("year", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        assertThat(Periodes.resoudre("7d", null, null, AUJOURDHUI).from()).isEqualTo(LocalDate.of(2025, 10, 9));
        assertThat(Periodes.resoudre("6m", null, null, AUJOURDHUI).from()).isEqualTo(LocalDate.of(2025, 4, 16));
        assertThat(Periodes.resoudre(null, null, null, AUJOURDHUI).code()).isEqualTo("30d");
        assertThat(Periodes.resoudre(null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), AUJOURDHUI).code())
                .isEqualTo("custom");
    }

    @Test
    void periodes_invalides() {
        assertThatThrownBy(() -> Periodes.resoudre("2w", null, null, AUJOURDHUI)).isInstanceOf(ParametreInvalideException.class);
        assertThatThrownBy(() -> Periodes.resoudre(null, LocalDate.of(2025, 2, 1), null, AUJOURDHUI))
                .isInstanceOf(ParametreInvalideException.class);
        assertThatThrownBy(() -> Periodes.resoudre(null, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 1, 1), AUJOURDHUI))
                .isInstanceOf(ParametreInvalideException.class);
    }

    // ---------------------------------------------------------------- risque

    @Test
    void risque_projetEnRetardAvecActivitesEnRetard_estCritique() {
        projet.setDateDebut(AUJOURDHUI.minusDays(100));
        projet.setDateEcheance(AUJOURDHUI.minusDays(1));
        List<Activite> activites = List.of(
                Donnees.activite("A", projet, employe, StatutActivite.EN_COURS, AUJOURDHUI.minusDays(5)),
                Donnees.activite("B", projet, employe, StatutActivite.TERMINE, AUJOURDHUI.minusDays(10)));

        IndicateurRisqueResponse risque = EvaluationRisque.evaluer(projet, 50, activites, AUJOURDHUI);

        assertThat(risque.riskLevel()).isEqualTo("critical");
        assertThat(risque.overdueActivities()).isEqualTo(1);
        assertThat(risque.overdueRate()).isEqualTo(50.0);
        assertThat(risque.expectedProgress()).isEqualTo(100);
        assertThat(risque.reasons()).isNotEmpty();
    }

    @Test
    void risque_projetDansLesTemps_estFaible() {
        projet.setDateDebut(AUJOURDHUI.minusDays(10));
        projet.setDateEcheance(AUJOURDHUI.plusDays(90));
        IndicateurRisqueResponse risque = EvaluationRisque.evaluer(projet, 20,
                List.of(Donnees.activite("A", projet, employe, StatutActivite.EN_COURS, AUJOURDHUI.plusDays(30))),
                AUJOURDHUI);

        assertThat(risque.riskLevel()).isEqualTo("low");
        assertThat(risque.daysRemaining()).isEqualTo(90);
        assertThat(risque.reasons()).isEmpty();
    }

    @Test
    void risque_projetClos_nEstPasEnRetard() {
        projet.setStatut(StatutProjet.TERMINE);
        projet.setDateEcheance(AUJOURDHUI.minusDays(30));

        assertThat(EvaluationRisque.evaluer(projet, 100, List.of(), AUJOURDHUI).riskLevel()).isEqualTo("low");
    }

    @Test
    void graphiques_repartitionsAuxLibellesDuFrontend() {
        assertThat(Graphiques.prioritesActivites(List.of()).labels()).containsExactly("Urgent", "Haute", "Moyenne", "Basse");
        assertThat(Graphiques.statutsProjets(List.of(projet), AUJOURDHUI).labels())
                .containsExactly("En cours", "Terminés", "En attente", "En retard", "Annulés");
    }
}
