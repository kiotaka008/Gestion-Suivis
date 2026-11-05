package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Règles d'autorisation métier, à l'intérieur d'une organisation : ADMIN tout,
 * MANAGER consulte tout et gère ses projets, EMPLOYE ses propres activités.
 */
class ControleAccesTest {

    private final ProjetRepository projets = mock(ProjetRepository.class);
    private final ActiviteRepository activites = mock(ActiviteRepository.class);
    private final ControleAcces controle = new ControleAcces(projets, activites);

    private final Utilisateur admin = Donnees.utilisateur("Admin", Role.ADMIN);
    private final Utilisateur managerA = Donnees.utilisateur("Manager A", Role.MANAGER);
    private final Utilisateur managerB = Donnees.utilisateur("Manager B", Role.MANAGER);
    private final Utilisateur employe = Donnees.utilisateur("Employe", Role.EMPLOYE);
    private final Utilisateur autreEmploye = Donnees.utilisateur("Autre", Role.EMPLOYE);

    private Projet projetA;
    private Activite activiteEmploye;

    @BeforeEach
    void initialiser() {
        projetA = Donnees.projet("Budget", managerA);
        activiteEmploye = Donnees.activite("Saisie", projetA, employe, StatutActivite.EN_COURS, null);
        when(projets.existsByIdAndMembresUtilisateurId(any(), any())).thenReturn(false);
        when(activites.existsByProjetIdAndResponsableId(any(), any())).thenReturn(false);
    }

    @Test
    void admin_gereTout() {
        assertThat(controle.peutGererProjet(Donnees.connecte(admin), projetA)).isTrue();
        assertThat(controle.peutGererActivite(Donnees.connecte(admin), activiteEmploye)).isTrue();
        assertThat(controle.peutVoirUtilisateur(Donnees.connecte(admin), employe)).isTrue();
    }

    @Test
    void manager_gereSesProjetsEtConsulteLesAutres() {
        Projet projetB = Donnees.projet("Serveurs", managerB);

        assertThat(controle.peutGererProjet(Donnees.connecte(managerA), projetA)).isTrue();
        assertThat(controle.peutGererProjet(Donnees.connecte(managerA), projetB)).isFalse();
        assertThat(controle.peutVoirProjet(Donnees.connecte(managerA), projetB)).isTrue();
        assertThat(controle.peutVoirActivite(Donnees.connecte(managerB), activiteEmploye)).isTrue();
        assertThatThrownBy(() -> controle.verifierGererProjet(Donnees.connecte(managerA), projetB))
                .isInstanceOf(AccesInterditException.class);
    }

    @Test
    void manager_voitLesUtilisateursDeSonOrganisation() {
        assertThat(controle.peutVoirUtilisateur(Donnees.connecte(managerA), employe)).isTrue();
    }

    @Test
    void employe_voitSaPropreActiviteMaisNeLaGerePas() {
        UtilisateurConnecte u = Donnees.connecte(employe);

        assertThat(controle.peutVoirActivite(u, activiteEmploye)).isTrue();
        assertThat(controle.estResponsableActivite(u, activiteEmploye)).isTrue();
        assertThat(controle.peutGererActivite(u, activiteEmploye)).isFalse();
    }

    @Test
    void employe_nAccedePasALActiviteDUnAutreEnChangeantLIdentifiant() {
        UtilisateurConnecte autre = Donnees.connecte(autreEmploye);

        assertThat(controle.peutVoirActivite(autre, activiteEmploye)).isFalse();
        assertThatThrownBy(() -> controle.verifierVoirActivite(autre, activiteEmploye))
                .isInstanceOf(AccesInterditException.class);
        assertThat(controle.peutVoirUtilisateur(autre, employe)).isFalse();
    }

    @Test
    void employe_voitUnProjetDontIlEstMembreOuAffecte() {
        UtilisateurConnecte u = Donnees.connecte(employe);
        assertThat(controle.peutVoirProjet(u, projetA)).isFalse();

        when(activites.existsByProjetIdAndResponsableId(projetA.getId(), employe.getId())).thenReturn(true);
        assertThat(controle.peutVoirProjet(u, projetA)).isTrue();
        assertThat(controle.peutGererProjet(u, projetA)).isFalse();

        Projet autreProjet = Donnees.projet("Autre", managerB);
        when(projets.existsByIdAndMembresUtilisateurId(autreProjet.getId(), employe.getId())).thenReturn(true);
        assertThat(controle.peutVoirProjet(u, autreProjet)).isTrue();
    }

    @Test
    void chefDeProjetEmploye_gereSonProjet() {
        Projet projetDeLEmploye = Donnees.projet("Interne", employe);

        assertThat(controle.peutGererProjet(Donnees.connecte(employe), projetDeLEmploye)).isTrue();
    }

    @Test
    void creationDeProjet_reserveeAuxAdminEtManager() {
        assertThatThrownBy(() -> controle.verifierCreerProjet(Donnees.connecte(employe)))
                .isInstanceOf(AccesInterditException.class);
        controle.verifierCreerProjet(Donnees.connecte(managerA));
        controle.verifierCreerProjet(Donnees.connecte(admin));
    }
}
