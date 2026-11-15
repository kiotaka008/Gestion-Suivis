package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.historique.EvenementHistoriqueResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.JournalAudit;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.JournalAuditRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Consultation de l'historique : droits, tri, pagination, libellés et absence de données sensibles. */
class HistoriqueServiceTest {

    private final JournalAuditRepository journal = mock(JournalAuditRepository.class);
    private final ProjetRepository projets = mock(ProjetRepository.class);
    private final ActiviteRepository activites = mock(ActiviteRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final UtilisateurCourant courant = mock(UtilisateurCourant.class);
    private final HistoriqueService service = new HistoriqueService(journal, projets, activites, utilisateurs,
            new ControleAcces(projets, activites), courant);

    private final Utilisateur admin = Donnees.utilisateur("Admin", Role.ADMIN);
    private final Utilisateur manager = Donnees.utilisateur("Manager", Role.MANAGER);
    private final Utilisateur employe = Donnees.utilisateur("Employe", Role.EMPLOYE);
    private final Utilisateur etranger = Donnees.utilisateur("Etranger", Role.EMPLOYE);
    private Projet projet;
    private Activite activite;

    @BeforeEach
    void initialiser() {
        projet = Donnees.projet("Budget", manager);
        activite = Donnees.activite("Saisie", projet, employe, StatutActivite.EN_COURS, LocalDate.of(2026, 12, 1));
        when(projets.findById(projet.getId())).thenReturn(Optional.of(projet));
        when(activites.findById(activite.getId())).thenReturn(Optional.of(activite));
        when(utilisateurs.findAllById(any())).thenReturn(List.of(admin, manager, employe));
    }

    private static JournalAudit entree(UUID auteur, String action, String type, UUID entite, Map<String, Object> details) {
        JournalAudit e = new JournalAudit();
        e.setId(UUID.randomUUID());
        e.setUtilisateurId(auteur);
        e.setAction(action);
        e.setTypeEntite(type);
        e.setEntiteId(entite);
        e.setMetadonnees(details);
        e.setDateCreation(Instant.parse("2026-11-12T09:00:00Z"));
        return e;
    }

    private static Page<JournalAudit> page(JournalAudit... entrees) {
        return new PageImpl<>(List.of(entrees), PageRequest.of(0, 20, HistoriqueService.TRI), entrees.length);
    }

    @Test
    void projet_inclutSesActivites_etImposeLeTriChronologiqueInverse() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        when(activites.idsDuProjet(projet.getId())).thenReturn(List.of(activite.getId()));
        when(journal.historiqueProjet(anyString(), any(), anyString(), anyCollection(), any())).thenReturn(page(
                entree(manager.getId(), "project.updated", "project", projet.getId(),
                        Map.of("fields", List.of("status", "dueDate"), "status", "archived")),
                entree(employe.getId(), "activity.updated", "activity", activite.getId(),
                        Map.of("fields", List.of("progress")))));

        // Tri demandé par l'appelant ignoré : l'historique est toujours chronologique inverse.
        PageResponse<EvenementHistoriqueResponse> reponse = service.projet(projet.getId(),
                PageRequest.of(1, 10, Sort.by("action")));

        ArgumentCaptor<Pageable> pagination = ArgumentCaptor.forClass(Pageable.class);
        verify(journal).historiqueProjet(eq(AuditService.PROJET), eq(projet.getId()), eq(AuditService.ACTIVITE),
                eq(List.of(activite.getId())), pagination.capture());
        assertThat(pagination.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pagination.getValue().getPageSize()).isEqualTo(10);
        assertThat(pagination.getValue().getSort()).isEqualTo(HistoriqueService.TRI);

        assertThat(reponse.data()).extracting(EvenementHistoriqueResponse::description).containsExactly(
                "Projet modifié : statut (Archivé), échéance",
                "Activité modifiée : progression");
        assertThat(reponse.data().getFirst().author()).isEqualTo(
                new EvenementHistoriqueResponse.Auteur(manager.getId(), "Manager"));
        assertThat(reponse.data().getFirst().fields()).containsExactly("status", "dueDate");
        assertThat(reponse.data().getFirst().status()).isEqualTo("archived");
    }

    @Test
    void projetSansActivite_interrogeSeulementLeProjet() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        when(activites.idsDuProjet(projet.getId())).thenReturn(List.of());
        when(journal.findByTypeEntiteAndEntiteId(eq(AuditService.PROJET), eq(projet.getId()), any()))
                .thenReturn(page(entree(manager.getId(), "project.created", "project", projet.getId(), null)));

