package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.activite.ActiviteResponse;
import com.gestionsuivis.backend.dto.activite.CreerActiviteRequest;
import com.gestionsuivis.backend.dto.activite.ModifierActiviteRequest;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.RoleMembreProjet;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.mapper.ActiviteMapper;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Règles métier des activités (affectation, statuts, permissions EMPLOYE, avancement), avec Mockito. */
class ActiviteServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2025-10-15T10:00:00Z");

    private final ActiviteRepository activites = mock(ActiviteRepository.class);
    private final ProjetRepository projets = mock(ProjetRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final UtilisateurCourant courant = mock(UtilisateurCourant.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AuditService audit = mock(AuditService.class);
    private final ActiviteService service = new ActiviteService(activites, projets, utilisateurs,
            new ActiviteMapper(new UtilisateurMapper()), new ControleAcces(projets, activites), courant, notifications,
            new CalculAvancement(activites), audit, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private final Utilisateur manager = Donnees.utilisateur("Manager", Role.MANAGER);
    private final Utilisateur employe = Donnees.utilisateur("Employe", Role.EMPLOYE);
    private final Utilisateur autre = Donnees.utilisateur("Autre", Role.EMPLOYE);
    private Projet projet;
    private Activite activite;

    @BeforeEach
    void initialiser() {
        projet = Donnees.projet("Budget", manager);
        activite = Donnees.activite("Saisie", projet, employe, StatutActivite.EN_COURS, LocalDate.of(2025, 10, 20));
        when(activites.findById(activite.getId())).thenReturn(Optional.of(activite));
        when(projets.findById(projet.getId())).thenReturn(Optional.of(projet));
        when(activites.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void employeAffecte_peutMettreAJourStatutEtAvancement() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        when(activites.avancementMoyen(projet.getId())).thenReturn(60.0);

        ActiviteResponse reponse = service.modifier(activite.getId(), new ModifierActiviteRequest(
                null, null, null, null, null, null, null, 60));

        assertThat(reponse.progress()).isEqualTo(60);
        assertThat(projet.getAvancement()).isEqualTo(60);
    }

    @Test
    void employeAffecte_neModifiePasLeTitreNiLesDates() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));

        assertThatThrownBy(() -> service.modifier(activite.getId(), new ModifierActiviteRequest(
                "Nouveau titre", null, null, null, null, null, null, null)))
                .isInstanceOf(AccesInterditException.class)
                .hasMessageContaining("statut et l'avancement");
    }

    @Test
    void autreEmploye_nAccedePasALActivite() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(autre));

        assertThatThrownBy(() -> service.obtenir(activite.getId())).isInstanceOf(AccesInterditException.class);
        assertThatThrownBy(() -> service.modifier(activite.getId(), new ModifierActiviteRequest(
                null, null, StatutActivite.TERMINE, null, null, null, null, null)))
                .isInstanceOf(AccesInterditException.class);
    }

    @Test
    void passageATermine_avancement100EtNotificationDuChef() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        when(activites.avancementMoyen(projet.getId())).thenReturn(100.0);

        ActiviteResponse reponse = service.modifier(activite.getId(), new ModifierActiviteRequest(
                null, null, StatutActivite.TERMINE, null, null, null, null, null));

        assertThat(reponse.status()).isEqualTo(StatutActivite.TERMINE);
        assertThat(reponse.progress()).isEqualTo(100);
        assertThat(reponse.completedAt()).isEqualTo(activite.getDateModification());
        assertThat(projet.getAvancement()).isEqualTo(100);
        verify(notifications).notifier(eq(manager), eq(employe.getId()), eq(TypeNotification.ACTIVITE),
                eq("Activité terminée"), any());
    }

    @Test
    void statutBloque_estAccepte() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        ActiviteResponse reponse = service.modifier(activite.getId(), new ModifierActiviteRequest(
                null, null, StatutActivite.BLOQUE, null, null, null, null, null));

        assertThat(reponse.status()).isEqualTo(StatutActivite.BLOQUE);
        assertThat(reponse.completedAt()).isNull();
    }

    @Test
    void creationParLeManager_affecteNotifieEtAjouteAuxMembres() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        when(utilisateurs.findById(employe.getId())).thenReturn(Optional.of(employe));

        ActiviteResponse reponse = service.creer(new CreerActiviteRequest("Rapport", null, projet.getId(), null,
                Priorite.HAUTE, employe.getId(), LocalDate.of(2025, 10, 16), LocalDate.of(2025, 10, 30), null));

        assertThat(reponse.status()).isEqualTo(StatutActivite.A_FAIRE);
        assertThat(reponse.assigneeId()).isEqualTo(employe.getId());
        assertThat(projet.utilisateursMembres()).contains(employe);
        assertThat(projet.membre(employe.getId())).get()
                .satisfies(m -> assertThat(m.getRole()).isEqualTo(RoleMembreProjet.MEMBRE));
        verify(notifications).notifier(eq(employe), eq(manager.getId()), eq(TypeNotification.ACTIVITE),
                eq("Nouvelle activité affectée"), any());
        verify(audit).journaliser(eq(manager.getId()), eq("created"), eq(AuditService.ACTIVITE), any());
    }

    @Test
    void creation_refuseeAUnEmployeEtSurUnProjetClos() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        CreerActiviteRequest requete = new CreerActiviteRequest("X", null, projet.getId(), null, null, null,
                null, null, null);
        assertThatThrownBy(() -> service.creer(requete)).isInstanceOf(AccesInterditException.class);

        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        projet.setStatut(StatutProjet.ARCHIVE);
        assertThatThrownBy(() -> service.creer(requete)).isInstanceOf(ConflitMetierException.class);
        verify(activites, never()).saveAndFlush(any());
    }

    @Test
    void datesIncoherentes_donnent422SurDueDate() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThatThrownBy(() -> service.creer(new CreerActiviteRequest("X", null, projet.getId(), null, null, null,
                LocalDate.of(2025, 10, 20), LocalDate.of(2025, 10, 10), null)))
                .isInstanceOf(RequeteInvalideException.class)
                .satisfies(e -> assertThat(((RequeteInvalideException) e).getChamp()).isEqualTo("dueDate"));
    }

    @Test
    void activiteEchue_estSignaleeEnRetard() {
        activite.setDateEcheance(LocalDate.of(2025, 10, 1));
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThat(service.obtenir(activite.getId()).isOverdue()).isTrue();
    }
}
