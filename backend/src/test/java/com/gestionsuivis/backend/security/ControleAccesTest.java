package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Departement;
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
 * Règles d'autorisation métier (cahier des charges, §7) :
 * ADMIN tout, MANAGER son département, EMPLOYE ses propres activités.
 */
class ControleAccesTest {

    private final ProjetRepository projets = mock(ProjetRepository.class);
    private final ActiviteRepository activites = mock(ActiviteRepository.class);
    private final ControleAcces controle = new ControleAcces(projets, activites);

    private final Departement finance = Donnees.departement("Finance");
    private final Departement it = Donnees.departement("IT");
    private final Utilisateur admin = Donnees.utilisateur("Admin", Role.ADMIN, null);
    private final Utilisateur managerFinance = Donnees.utilisateur("Manager Finance", Role.MANAGER, finance);
    private final Utilisateur managerIt = Donnees.utilisateur("Manager IT", Role.MANAGER, it);
    private final Utilisateur employe = Donnees.utilisateur("Employe", Role.EMPLOYE, finance);
    private final Utilisateur autreEmploye = Donnees.utilisateur("Autre", Role.EMPLOYE, finance);

    private Projet projetFinance;
    private Activite activiteEmploye;

    @BeforeEach
    void initialiser() {
        projetFinance = Donnees.projet("Budget", managerFinance, finance);
        activiteEmploye = Donnees.activite("Saisie", projetFinance, employe, StatutActivite.EN_COURS, null);
        when(projets.existsByIdAndMembresId(any(), any())).thenReturn(false);
        when(activites.existsByProjetIdAndResponsableId(any(), any())).thenReturn(false);
    }

    @Test
    void admin_gereTout() {
        assertThat(controle.peutGererProjet(Donnees.connecte(admin), projetFinance)).isTrue();
        assertThat(controle.peutGererActivite(Donnees.connecte(admin), activiteEmploye)).isTrue();
        assertThat(controle.peutVoirUtilisateur(Donnees.connecte(admin), employe)).isTrue();
    }

    @Test
    void manager_gereLesProjetsDeSonDepartementUniquement() {
        Projet projetIt = Donnees.projet("Serveurs", managerIt, it);

        assertThat(controle.peutGererProjet(Donnees.connecte(managerFinance), projetFinance)).isTrue();
        assertThat(controle.peutGererProjet(Donnees.connecte(managerFinance), projetIt)).isFalse();
        assertThat(controle.peutVoirProjet(Donnees.connecte(managerFinance), projetIt)).isFalse();
        assertThatThrownBy(() -> controle.verifierGererProjet(Donnees.connecte(managerFinance), projetIt))
                .isInstanceOf(AccesInterditException.class);
    }

    @Test
    void manager_voitLesUtilisateursDeSonDepartementUniquement() {
        assertThat(controle.peutVoirUtilisateur(Donnees.connecte(managerFinance), employe)).isTrue();
        assertThat(controle.peutVoirUtilisateur(Donnees.connecte(managerIt), employe)).isFalse();
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
        assertThat(controle.peutVoirProjet(u, projetFinance)).isFalse();

        when(activites.existsByProjetIdAndResponsableId(projetFinance.getId(), employe.getId())).thenReturn(true);

        assertThat(controle.peutVoirProjet(u, projetFinance)).isTrue();
        assertThat(controle.peutGererProjet(u, projetFinance)).isFalse();
    }

    @Test
    void chefDeProjetEmploye_gereSonProjet() {
        Projet projetDeLEmploye = Donnees.projet("Interne", employe, finance);

        assertThat(controle.peutGererProjet(Donnees.connecte(employe), projetDeLEmploye)).isTrue();
    }

    @Test
    void creationDeProjet_reserveeAuxAdminEtManager() {
        assertThatThrownBy(() -> controle.verifierCreerProjet(Donnees.connecte(employe)))
                .isInstanceOf(AccesInterditException.class);
        controle.verifierCreerProjet(Donnees.connecte(managerFinance));
        controle.verifierCreerProjet(Donnees.connecte(admin));
    }
}