        assertThat(service.projet(projet.getId(), PageRequest.of(0, 20)).data())
                .extracting(EvenementHistoriqueResponse::description).containsExactly("Projet créé");
        verify(journal, never()).historiqueProjet(anyString(), any(), anyString(), anyCollection(), any());
    }

    @Test
    void projet_nonVisible_estRefuseSansLireLeJournal() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(etranger));

        assertThatThrownBy(() -> service.projet(projet.getId(), PageRequest.of(0, 20)))
                .isInstanceOf(AccesInterditException.class);
        verify(journal, never()).findByTypeEntiteAndEntiteId(anyString(), any(), any());
        verify(journal, never()).historiqueProjet(anyString(), any(), anyString(), anyCollection(), any());
    }

    @Test
    void projet_inexistant_404() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(admin));
        UUID inconnu = UUID.randomUUID();
        assertThatThrownBy(() -> service.projet(inconnu, PageRequest.of(0, 20)))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void activite_visibleParSonResponsable_etActionsSurLeProjetLisibles() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        when(journal.findByTypeEntiteAndEntiteId(eq(AuditService.ACTIVITE), eq(activite.getId()), any()))
                .thenReturn(page(entree(employe.getId(), "activity.updated", "activity", activite.getId(),
                        Map.of("fields", List.of("status", "progress"), "status", "done"))));

        assertThat(service.activite(activite.getId(), PageRequest.of(0, 20)).data())
                .extracting(EvenementHistoriqueResponse::description)
                .containsExactly("Activité modifiée : statut (Terminée), progression");
    }

    @Test
    void activite_nonVisible_refusee() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(etranger));
        assertThatThrownBy(() -> service.activite(activite.getId(), PageRequest.of(0, 20)))
                .isInstanceOf(AccesInterditException.class);
    }

    @Test
    void utilisateur_reserveAuxAdministrateursEtALUtilisateurLuiMeme() {
        when(utilisateurs.findById(employe.getId())).thenReturn(Optional.of(employe));
        when(journal.findByTypeEntiteAndEntiteId(eq(AuditService.UTILISATEUR), eq(employe.getId()), any()))
                .thenReturn(page(entree(employe.getId(), "user.login", "user", employe.getId(), null)));

        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        assertThatThrownBy(() -> service.utilisateur(employe.getId(), PageRequest.of(0, 20)))
                .isInstanceOf(AccesInterditException.class);

        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        assertThat(service.utilisateur(employe.getId(), PageRequest.of(0, 20)).data())
                .extracting(EvenementHistoriqueResponse::description).containsExactly("Connexion");

        when(courant.obtenir()).thenReturn(Donnees.connecte(admin));
        assertThat(service.utilisateur(employe.getId(), PageRequest.of(0, 20)).data()).hasSize(1);
    }

    @Test
    void auteurSupprime_actionAutomatique_etMembreCite() {
        UUID supprime = UUID.randomUUID();
        JournalAudit parUnSupprime = entree(supprime, "project.member_added", "project", projet.getId(),
                Map.of("memberId", employe.getId().toString()));
        JournalAudit automatique = entree(null, "project.activity_deleted", "project", projet.getId(),
                Map.of("activityId", UUID.randomUUID().toString(), "activityTitle", "Saisie"));

        EvenementHistoriqueResponse ajout = HistoriqueService.versDto(parUnSupprime,
                id -> id.equals(employe.getId()) ? "Employe" : null);
        EvenementHistoriqueResponse suppression = HistoriqueService.versDto(automatique, id -> null);

        assertThat(ajout.author()).isEqualTo(new EvenementHistoriqueResponse.Auteur(supprime, "Utilisateur supprimé"));
        assertThat(ajout.description()).isEqualTo("Membre ajouté : Employe");
        assertThat(suppression.author()).isNull();
        assertThat(suppression.description()).isEqualTo("Activité supprimée : « Saisie »");
    }

    @Test
    void metadonneesInattendues_ignorees() {
        // Une métadonnée hors liste (ex. ajoutée en base par un autre outil) n'est jamais renvoyée.
        JournalAudit e = entree(admin.getId(), "user.updated", "user", employe.getId(),
                Map.of("fields", List.of("email"), "email", "secret@exemple.com", "password", "x"));

        EvenementHistoriqueResponse dto = HistoriqueService.versDto(e, id -> "Admin");

        assertThat(dto.description()).isEqualTo("Compte modifié : email");
        assertThat(dto.toString()).doesNotContain("secret@exemple.com").doesNotContain("password");
    }
}
