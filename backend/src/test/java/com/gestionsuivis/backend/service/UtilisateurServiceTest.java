package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.utilisateur.CreerUtilisateurRequest;
import com.gestionsuivis.backend.dto.utilisateur.ModifierProfilRequest;
import com.gestionsuivis.backend.dto.utilisateur.ModifierUtilisateurRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Organisation;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ConversationIaRepository;
import com.gestionsuivis.backend.repository.NotificationRepository;
import com.gestionsuivis.backend.repository.OrganisationRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gestion des utilisateurs : droits ADMIN, unicité de l'email, dernier administrateur, suppression protégée. */
class UtilisateurServiceTest {

    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final ProjetRepository projets = mock(ProjetRepository.class);
    private final ActiviteRepository activites = mock(ActiviteRepository.class);
    private final ConversationIaRepository conversations = mock(ConversationIaRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UtilisateurCourant courant = mock(UtilisateurCourant.class);
    private final AuditService audit = mock(AuditService.class);
    private final OrganisationRepository organisations = mock(OrganisationRepository.class);
    private final BCryptPasswordEncoder encodeur = new BCryptPasswordEncoder(4);
    private final UtilisateurService service = new UtilisateurService(utilisateurs, projets, activites, conversations,
            notifications, encodeur, new UtilisateurMapper(), new ControleAcces(projets, activites), courant, audit,
            organisations);

    private final Utilisateur admin = Donnees.utilisateur("Admin", Role.ADMIN);
    private final Utilisateur manager = Donnees.utilisateur("Manager", Role.MANAGER);
    private final Utilisateur employe = Donnees.utilisateur("Employe", Role.EMPLOYE);

    @BeforeEach
    void initialiser() {
        for (Utilisateur u : List.of(admin, manager, employe)) {
            when(utilisateurs.findById(u.getId())).thenReturn(Optional.of(u));
        }
        when(utilisateurs.saveAndFlush(any())).thenAnswer(invocation -> {
            Utilisateur u = invocation.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        when(utilisateurs.countByRoleAndActifTrue(Role.ADMIN)).thenReturn(2L);
        Organisation organisation = new Organisation();
        organisation.setId(Donnees.ORGANISATION);
        when(organisations.verrouiller(Donnees.ORGANISATION)).thenReturn(Optional.of(organisation));
    }

    private void connecter(Utilisateur u) {
        when(courant.obtenir()).thenReturn(Donnees.connecte(u));
    }

    // ---------------------------------------------------------------- liste et consultation

    @Test
    void liste_interditeAuxMembres() {
        connecter(employe);
        assertThatThrownBy(() -> service.lister(null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccesInterditException.class);
        verify(utilisateurs, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void consultation_unMembreNeVoitQueSonPropreCompte() {
        connecter(employe);
        assertThat(service.obtenir(employe.getId()).id()).isEqualTo(employe.getId());
        assertThatThrownBy(() -> service.obtenir(manager.getId())).isInstanceOf(AccesInterditException.class);
    }

    @Test
    void consultation_compteInexistant_404() {
        connecter(admin);
        assertThatThrownBy(() -> service.obtenir(UUID.randomUUID())).isInstanceOf(RessourceIntrouvableException.class);
    }

    // ---------------------------------------------------------------- création

    @Test
    void creation_reserveeALAdministrateur() {
        connecter(manager);
        assertThatThrownBy(() -> service.creer(new CreerUtilisateurRequest("Nouveau", "n@exemple.com",
                Role.EMPLOYE, "MotDePasse123", null))).isInstanceOf(AccesInterditException.class);
        verify(utilisateurs, never()).saveAndFlush(any());
    }

    @Test
    void creation_emailNormaliseMotDePasseHacheEtCompteActif() {
        connecter(admin);
        UtilisateurResponse cree = service.creer(new CreerUtilisateurRequest("  Nouveau ", "  Nouveau@Exemple.COM ",
                Role.MANAGER, "MotDePasse123", null));

        ArgumentCaptor<Utilisateur> enregistre = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurs).saveAndFlush(enregistre.capture());
        assertThat(enregistre.getValue().getEmail()).isEqualTo("nouveau@exemple.com");
        assertThat(enregistre.getValue().getNom()).isEqualTo("Nouveau");
        assertThat(enregistre.getValue().getMotDePasseHash()).isNotEqualTo("MotDePasse123");
        assertThat(encodeur.matches("MotDePasse123", enregistre.getValue().getMotDePasseHash())).isTrue();
        assertThat(cree.isActive()).isTrue();
        assertThat(cree.role()).isEqualTo(Role.MANAGER);
        verify(audit).journaliser(admin.getId(), "created", AuditService.UTILISATEUR, cree.id());
    }

    @Test
    void creation_emailDejaUtilise_conflit() {
        connecter(admin);
        when(utilisateurs.existsByEmailIgnoreCase("pris@exemple.com")).thenReturn(true);
        assertThatThrownBy(() -> service.creer(new CreerUtilisateurRequest("X", "Pris@exemple.com",
                Role.EMPLOYE, "MotDePasse123", true)))
                .isInstanceOf(ConflitMetierException.class).hasMessage("Cet email est déjà utilisé.");
    }

    // ---------------------------------------------------------------- modification

    @Test
    @SuppressWarnings("unchecked")
    void modification_journaliseLesNomsDesChampsModifies_sansLeursValeurs() {
        connecter(admin);
        service.modifier(employe.getId(), new ModifierUtilisateurRequest("Employe", "nouvel@exemple.com", Role.MANAGER, null));

        ArgumentCaptor<Map<String, ?>> details = ArgumentCaptor.forClass(Map.class);
        verify(audit).journaliser(eq(admin.getId()), eq("updated"), eq(AuditService.UTILISATEUR), eq(employe.getId()),
                details.capture());
        assertThat(details.getValue().get(AuditService.CLE_CHAMPS)).isEqualTo(List.of("email", "role"));
        assertThat(details.getValue().toString()).doesNotContain("nouvel@exemple.com");
        assertThat(employe.getRole()).isEqualTo(Role.MANAGER);
        assertThat(employe.getEmail()).isEqualTo("nouvel@exemple.com");
    }

    @Test
    void modification_emailDUnAutreCompte_conflit() {
        connecter(admin);
        when(utilisateurs.existsByEmailIgnoreCaseAndIdNot("manager@exemple.com", employe.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.modifier(employe.getId(),
                new ModifierUtilisateurRequest(null, "manager@exemple.com", null, null)))
                .isInstanceOf(ConflitMetierException.class);
    }

    @Test
    void modification_reserveeALAdministrateur() {
        connecter(manager);
        assertThatThrownBy(() -> service.modifier(employe.getId(),
                new ModifierUtilisateurRequest("X", null, Role.ADMIN, null))).isInstanceOf(AccesInterditException.class);
        assertThat(employe.getRole()).isEqualTo(Role.EMPLOYE);
    }

    @Test
    void dernierAdministrateurActif_nePeutPasPerdreSonRole() {
        connecter(admin);
        when(utilisateurs.countByRoleAndActifTrue(Role.ADMIN)).thenReturn(1L);
        assertThatThrownBy(() -> service.modifier(admin.getId(),
                new ModifierUtilisateurRequest(null, null, Role.MANAGER, null)))
                .isInstanceOf(ConflitMetierException.class).hasMessageContaining("dernier administrateur");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    }

    // ---------------------------------------------------------------- activation / désactivation

    @Test
    void desactivation_desonPropreCompte_refusee() {
        connecter(admin);
        assertThatThrownBy(() -> service.desactiver(admin.getId())).isInstanceOf(ConflitMetierException.class);
        assertThat(admin.isActif()).isTrue();
    }

    @Test
    void desactivation_duDernierAdministrateurActif_refusee() {
        Utilisateur autreAdmin = Donnees.utilisateur("Autre admin", Role.ADMIN);
        when(utilisateurs.findById(autreAdmin.getId())).thenReturn(Optional.of(autreAdmin));
        when(utilisateurs.countByRoleAndActifTrue(Role.ADMIN)).thenReturn(1L);
        connecter(admin);
        assertThatThrownBy(() -> service.desactiver(autreAdmin.getId())).isInstanceOf(ConflitMetierException.class);
    }

    @Test
    void desactivationPuisActivation_journalisees() {
        connecter(admin);
        service.desactiver(employe.getId());
        assertThat(employe.isActif()).isFalse();
        service.activer(employe.getId());
        assertThat(employe.isActif()).isTrue();
        verify(audit).journaliser(admin.getId(), "deactivated", AuditService.UTILISATEUR, employe.getId());
        verify(audit).journaliser(admin.getId(), "activated", AuditService.UTILISATEUR, employe.getId());
    }

    // ---------------------------------------------------------------- suppression

    @Test
    void suppression_refuseeSiLUtilisateurEstEncoreReference() {
        connecter(admin);
        when(activites.countByResponsableId(employe.getId())).thenReturn(2L);
        assertThatThrownBy(() -> service.supprimer(employe.getId()))
                .isInstanceOf(ConflitMetierException.class).hasMessageContaining("désactivez-le");
        verify(utilisateurs, never()).delete(any(Utilisateur.class));
    }

    @Test
    void suppression_retireDesProjetsSupprimeSesNotificationsEtJournalise() {
        connecter(admin);
        Projet projet = Donnees.projet("Budget", manager);
        projet.ajouterMembre(employe, com.gestionsuivis.backend.entity.RoleMembreProjet.MEMBRE);
        when(projets.findByMembresUtilisateurId(employe.getId())).thenReturn(List.of(projet));

        service.supprimer(employe.getId());

        assertThat(projet.utilisateursMembres()).doesNotContain(employe);
        verify(notifications).supprimerParDestinataire(employe.getId());
        verify(utilisateurs).delete(employe);
        verify(audit).journaliser(admin.getId(), "deleted", AuditService.UTILISATEUR, employe.getId());
    }

    @Test
    void suppression_desonPropreCompte_refusee() {
        connecter(admin);
        assertThatThrownBy(() -> service.supprimer(admin.getId())).isInstanceOf(ConflitMetierException.class);
    }

    // ---------------------------------------------------------------- dernier administrateur actif (AUD-002, S5)
    // Mockito vérifie l'ordre « verrou de l'organisation, puis chargement, puis comptage » et les
    // décisions ; l'exclusion mutuelle réelle de deux transactions ne se démontre que sur PostgreSQL.

    private Utilisateur autreAdmin() {
        Utilisateur autre = Donnees.utilisateur("Autre admin", Role.ADMIN);
        when(utilisateurs.findById(autre.getId())).thenReturn(Optional.of(autre));
        return autre;
    }

    @Test
    void desactivationDUnAdministrateur_autoriseeSiUnAutreReste_verrouPuisChargementPuisComptage() {
        Utilisateur autre = autreAdmin();
        connecter(admin);

        service.desactiver(autre.getId());

        var ordre = inOrder(organisations, utilisateurs);
        ordre.verify(organisations).verrouiller(Donnees.ORGANISATION);
        ordre.verify(utilisateurs).findById(autre.getId());
        ordre.verify(utilisateurs).countByRoleAndActifTrue(Role.ADMIN);
        assertThat(autre.isActif()).isFalse();
        verify(audit).journaliser(admin.getId(), "deactivated", AuditService.UTILISATEUR, autre.getId());
    }

    @Test
    void desactivationDuDernierAdministrateur_refuseeApresVerrou_sansModification() {
        Utilisateur autre = autreAdmin();
        when(utilisateurs.countByRoleAndActifTrue(Role.ADMIN)).thenReturn(1L);
        connecter(admin);

        assertThatThrownBy(() -> service.desactiver(autre.getId()))
                .isInstanceOf(ConflitMetierException.class)
                .hasMessage("Impossible de désactiver le dernier administrateur actif.");

        var ordre = inOrder(organisations, utilisateurs);
        ordre.verify(organisations).verrouiller(Donnees.ORGANISATION);
        ordre.verify(utilisateurs).findById(autre.getId());
        ordre.verify(utilisateurs).countByRoleAndActifTrue(Role.ADMIN);
        assertThat(autre.isActif()).isTrue();
        verify(audit, never()).journaliser(any(), eq("deactivated"), any(), any());
    }

    @Test
    void retrogradationDUnAdministrateur_autoriseeSiUnAutreReste_verrouAvantChargement() {
        Utilisateur autre = autreAdmin();
        connecter(admin);

        service.modifier(autre.getId(), new ModifierUtilisateurRequest(null, null, Role.MANAGER, null));

        var ordre = inOrder(organisations, utilisateurs);
        ordre.verify(organisations).verrouiller(Donnees.ORGANISATION);
        ordre.verify(utilisateurs).findById(autre.getId());
        ordre.verify(utilisateurs).countByRoleAndActifTrue(Role.ADMIN);
        assertThat(autre.getRole()).isEqualTo(Role.MANAGER);
    }

    @Test
    void retrogradationDuDernierAdministrateur_refusee_roleInchange() {
        Utilisateur autre = autreAdmin();
        when(utilisateurs.countByRoleAndActifTrue(Role.ADMIN)).thenReturn(1L);
        connecter(admin);

        assertThatThrownBy(() -> service.modifier(autre.getId(),
                new ModifierUtilisateurRequest(null, null, Role.EMPLOYE, null)))
                .isInstanceOf(ConflitMetierException.class)
                .hasMessage("Impossible de retirer le rôle du dernier administrateur actif.");

        var ordre = inOrder(organisations, utilisateurs);
        ordre.verify(organisations).verrouiller(Donnees.ORGANISATION);
        ordre.verify(utilisateurs).countByRoleAndActifTrue(Role.ADMIN);
        assertThat(autre.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void suppressionDuDernierAdministrateurActif_refusee_sansSuppression() {
        Utilisateur autre = autreAdmin();
        when(utilisateurs.countByRoleAndActifTrue(Role.ADMIN)).thenReturn(1L);
        connecter(admin);

        assertThatThrownBy(() -> service.supprimer(autre.getId()))
                .isInstanceOf(ConflitMetierException.class)
                .hasMessage("Impossible de supprimer le dernier administrateur actif.");

        var ordre = inOrder(organisations, utilisateurs);
        ordre.verify(organisations).verrouiller(Donnees.ORGANISATION);
        ordre.verify(utilisateurs).findById(autre.getId());
        ordre.verify(utilisateurs).countByRoleAndActifTrue(Role.ADMIN);
        verify(utilisateurs, never()).delete(any(Utilisateur.class));
    }

    @Test
    void suppressionDUnAdministrateur_autoriseeSiUnAutreReste() {
        Utilisateur autre = autreAdmin();
        connecter(admin);

        service.supprimer(autre.getId());

        var ordre = inOrder(organisations, utilisateurs);
        ordre.verify(organisations).verrouiller(Donnees.ORGANISATION);
        ordre.verify(utilisateurs).findById(autre.getId());
        ordre.verify(utilisateurs).countByRoleAndActifTrue(Role.ADMIN);
        ordre.verify(utilisateurs).delete(autre);
    }

    @Test
    void desactivationDUnNonAdministrateur_autorisee_sansComptage() {
        connecter(admin);

        service.desactiver(employe.getId());

        verify(organisations).verrouiller(Donnees.ORGANISATION);
        verify(utilisateurs, never()).countByRoleAndActifTrue(any());
        assertThat(employe.isActif()).isFalse();
    }

    @Test
    void organisationIntrouvable_aucunChargementNiComptageNiModification() {
        // Le verrou n'est pas obtenu : rien n'est lu ni décidé (le comptage n'a jamais lieu avant le verrou).
        when(organisations.verrouiller(Donnees.ORGANISATION)).thenReturn(Optional.empty());
        Utilisateur autre = autreAdmin();
        connecter(admin);

        assertThatThrownBy(() -> service.desactiver(autre.getId())).isInstanceOf(RessourceIntrouvableException.class);
        assertThatThrownBy(() -> service.supprimer(autre.getId())).isInstanceOf(RessourceIntrouvableException.class);
        assertThatThrownBy(() -> service.modifier(autre.getId(),
                new ModifierUtilisateurRequest(null, null, Role.MANAGER, null)))
                .isInstanceOf(RessourceIntrouvableException.class);

        verify(utilisateurs, never()).findById(autre.getId());
        verify(utilisateurs, never()).countByRoleAndActifTrue(any());
        assertThat(autre.isActif()).isTrue();
        assertThat(autre.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void utilisateurDUneAutreOrganisation_404_sansComptageNiModification() {
        Utilisateur etranger = Donnees.utilisateur("Etranger", Role.ADMIN);
        etranger.setOrganisationId(UUID.fromString("00000000-0000-4000-8000-000000000099"));
        when(utilisateurs.findById(etranger.getId())).thenReturn(Optional.of(etranger));
        connecter(admin);

        assertThatThrownBy(() -> service.desactiver(etranger.getId())).isInstanceOf(RessourceIntrouvableException.class);
        assertThatThrownBy(() -> service.modifier(etranger.getId(),
                new ModifierUtilisateurRequest(null, null, Role.EMPLOYE, null)))
                .isInstanceOf(RessourceIntrouvableException.class);

        verify(utilisateurs, never()).countByRoleAndActifTrue(any());
        assertThat(etranger.isActif()).isTrue();
        assertThat(etranger.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void modificationSansRole_promotionEtActivation_sansComptage() {
        connecter(admin);

        // Sans rôle envoyé : aucun retrait d'administrateur possible, donc aucun verrou.
        service.modifier(employe.getId(), new ModifierUtilisateurRequest("Nouveau nom", null, null, null));
        verify(organisations, never()).verrouiller(any());

        // Promotion et activation ne font qu'ajouter des administrateurs : jamais de comptage.
        service.modifier(employe.getId(), new ModifierUtilisateurRequest(null, null, Role.ADMIN, null));
        assertThat(employe.getRole()).isEqualTo(Role.ADMIN);
        employe.setActif(false);
        service.activer(employe.getId());
        assertThat(employe.isActif()).isTrue();
        verify(utilisateurs, never()).countByRoleAndActifTrue(any());
    }

    // ---------------------------------------------------------------- profil

    @Test
    void profil_chacunModifieLeSien_sansPouvoirChangerSonRole() {
        connecter(employe);
        UtilisateurResponse profil = service.modifierProfil(new ModifierProfilRequest(" Nouveau nom ", null, " "));
        assertThat(profil.name()).isEqualTo("Nouveau nom");
        assertThat(profil.avatarUrl()).isNull();
        assertThat(employe.getRole()).isEqualTo(Role.EMPLOYE);
        verify(utilisateurs, never()).existsByEmailIgnoreCaseAndIdNot(anyString(), any());
    }
}
