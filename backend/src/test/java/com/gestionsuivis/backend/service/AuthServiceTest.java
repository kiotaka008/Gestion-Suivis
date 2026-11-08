package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.config.ExecuteurOrganisation;
import com.gestionsuivis.backend.dto.auth.ChangerMotDePasseRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionRequest;
import com.gestionsuivis.backend.dto.auth.InscriptionRequest;
import com.gestionsuivis.backend.entity.Organisation;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.IdentifiantsInvalidesException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.OrganisationRepository;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ContexteOrganisation;
import com.gestionsuivis.backend.security.JwtService;
import com.gestionsuivis.backend.security.SecuriteProperties;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.VerificateurGoogle;
import com.gestionsuivis.backend.dto.auth.ConnexionGoogleRequest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.gestionsuivis.backend.support.CaptureLogs;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Connexion multi-organisation, changement de mot de passe et inscription (Mockito,
 * sans base). Le dépôt simulé ne renvoie que les comptes de l'organisation courante,
 * comme le filtre {@code @TenantId} d'Hibernate.
 */
class AuthServiceTest {

    private static final String MOT_DE_PASSE = "MotDePasse@2026";
    private static final Instant MAINTENANT = Instant.parse("2026-11-05T10:00:00Z");
    private static final UUID AUTRE_ORGANISATION = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final OrganisationRepository organisations = mock(OrganisationRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final UtilisateurCourant courant = mock(UtilisateurCourant.class);
    private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
    private final List<Utilisateur> base = new ArrayList<>();
    private final JwtDecoder decodeurGoogle = mock(JwtDecoder.class);
    private Utilisateur utilisateur;

    private AuthService service(boolean inscriptionOuverte) {
        SecuriteProperties securite = SecuriteProperties.depuis(new MockEnvironment()
                .withProperty(SecuriteProperties.VAR_INSCRIPTION, String.valueOf(inscriptionOuverte)));
        return new AuthService(utilisateurs, organisations,
                new ExecuteurOrganisation(mock(PlatformTransactionManager.class)), encodeur, jwt,
                new UtilisateurMapper(), courant, securite, mock(AuditService.class),
                Clock.fixed(MAINTENANT, ZoneOffset.UTC), new VerificateurGoogle("client-test", decodeurGoogle));
    }

    @BeforeEach
    void initialiser() {
        utilisateur = compte("rickael@company.com", Donnees.ORGANISATION);
        when(organisations.findAll()).thenReturn(List.of(organisation(Donnees.ORGANISATION, "demo-corp"),
                organisation(AUTRE_ORGANISATION, "autre")));
        when(organisations.findBySlugIgnoreCase("autre"))
                .thenReturn(Optional.of(organisation(AUTRE_ORGANISATION, "autre")));
        when(utilisateurs.findByEmailIgnoreCase(anyString())).thenAnswer(invocation -> base.stream()
                .filter(u -> u.getEmail().equals(invocation.getArgument(0)))
                .filter(u -> ContexteOrganisation.courante().orElseThrow().equals(u.getOrganisationId()))
                .findFirst());
        when(utilisateurs.findById(any())).thenAnswer(invocation -> base.stream()
                .filter(u -> u.getId().equals(invocation.getArgument(0))).findFirst());
        when(jwt.generer(any())).thenReturn("jeton-signe");
    }

    @Test
    void connexion_reussie_renvoieUtilisateurEtJetonEtDateDeConnexion() {
        try (CaptureLogs logs = CaptureLogs.de(AuthService.class)) {
            AuthService.ResultatConnexion resultat = service(false)
                    .connecter(new ConnexionRequest("  Rickael@Company.com ", MOT_DE_PASSE));

            assertThat(resultat.utilisateur().email()).isEqualTo("rickael@company.com");
            assertThat(resultat.utilisateur().organizationId()).isEqualTo(Donnees.ORGANISATION);
            assertThat(resultat.jeton()).isEqualTo("jeton-signe");
            assertThat(resultat.toString()).doesNotContain("jeton-signe");
            assertThat(utilisateur.getDerniereConnexion()).isEqualTo(MAINTENANT);
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
    void memeEmailDansDeuxOrganisations_exigeLOrganisation() {
        compte("jean@company.com", Donnees.ORGANISATION);
        Utilisateur jeanAutre = compte("jean@company.com", AUTRE_ORGANISATION);
        AuthService service = service(false);

        assertThatThrownBy(() -> service.connecter(new ConnexionRequest("jean@company.com", MOT_DE_PASSE)))
                .isInstanceOf(RequeteInvalideException.class)
                .satisfies(e -> assertThat(((RequeteInvalideException) e).getChamp()).isEqualTo("organization"));

        AuthService.ResultatConnexion resultat = service.connecter(
                new ConnexionRequest("jean@company.com", MOT_DE_PASSE, "autre"));
        assertThat(resultat.utilisateur().id()).isEqualTo(jeanAutre.getId());
        assertThat(resultat.utilisateur().organizationId()).isEqualTo(AUTRE_ORGANISATION);
    }

    @Test
    void changementDeMotDePasse_verifieLAncienEtEncodeLeNouveau() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(utilisateur));
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
    void inscription_ouverte_exigeLOrganisationSiPlusieursEtCreeUnMembre() {
        when(utilisateurs.saveAndFlush(any())).thenAnswer(invocation -> {
            Utilisateur cree = invocation.getArgument(0);
            cree.setOrganisationId(ContexteOrganisation.courante().orElseThrow());
            return cree;
        });
        AuthService service = service(true);

        assertThatThrownBy(() -> service.inscrire(new InscriptionRequest("Nouveau", "n@exemple.com", "12345678")))
                .isInstanceOf(RequeteInvalideException.class);

        var cree = service.inscrire(new InscriptionRequest("Nouveau", "Nouveau@Exemple.com", "12345678", "autre"));

        assertThat(cree.role()).isEqualTo(Role.EMPLOYE);
        assertThat(cree.email()).isEqualTo("nouveau@exemple.com");
        assertThat(cree.organizationId()).isEqualTo(AUTRE_ORGANISATION);
    }

    // ---------------------------------------------------------------- Google

    @Test
    void google_jetonValideAvecCompteExistant_connecte() {
        when(decodeurGoogle.decode("jeton-google")).thenReturn(jwtGoogle("Rickael@Company.com", true));

        AuthService.ResultatConnexion resultat = service(false)
                .connecterAvecGoogle(new ConnexionGoogleRequest("jeton-google", null));

        assertThat(resultat.utilisateur().id()).isEqualTo(utilisateur.getId());
        assertThat(resultat.jeton()).isEqualTo("jeton-signe");
        assertThat(utilisateur.getDerniereConnexion()).isEqualTo(MAINTENANT);
    }

    @Test
    void google_jetonInvalideOuEmailNonVerifie_refuse() {
        when(decodeurGoogle.decode("faux")).thenThrow(new BadJwtException("signature"));
        when(decodeurGoogle.decode("non-verifie")).thenReturn(jwtGoogle("rickael@company.com", false));
        AuthService service = service(false);

        assertThatThrownBy(() -> service.connecterAvecGoogle(new ConnexionGoogleRequest("faux", null)))
                .isInstanceOf(IdentifiantsInvalidesException.class);
        assertThatThrownBy(() -> service.connecterAvecGoogle(new ConnexionGoogleRequest("non-verifie", null)))
                .isInstanceOf(IdentifiantsInvalidesException.class);
    }

    @Test
    void google_emailInconnu_refuseSansInscriptionEtCreeUnMembreSinon() {
        when(decodeurGoogle.decode("nouveau")).thenReturn(jwtGoogle("nouveau@gmail.com", true));
        when(utilisateurs.saveAndFlush(any())).thenAnswer(invocation -> {
            Utilisateur cree = invocation.getArgument(0);
            cree.setId(UUID.randomUUID());
            cree.setOrganisationId(ContexteOrganisation.courante().orElseThrow());
            base.add(cree);
            return cree;
        });

        assertThatThrownBy(() -> service(false).connecterAvecGoogle(new ConnexionGoogleRequest("nouveau", null)))
                .isInstanceOf(IdentifiantsInvalidesException.class).hasMessageContaining("Aucun compte");

        AuthService.ResultatConnexion resultat = service(true)
                .connecterAvecGoogle(new ConnexionGoogleRequest("nouveau", "autre"));
        assertThat(resultat.utilisateur().email()).isEqualTo("nouveau@gmail.com");
        assertThat(resultat.utilisateur().role()).isEqualTo(Role.EMPLOYE);
        assertThat(resultat.utilisateur().organizationId()).isEqualTo(AUTRE_ORGANISATION);
    }

    @Test
    void google_nonConfigure_renvoie403() {
        AuthService sansGoogle = new AuthService(utilisateurs, organisations,
                new ExecuteurOrganisation(mock(PlatformTransactionManager.class)), encodeur, jwt,
                new UtilisateurMapper(), courant, SecuriteProperties.depuis(new MockEnvironment()),
                mock(AuditService.class), Clock.systemUTC(), new VerificateurGoogle(null, null));

        assertThatThrownBy(() -> sansGoogle.connecterAvecGoogle(new ConnexionGoogleRequest("x", null)))
                .isInstanceOf(AccesInterditException.class);
    }

    private static Jwt jwtGoogle(String email, boolean verifie) {
        return Jwt.withTokenValue("jeton").header("alg", "RS256").issuer("https://accounts.google.com")
                .audience(List.of("client-test")).claim("email", email).claim("email_verified", verifie)
                .claim("name", "Utilisateur Google").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .build();
    }

    private Utilisateur compte(String email, UUID organisation) {
        Utilisateur compte = Donnees.utilisateur(email, Role.ADMIN);
        compte.setEmail(email);
        compte.setOrganisationId(organisation);
        compte.setMotDePasseHash(encodeur.encode(MOT_DE_PASSE));
        base.add(compte);
        return compte;
    }

    private static Organisation organisation(UUID id, String slug) {
        Organisation organisation = new Organisation();
        organisation.setId(id);
        organisation.setSlug(slug);
        organisation.setNom(slug);
        return organisation;
    }
}
