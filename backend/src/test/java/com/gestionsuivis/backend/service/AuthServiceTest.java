package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.auth.ChangerMotDePasseRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionRequest;
import com.gestionsuivis.backend.dto.auth.InscriptionRequest;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.IdentifiantsInvalidesException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.JwtService;
import com.gestionsuivis.backend.security.SecuriteProperties;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.support.CaptureLogs;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Connexion, changement de mot de passe et inscription (Mockito, sans base). */
class AuthServiceTest {

    private static final String MOT_DE_PASSE = "MotDePasse@2026";

    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final UtilisateurCourant courant = mock(UtilisateurCourant.class);
    private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
    private Utilisateur utilisateur;

    private AuthService service(boolean inscriptionOuverte) {
        SecuriteProperties securite = SecuriteProperties.depuis(new MockEnvironment()
                .withProperty(SecuriteProperties.VAR_INSCRIPTION, String.valueOf(inscriptionOuverte)));
        return new AuthService(utilisateurs, encodeur, jwt, new UtilisateurMapper(), courant, securite);
    }

    @BeforeEach
    void initialiser() {
        utilisateur = Donnees.utilisateur("Rickael", Role.ADMIN, null);
        utilisateur.setEmail("rickael@company.com");
        utilisateur.setMotDePasseHash(encodeur.encode(MOT_DE_PASSE));
        when(utilisateurs.findByEmailIgnoreCase("rickael@company.com")).thenReturn(Optional.of(utilisateur));
        when(jwt.generer(any())).thenReturn("jeton-signe");
    }

    @Test
    void connexion_reussie_renvoieUtilisateurEtJeton() {
        try (CaptureLogs logs = CaptureLogs.de(AuthService.class)) {
            AuthService.ResultatConnexion resultat = service(false)
                    .connecter(new ConnexionRequest("  Rickael@Company.com ", MOT_DE_PASSE));

            assertThat(resultat.utilisateur().email()).isEqualTo("rickael@company.com");
            assertThat(resultat.jeton()).isEqualTo("jeton-signe");
            assertThat(resultat.toString()).doesNotContain("jeton-signe");
            assertThat(logs.tout()).doesNotContain(MOT_DE_PASSE).doesNotContain("jeton-signe");
        }
    }

    @Test
    void connexion_echouee_messageIdentiqueQuelleQueSoitLaCause() {
        AuthService service = service(false);

        assertThatThrownBy(() -> service.connecter(new ConnexionRequest("rickael@company.com", "mauvais")))
                .isInstanceOf(IdentifiantsInvalidesException.class).hasMessage("Email ou mot de passe incorrect.");
        assertThatThrownBy(() -> service.connecter(new ConnexionRequest("inconnu@company.com", MOT_DE_PASSE)))
                .isInstanceOf(IdentifiantsInvalidesException.class).hasMessage("Email ou mot de passe incorrect.");
        utilisateur.setActif(false);
        assertThatThrownBy(() -> service.connecter(new ConnexionRequest("rickael@company.com", MOT_DE_PASSE)))
                .isInstanceOf(IdentifiantsInvalidesException.class).hasMessage("Email ou mot de passe incorrect.");
    }

    @Test
    void changementDeMotDePasse_verifieLAncienEtEncodeLeNouveau() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(utilisateur));
        when(utilisateurs.findById(utilisateur.getId())).thenReturn(Optional.of(utilisateur));
        AuthService service = service(false);

        assertThatThrownBy(() -> service.changerMotDePasse(new ChangerMotDePasseRequest("faux", "Nouveau@2026")))
                .isInstanceOf(RequeteInvalideException.class)
                .satisfies(e -> assertThat(((RequeteInvalideException) e).getChamp()).isEqualTo("currentPassword"));
        assertThatThrownBy(() -> service.changerMotDePasse(new ChangerMotDePasseRequest(MOT_DE_PASSE, MOT_DE_PASSE)))
                .isInstanceOf(RequeteInvalideException.class);

        service.changerMotDePasse(new ChangerMotDePasseRequest(MOT_DE_PASSE, "Nouveau@2026"));

        assertThat(utilisateur.getMotDePasseHash()).startsWith("$2a$").doesNotContain("Nouveau@2026");
        assertThat(encodeur.matches("Nouveau@2026", utilisateur.getMotDePasseHash())).isTrue();
    }

    @Test
    void inscription_fermeeParDefaut() {
        assertThatThrownBy(() -> service(false).inscrire(new InscriptionRequest("A", "a@b.com", "12345678")))
                .isInstanceOf(AccesInterditException.class);
    }

    @Test
    void inscription_ouverte_creeUnEmploye() {
        when(utilisateurs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var cree = service(true).inscrire(new InscriptionRequest("Nouveau", "Nouveau@Exemple.com", "12345678"));

        assertThat(cree.role()).isEqualTo(Role.EMPLOYE);
        assertThat(cree.email()).isEqualTo("nouveau@exemple.com");
    }
}
