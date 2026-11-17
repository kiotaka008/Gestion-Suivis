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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        // S3 : verrou du projet, puis statut relu en base (ici : l'état courant de l'objet projet).
        when(projets.verrouiller(projet.getId())).thenReturn(Optional.of(projet));
        when(projets.statutActuel(projet.getId())).thenAnswer(invocation -> Optional.of(projet.getStatut()));
        // Comme la base : un identifiant est attribué à l'enregistrement.
        when(activites.saveAndFlush(any())).thenAnswer(invocation -> {
            Activite a = invocation.getArgument(0);
            if (a.getId() == null) {
                a.setId(java.util.UUID.randomUUID());
            }
            return a;
        });
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

    // ---------------------------------------------------------------- historique (audit)

    @Test
    @SuppressWarnings("unchecked")
    void modification_journaliseLesChampsReellementModifiesEtLeNouveauStatut() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        service.modifier(activite.getId(), new ModifierActiviteRequest(
                "Saisie", null, StatutActivite.TERMINE, null, null, null, LocalDate.of(2025, 10, 25), null));

        org.mockito.ArgumentCaptor<java.util.Map<String, ?>> details = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(audit).journaliser(eq(manager.getId()), eq("updated"), eq(AuditService.ACTIVITE), eq(activite.getId()),
                details.capture());
        // Titre identique : non retenu ; seuls le statut et l'échéance ont changé.
        assertThat(details.getValue().get(AuditService.CLE_CHAMPS)).isEqualTo(java.util.List.of("dueDate", "status"));
        assertThat(details.getValue().get(AuditService.CLE_STATUT)).isEqualTo("done");
    }

    @Test
    void modificationSansChangement_neJournaliseRien() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        service.modifier(activite.getId(), new ModifierActiviteRequest(
                "Saisie", null, StatutActivite.EN_COURS, null, null, null, null, null));

        verify(audit, org.mockito.Mockito.never()).journaliser(any(), eq("updated"), any(), any(), any());
    }

    @Test
    void creation_estAussiVisibleDansLHistoriqueDuProjet() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        ActiviteResponse reponse = service.creer(new CreerActiviteRequest("Rapport", null, projet.getId(), null,
                null, null, null, null, null));

        verify(audit).journaliser(eq(manager.getId()), eq("activity_created"), eq(AuditService.PROJET),
                eq(projet.getId()), eq(java.util.Map.of(AuditService.CLE_ACTIVITE, reponse.id(),
                        AuditService.CLE_TITRE_ACTIVITE, "Rapport")));
    }

    // ---------------------------------------------------------------- projet clos : activités en lecture seule (AUD-012)

    private static final String MESSAGE_PROJET_CLOS = "Impossible d'ajouter une activité à un projet terminé ou archivé.";

    /** Projet clos dont l'avancement (40 %) différerait d'un recalcul (aucune activité simulée : 0 %). */
    private void clore(StatutProjet statut) {
        projet.setStatut(statut);
        projet.setAvancement(40);
        activite.setAvancement(40);
        when(utilisateurs.findById(autre.getId())).thenReturn(Optional.of(autre));
    }

    /** État observable de l'activité et du projet, pour vérifier qu'un refus ne change rien. */
    private List<Object> etat() {
        return Arrays.asList(activite.getTitre(), activite.getDescription(), activite.getPriorite(),
                activite.getStatut(), activite.getAvancement(), activite.getDateDebut(), activite.getDateEcheance(),
                activite.getResponsable(), projet.getAvancement(), List.copyOf(projet.utilisateursMembres()));
    }

    /** Refus sans effet : ni écriture, ni suppression, ni recalcul, ni historique, ni notification. */
    private void verifierAucunEffet() {
        verify(activites, never()).saveAndFlush(any());
        verify(activites, never()).delete(any(Activite.class));
        verify(activites, never()).flush();
        verify(activites, never()).avancementMoyen(any());
        verify(utilisateurs, never()).findById(any());
        verifyNoInteractions(audit, notifications);
    }

    /** Requête PATCH qui ne modifie que le champ indiqué. */
    private ModifierActiviteRequest requeteSur(String champ) {
        return switch (champ) {
            case "title" -> new ModifierActiviteRequest("Nouveau titre", null, null, null, null, null, null, null);
            case "description" -> new ModifierActiviteRequest(null, "Nouvelle description", null, null, null, null,
                    null, null);
            case "status" -> new ModifierActiviteRequest(null, null, StatutActivite.TERMINE, null, null, null, null,
                    null);
            case "priority" -> new ModifierActiviteRequest(null, null, null, Priorite.HAUTE, null, null, null, null);
            case "assigneeId" -> new ModifierActiviteRequest(null, null, null, null, autre.getId(), null, null, null);
            case "startDate" -> new ModifierActiviteRequest(null, null, null, null, null, LocalDate.of(2025, 10, 16),
                    null, null);
            case "dueDate" -> new ModifierActiviteRequest(null, null, null, null, null, null,
                    LocalDate.of(2025, 10, 30), null);
            case "progress" -> new ModifierActiviteRequest(null, null, null, null, null, null, null, 80);
            default -> throw new IllegalArgumentException(champ);
        };
    }

    static Stream<Arguments> projetsClosEtChamps() {
        return Stream.of(StatutProjet.TERMINE, StatutProjet.ARCHIVE).flatMap(statut -> Stream.of(
                "title", "description", "priority", "startDate", "dueDate", "status", "progress", "assigneeId")
                .map(champ -> Arguments.of(statut, champ)));
    }

    // A et B : chaque champ, sur un projet terminé puis archivé, par le chef de projet.
    @ParameterizedTest(name = "{0} : {1}")
    @MethodSource("projetsClosEtChamps")
    void projetClos_modificationDUnChampParLeChef_409SansEffet(StatutProjet statut, String champ) {
        clore(statut);
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        List<Object> avant = etat();

        assertThatThrownBy(() -> service.modifier(activite.getId(), requeteSur(champ)))
                .isInstanceOf(ConflitMetierException.class)
                .hasMessage(MESSAGE_PROJET_CLOS);

        assertThat(etat()).isEqualTo(avant);
        verifierAucunEffet();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StatutProjet.class, names = {"TERMINE", "ARCHIVE"})
    void projetClos_suppressionParLeChef_409SansEffet(StatutProjet statut) {
        clore(statut);
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        List<Object> avant = etat();

        assertThatThrownBy(() -> service.supprimer(activite.getId()))
                .isInstanceOf(ConflitMetierException.class)
                .hasMessage(MESSAGE_PROJET_CLOS);

        assertThat(etat()).isEqualTo(avant);
        verifierAucunEffet();
    }

    // C : le changement de responsable n'ajoute aucun membre et ne notifie personne.
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StatutProjet.class, names = {"TERMINE", "ARCHIVE"})
    void projetClos_changementDeResponsable_409SansMembreAjouteNiNotification(StatutProjet statut) {
        clore(statut);
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThatThrownBy(() -> service.modifier(activite.getId(), requeteSur("assigneeId")))
                .isInstanceOf(ConflitMetierException.class);

        assertThat(activite.getResponsable()).isEqualTo(employe);
        assertThat(projet.utilisateursMembres()).doesNotContain(autre);
        assertThat(projet.membre(autre.getId())).isEmpty();
        verifyNoInteractions(notifications);
    }

    // D : la personne affectée ne peut plus changer ni le statut ni l'avancement.
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StatutProjet.class, names = {"TERMINE", "ARCHIVE"})
    void projetClos_personneAffectee_statutEtAvancementRefuses409(StatutProjet statut) {
        clore(statut);
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        List<Object> avant = etat();

        assertThatThrownBy(() -> service.modifier(activite.getId(), requeteSur("status")))
                .isInstanceOf(ConflitMetierException.class)
                .hasMessage(MESSAGE_PROJET_CLOS);
        assertThatThrownBy(() -> service.modifier(activite.getId(), requeteSur("progress")))
                .isInstanceOf(ConflitMetierException.class)
                .hasMessage(MESSAGE_PROJET_CLOS);

        assertThat(etat()).isEqualTo(avant);
        verifierAucunEffet();
    }

    // E : les droits sont vérifiés avant le statut du projet (403 avant 409).
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StatutProjet.class, names = {"TERMINE", "ARCHIVE"})
    void projetClos_sansDroit403_avecDroit409(StatutProjet statut) {
        clore(statut);
        List<Object> avant = etat();

        when(courant.obtenir()).thenReturn(Donnees.connecte(autre));
        assertThatThrownBy(() -> service.modifier(activite.getId(), requeteSur("status")))
                .isInstanceOf(AccesInterditException.class);
        assertThatThrownBy(() -> service.supprimer(activite.getId()))
                .isInstanceOf(AccesInterditException.class);
        // La personne affectée n'a pas le droit de supprimer : 403, pas 409.
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        assertThatThrownBy(() -> service.supprimer(activite.getId()))
                .isInstanceOf(AccesInterditException.class);

        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        assertThatThrownBy(() -> service.modifier(activite.getId(), requeteSur("status")))
                .isInstanceOf(ConflitMetierException.class);
        assertThatThrownBy(() -> service.supprimer(activite.getId()))
                .isInstanceOf(ConflitMetierException.class);

        assertThat(etat()).isEqualTo(avant);
        verifierAucunEffet();
    }

    // F : un projet en cours ou en attente n'est pas clos.
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StatutProjet.class, names = {"EN_COURS", "EN_ATTENTE"})
    void projetOuvert_modificationEtSuppressionRestentPossibles(StatutProjet statut) {
        projet.setStatut(statut);

        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        assertThat(service.modifier(activite.getId(), requeteSur("progress")).progress()).isEqualTo(80);

        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        assertThat(service.modifier(activite.getId(), requeteSur("title")).title()).isEqualTo("Nouveau titre");
        verify(audit).journaliser(eq(manager.getId()), eq("updated"), eq(AuditService.ACTIVITE),
                eq(activite.getId()), any());

        service.supprimer(activite.getId());
        verify(activites).delete(activite);
        verify(audit).journaliser(manager.getId(), "deleted", AuditService.ACTIVITE, activite.getId());
    }

    // G : la création reste refusée, aussi pour un projet terminé (le cas archivé est couvert plus haut).
    @Test
    void creation_refuseeSurUnProjetTermine_409SansEffet() {
        clore(StatutProjet.TERMINE);
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThatThrownBy(() -> service.creer(new CreerActiviteRequest("X", null, projet.getId(), null, null,
                autre.getId(), null, null, null)))
                .isInstanceOf(ConflitMetierException.class)
                .hasMessage(MESSAGE_PROJET_CLOS);

        assertThat(projet.membre(autre.getId())).isEmpty();
        verifierAucunEffet();
    }

    // ---------------------------------------------------------------- verrou du projet et recalcul (AUD-002, S3)
    // Mockito vérifie l'ordre des appels et les décisions. L'attente réelle entre deux transactions,
    // le verrou SQL généré et l'UPDATE limité aux colonnes modifiées ne se vérifient que sur PostgreSQL.

    @Test
    void modification_verrouEtStatutRelu_avantLaModificationDeLActiviteEtLeRecalcul() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        when(activites.avancementMoyen(projet.getId())).thenReturn(55.0);
        int[] avancementAuMomentDuVerrou = {-1};
        when(projets.verrouiller(projet.getId())).thenAnswer(invocation -> {
            avancementAuMomentDuVerrou[0] = activite.getAvancement();
            return Optional.of(projet);
        });
        int avancementInitial = activite.getAvancement();

        service.modifier(activite.getId(), new ModifierActiviteRequest(null, null, null, null, null, null, null, 55));

        var ordre = inOrder(activites, projets);
        ordre.verify(activites).findById(activite.getId());
        ordre.verify(projets).verrouiller(projet.getId());
        ordre.verify(projets).statutActuel(projet.getId());
        ordre.verify(activites).avancementMoyen(projet.getId());
        // Le verrou est pris avant que l'activité ne soit modifiée.
        assertThat(avancementAuMomentDuVerrou[0]).isEqualTo(avancementInitial);
        assertThat(activite.getAvancement()).isEqualTo(55);
        assertThat(projet.getAvancement()).isEqualTo(55);
        verify(projets, never()).findById(any());
    }

    @Test
    void creation_verrouEtStatutRelu_avantLEnregistrementEtLeRecalcul_sansLectureNonVerrouillee() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        service.creer(new CreerActiviteRequest("Rapport", null, projet.getId(), null, null, null, null, null, null));

        var ordre = inOrder(projets, activites);
        ordre.verify(projets).verrouiller(projet.getId());
        ordre.verify(projets).statutActuel(projet.getId());
        ordre.verify(activites).saveAndFlush(any());
        ordre.verify(activites).avancementMoyen(projet.getId());
        verify(projets, never()).findById(any());
    }

    @Test
    void suppression_verrouEtStatutRelu_avantLaSuppressionEtLeRecalcul() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        service.supprimer(activite.getId());

        var ordre = inOrder(activites, projets);
        ordre.verify(activites).findById(activite.getId());
        ordre.verify(projets).verrouiller(projet.getId());
        ordre.verify(projets).statutActuel(projet.getId());
        ordre.verify(activites).delete(activite);
        ordre.verify(activites).avancementMoyen(projet.getId());
    }

    // Le projet en mémoire est encore « en cours », mais la base indique qu'il vient d'être clos
    // (clôture validée pendant que la requête attendait le verrou) : la décision suit la base.
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StatutProjet.class, names = {"TERMINE", "ARCHIVE"})
    void statutReluApresLeVerrou_clotureConcurrente_toutEstRefuse(StatutProjet statut) {
        when(projets.statutActuel(projet.getId())).thenReturn(Optional.of(statut));
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThatThrownBy(() -> service.modifier(activite.getId(), requeteSur("title")))
                .isInstanceOf(ConflitMetierException.class).hasMessage(MESSAGE_PROJET_CLOS);
        assertThatThrownBy(() -> service.creer(new CreerActiviteRequest("X", null, projet.getId(), null, null,
                null, null, null, null)))
                .isInstanceOf(ConflitMetierException.class).hasMessage(MESSAGE_PROJET_CLOS);
        assertThatThrownBy(() -> service.supprimer(activite.getId()))
                .isInstanceOf(ConflitMetierException.class).hasMessage(MESSAGE_PROJET_CLOS);

        assertThat(projet.getStatut()).isEqualTo(StatutProjet.EN_COURS);
        assertThat(activite.getTitre()).isEqualTo("Saisie");
        verify(activites, never()).saveAndFlush(any());
        verify(activites, never()).delete(any(Activite.class));
        verify(activites, never()).avancementMoyen(any());
        verifyNoInteractions(audit, notifications);
    }

    @Test
    void statutReluApresLeVerrou_reouvertureConcurrente_modificationAutorisee() {
        // Le projet en mémoire est encore « terminé », mais la base indique qu'il a été rouvert.
        projet.setStatut(StatutProjet.TERMINE);
        when(projets.statutActuel(projet.getId())).thenReturn(Optional.of(StatutProjet.EN_COURS));
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        ActiviteResponse reponse = service.modifier(activite.getId(), requeteSur("title"));

        assertThat(reponse.title()).isEqualTo("Nouveau titre");
    }

    @Test
    void recalcul_neModifieQueLAvancementDuProjet_etProjetEnDynamicUpdate() {
        // @DynamicUpdate : seules les colonnes modifiées sont écrites (le SQL réel se vérifie sur PostgreSQL).
        assertThat(Projet.class.isAnnotationPresent(org.hibernate.annotations.DynamicUpdate.class)).isTrue();
        projet.setDescription("Description du projet");
        projet.setDateDebut(LocalDate.of(2025, 10, 1));
        projet.setDateEcheance(LocalDate.of(2025, 12, 31));
        List<Object> avant = Arrays.asList(projet.getNom(), projet.getDescription(), projet.getStatut(),
                projet.getPriorite(), projet.getDateDebut(), projet.getDateEcheance(), projet.getChefDeProjet(),
                List.copyOf(projet.utilisateursMembres()));
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        when(activites.avancementMoyen(projet.getId())).thenReturn(70.0);

        service.modifier(activite.getId(), new ModifierActiviteRequest(null, null, null, null, null, null, null, 70));

        assertThat(projet.getAvancement()).isEqualTo(70);
        assertThat(Arrays.asList(projet.getNom(), projet.getDescription(), projet.getStatut(), projet.getPriorite(),
                projet.getDateDebut(), projet.getDateEcheance(), projet.getChefDeProjet(),
                List.copyOf(projet.utilisateursMembres()))).isEqualTo(avant);
    }

    @Test
    void absences_comportementsInchanges() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        java.util.UUID inconnu = java.util.UUID.randomUUID();
        when(activites.findById(inconnu)).thenReturn(Optional.empty());
        when(projets.verrouiller(inconnu)).thenReturn(Optional.empty());

        // Activité inexistante : 404, sans verrou.
        assertThatThrownBy(() -> service.modifier(inconnu, requeteSur("title")))
                .isInstanceOf(com.gestionsuivis.backend.exception.RessourceIntrouvableException.class);
        assertThatThrownBy(() -> service.supprimer(inconnu))
                .isInstanceOf(com.gestionsuivis.backend.exception.RessourceIntrouvableException.class);
        verify(projets, never()).verrouiller(projet.getId());
        // Projet inexistant à la création : 422 sur projectId, comme avant.
        assertThatThrownBy(() -> service.creer(new CreerActiviteRequest("X", null, inconnu, null, null, null,
                null, null, null)))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessage("Projet introuvable.")
                .satisfies(e -> assertThat(((RequeteInvalideException) e).getChamp()).isEqualTo("projectId"));
        verify(activites, never()).saveAndFlush(any());
        verify(activites, never()).avancementMoyen(any());
    }
}
