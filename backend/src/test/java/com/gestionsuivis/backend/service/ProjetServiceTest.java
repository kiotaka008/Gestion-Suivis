package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.projet.CreerProjetRequest;
import com.gestionsuivis.backend.dto.projet.ModifierProjetRequest;
import com.gestionsuivis.backend.dto.projet.ProjetResponse;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.mapper.ProjetMapper;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.CommentaireRepository;
import com.gestionsuivis.backend.repository.DepartementRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Règles métier des projets (création, département imposé, chef, dates), avec Mockito. */
class ProjetServiceTest {

    private final ProjetRepository projets = mock(ProjetRepository.class);
    private final ActiviteRepository activites = mock(ActiviteRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final DepartementRepository departements = mock(DepartementRepository.class);
    private final UtilisateurCourant courant = mock(UtilisateurCourant.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ProjetService service = new ProjetService(projets, activites, mock(CommentaireRepository.class),
            utilisateurs, departements, new ProjetMapper(new UtilisateurMapper()), new CalculAvancement(activites),
            new ControleAcces(projets, activites), courant, notifications, Clock.systemUTC());

    private final Departement finance = Donnees.departement("Finance");
    private final Departement it = Donnees.departement("IT");
    private final Utilisateur manager = Donnees.utilisateur("Manager", Role.MANAGER, finance);
    private final Utilisateur employe = Donnees.utilisateur("Employe", Role.EMPLOYE, finance);
    private final Utilisateur employeIt = Donnees.utilisateur("Employe IT", Role.EMPLOYE, it);

    @BeforeEach
    void initialiser() {
        when(projets.save(any())).thenAnswer(invocation -> {
            Projet projet = invocation.getArgument(0);
            if (projet.getId() == null) {
                projet.setId(java.util.UUID.randomUUID());
            }
            return projet;
        });
        when(utilisateurs.getReferenceById(manager.getId())).thenReturn(manager);
        when(departements.getReferenceById(finance.getId())).thenReturn(finance);
        when(activites.avancementMoyenParProjet(any())).thenReturn(List.of());
    }

    @Test
    void employe_neCreePasDeProjet() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));

        assertThatThrownBy(() -> service.creer(requete(null, null))).isInstanceOf(AccesInterditException.class);
        verify(projets, never()).save(any());
    }

    @Test
    void manager_creeDansSonDepartementEtDevientChefParDefaut() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        ProjetResponse reponse = service.creer(requete(null, null));

        assertThat(reponse.departmentId()).isEqualTo(finance.getId());
        assertThat(reponse.ownerId()).isEqualTo(manager.getId());
        assertThat(reponse.status()).isEqualTo(StatutProjet.EN_COURS);
        assertThat(reponse.progress()).isZero();
    }

    @Test
    void manager_neCreePasDansUnAutreDepartementNiAvecUnChefExterne() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));
        when(utilisateurs.findById(employeIt.getId())).thenReturn(Optional.of(employeIt));

        assertThatThrownBy(() -> service.creer(requete(it.getId(), null)))
                .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("son département");
        assertThatThrownBy(() -> service.creer(requete(null, employeIt.getId())))
                .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("votre département");
    }

    @Test
    void chefEmploye_neChangePasLeChefDeProjetNiLeDepartement() {
        Projet projet = Donnees.projet("Interne", employe, finance);
        when(projets.findById(projet.getId())).thenReturn(Optional.of(projet));
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));

        assertThatThrownBy(() -> service.modifier(projet.getId(), new ModifierProjetRequest(
                null, null, null, null, null, null, manager.getId(), null, null)))
                .isInstanceOf(AccesInterditException.class);
        assertThatThrownBy(() -> service.modifier(projet.getId(), new ModifierProjetRequest(
                null, null, null, null, null, null, null, it.getId(), null)))
                .isInstanceOf(AccesInterditException.class);

        ProjetResponse renomme = service.modifier(projet.getId(), new ModifierProjetRequest(
                "Interne v2", null, StatutProjet.EN_ATTENTE, null, null, null, null, null, null));
        assertThat(renomme.name()).isEqualTo("Interne v2");
        assertThat(renomme.status()).isEqualTo(StatutProjet.EN_ATTENTE);
    }

    @Test
    void echeanceAvantLaDateDeDebut_estRefusee() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThatThrownBy(() -> service.creer(new CreerProjetRequest("P", null, null, null,
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 4, 1), null, null, null)))
                .isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void suppression_reserveeAuxAdministrateurs() {
        Projet projet = Donnees.projet("Budget", manager, finance);
        when(projets.findById(projet.getId())).thenReturn(Optional.of(projet));
        when(courant.obtenir()).thenReturn(Donnees.connecte(manager));

        assertThatThrownBy(() -> service.supprimer(projet.getId())).isInstanceOf(AccesInterditException.class);
    }

    private static CreerProjetRequest requete(java.util.UUID departementId, java.util.UUID chefId) {
        return new CreerProjetRequest("Migration cloud", "Description", null, null, null,
                LocalDate.of(2025, 12, 31), chefId, departementId, null);
    }
}
