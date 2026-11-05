package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.projet.CreerProjetRequest;
import com.gestionsuivis.backend.dto.projet.ModifierProjetRequest;
import com.gestionsuivis.backend.dto.projet.ProjetResponse;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.RoleMembreProjet;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.mapper.ProjetMapper;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Règles métier des projets (création, chef et membres, dates), avec Mockito. */
class ProjetServiceTest {

    private final ProjetRepository projets = mock(ProjetRepository.class);
    private final ActiviteRepository activites = mock(ActiviteRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final UtilisateurCourant courant = mock(UtilisateurCourant.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ProjetService service = new ProjetService(projets, activites, utilisateurs,
            new ProjetMapper(new UtilisateurMapper()), new ControleAcces(projets, activites), courant, notifications,
            mock(AuditService.class), Clock.systemUTC());

    private final Utilisateur admin = Donnees.utilisateur("Admin", Role.ADMIN);
    private final Utilisateur manager = Donnees.utilisateur("Manager", Role.MANAGER);
    private final Utilisateur employe = Donnees.utilisateur("Employe", Role.EMPLOYE);
    private final Utilisateur inactif = Donnees.utilisateur("Inactif", Role.EMPLOYE);

    @BeforeEach
    void initialiser() {
        when(projets.saveAndFlush(any())).thenAnswer(invocation -> {
            Projet projet = invocation.getArgument(0);
            if (projet.getId() == null) {
                projet.setId(UUID.randomUUID());
            }
            return projet;
        });
        inactif.setActif(false);
        for (Utilisateur u : List.of(admin, manager, employe, inactif)) {
            when(utilisateurs.findById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    @Test
    void employe_neCreePasDeProjet() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));

        assertThatThrownBy(() -> service.creer(requete(null, null))).isInstanceOf(AccesInterditException.class);
        verify(projets, never()).saveAndFlush(any());
    }

    @Test
    void manager_devientChefParDefautEtMembreProprietaire() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        ProjetResponse reponse = service.creer(requete(null, List.of(employe.getId(), manager.getId())));

        assertThat(reponse.ownerId()).isEqualTo(manager.getId());
        assertThat(reponse.status()).isEqualTo(StatutProjet.EN_COURS);
        assertThat(reponse.priority()).isEqualTo(Priorite.MOYENNE);
        assertThat(reponse.progress()).isZero();
        assertThat(reponse.memberIds()).containsExactlyInAnyOrder(manager.getId(), employe.getId());
    }

    @Test
    void membreDesactive_estRefuse() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThatThrownBy(() -> service.creer(requete(null, List.of(inactif.getId()))))
                .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("désactivé");
    }

    @Test
    void chefEmploye_neChangePasLeChefDeProjet() {
        Projet projet = Donnees.projet("Interne", employe);
        when(projets.findById(projet.getId())).thenReturn(Optional.of(projet));
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));

        assertThatThrownBy(() -> service.modifier(projet.getId(), new ModifierProjetRequest(
                null, null, null, null, null, null, manager.getId(), null)))
                .isInstanceOf(AccesInterditException.class);

        ProjetResponse renomme = service.modifier(projet.getId(), new ModifierProjetRequest(
                "Interne v2", null, StatutProjet.EN_ATTENTE, null, null, null, null, null));
        assertThat(renomme.name()).isEqualTo("Interne v2");
        assertThat(renomme.status()).isEqualTo(StatutProjet.EN_ATTENTE);
    }

    @Test
    void admin_changeLeChef_lAncienResteMembre() {
        Projet projet = Donnees.projet("Interne", manager);
        when(projets.findById(projet.getId())).thenReturn(Optional.of(projet));
        when(courant.obtenir()).thenReturn(Donnees.connecte(admin));

        ProjetResponse reponse = service.modifier(projet.getId(), new ModifierProjetRequest(
                null, null, null, null, null, null, employe.getId(), null));

        assertThat(reponse.ownerId()).isEqualTo(employe.getId());
        assertThat(projet.membre(employe.getId())).get()
                .satisfies(m -> assertThat(m.getRole()).isEqualTo(RoleMembreProjet.PROPRIETAIRE));
        assertThat(projet.membre(manager.getId())).get()
                .satisfies(m -> assertThat(m.getRole()).isEqualTo(RoleMembreProjet.MEMBRE));
    }

    @Test
    void remplacementDesMembres_conserveLeChef() {
        Projet projet = Donnees.projet("Interne", manager);
        projet.ajouterMembre(employe, RoleMembreProjet.MEMBRE);
        when(projets.findById(projet.getId())).thenReturn(Optional.of(projet));
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        ProjetResponse reponse = service.modifier(projet.getId(), new ModifierProjetRequest(
                null, null, null, null, null, null, null, List.of()));

        assertThat(reponse.memberIds()).containsExactly(manager.getId());
        assertThatThrownBy(() -> service.retirerMembre(projet.getId(), manager.getId()))
                .isInstanceOf(ConflitMetierException.class);
    }

    @Test
    void echeanceAvantLaDateDeDebut_estRefusee() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThatThrownBy(() -> service.creer(new CreerProjetRequest("P", null, null, null,
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 4, 1), null, null)))
                .isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void suppression_reserveeAuxAdministrateurs() {
        Projet projet = Donnees.projet("Budget", manager);
        when(projets.findById(projet.getId())).thenReturn(Optional.of(projet));
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThatThrownBy(() -> service.supprimer(projet.getId())).isInstanceOf(AccesInterditException.class);
    }

    private static CreerProjetRequest requete(UUID chefId, List<UUID> membres) {
        return new CreerProjetRequest("Migration cloud", "Description", null, null, null,
                LocalDate.of(2025, 12, 31), chefId, membres);
    }
}
