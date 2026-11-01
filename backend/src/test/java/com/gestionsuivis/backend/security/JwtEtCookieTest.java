package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.support.Donnees;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JWT (émission, vérification, expiration), cookie HttpOnly, lecture du jeton et
 * conversion en utilisateur connecté, sans contexte Spring ni base de données.
 */
class JwtEtCookieTest {

    private static final String SECRET = "un-secret-de-test-suffisamment-long-pour-hs256";

    private final SecurityConfig configuration = new SecurityConfig();
    private final SecuriteProperties proprietes = SecuriteProperties.depuis(new MockEnvironment()
            .withProperty(SecuriteProperties.VAR_SECRET, SECRET));
    private final JwtDecoder decodeur = configuration.jwtDecoder(proprietes);
    private final Utilisateur utilisateur = Donnees.utilisateur("Rickael", Role.MANAGER, Donnees.departement("IT"));

    @Test
    void jetonEmis_estVerifieEtContientIdEtRole() {
        String jeton = service(Clock.systemUTC()).generer(utilisateur);

        Jwt jwt = decodeur.decode(jeton);

        assertThat(jwt.getSubject()).isEqualTo(utilisateur.getId().toString());
        assertThat(jwt.getClaimAsString(JwtService.CLAIM_ROLE)).isEqualTo("MANAGER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtService.EMETTEUR);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(59)));
    }

    @Test
    void jetonExpire_estRefuse() {
        Clock passe = Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC);
        String jeton = service(passe).generer(utilisateur);

        assertThatThrownBy(() -> decodeur.decode(jeton)).isInstanceOf(JwtException.class);
    }

    @Test
    void jetonSigneAvecUnAutreSecret_estRefuse() {
        SecuriteProperties autres = SecuriteProperties.depuis(new MockEnvironment()
                .withProperty(SecuriteProperties.VAR_SECRET, "un-autre-secret-de-test-tres-long-pour-hs256"));
        String jeton = new JwtService(configuration.jwtEncoder(autres), autres, Clock.systemUTC()).generer(utilisateur);

        assertThatThrownBy(() -> decodeur.decode(jeton)).isInstanceOf(JwtException.class);
    }

    @Test
    void secretAbsentOuCourt_estRemplaceParUnSecretAleatoire() {
        SecuriteProperties sansSecret = SecuriteProperties.depuis(new MockEnvironment());
        SecuriteProperties court = SecuriteProperties.depuis(new MockEnvironment()
                .withProperty(SecuriteProperties.VAR_SECRET, "court"));

        assertThat(sansSecret.secretGenere()).isTrue();
        assertThat(sansSecret.secretJwt()).hasSize(SecuriteProperties.LONGUEUR_MIN_SECRET);
        assertThat(court.secretGenere()).isTrue();
        assertThat(proprietes.toString()).doesNotContain(SECRET);
    }

    @Test
    void originesCors_parDefautEtSupplementaires() {
        SecuriteProperties avecOrigines = SecuriteProperties.depuis(new MockEnvironment()
                .withProperty(SecuriteProperties.VAR_ORIGINES, "https://suivi.exemple.com, pas-une-url"));

        assertThat(proprietes.originesAutorisees()).containsExactly("http://localhost:5173", "http://localhost:3000");
        assertThat(avecOrigines.originesAutorisees()).containsExactly("http://localhost:5173", "http://localhost:3000",
                "https://suivi.exemple.com");
        assertThat(proprietes.inscriptionOuverte()).isFalse();
        assertThat(proprietes.cookieSecurise()).isFalse();
    }

    @Test
    void cookie_estHttpOnlySameSiteLaxLimiteAApi() {
        ResponseCookie cookie = CookieAuthentification.creer("jeton", Duration.ofMinutes(60), true);
        ResponseCookie suppression = CookieAuthentification.supprimer(false);

        assertThat(cookie.toString()).contains("access_token=jeton").contains("Path=/api").contains("HttpOnly")
                .contains("Secure").contains("SameSite=Lax").contains("Max-Age=3600");
        assertThat(suppression.toString()).contains("Max-Age=0").doesNotContain("Secure");
    }

    @Test
    void lectureDuCookie_uniquementLeCookieAccessToken() {
        MockHttpServletRequest avecCookie = new MockHttpServletRequest("GET", "/api/projects");
        avecCookie.setCookies(new Cookie("autre", "x"), new Cookie(CookieAuthentification.NOM, "depuis-cookie"));
        MockHttpServletRequest cookieVide = new MockHttpServletRequest("GET", "/api/projects");
        cookieVide.setCookies(new Cookie(CookieAuthentification.NOM, " "));

        assertThat(JetonResolver.jetonCookie(avecCookie)).isEqualTo("depuis-cookie");
        assertThat(JetonResolver.jetonCookie(cookieVide)).isNull();
        assertThat(JetonResolver.jetonCookie(new MockHttpServletRequest("GET", "/api/projects"))).isNull();
    }

    @Test
    void lectureDuCookie_ignoreeSurLesRoutesDeConnexion() {
        for (String route : JetonResolver.ROUTES_SANS_JETON) {
            MockHttpServletRequest requete = new MockHttpServletRequest("POST", route);
            requete.setCookies(new Cookie(CookieAuthentification.NOM, "jeton-expire"));
            assertThat(JetonResolver.jetonCookie(requete)).as(route).isNull();
        }
    }

    @Test
    void convertisseur_rechargeLUtilisateurEtRefuseUnCompteDesactive() {
        UtilisateurRepository depot = mock(UtilisateurRepository.class);
        ConvertisseurJwtUtilisateur convertisseur = new ConvertisseurJwtUtilisateur(depot);
        Jwt jwt = decodeur.decode(service(Clock.systemUTC()).generer(utilisateur));

        when(depot.findById(utilisateur.getId())).thenReturn(Optional.of(utilisateur));
        AbstractAuthenticationToken authentification = convertisseur.convert(jwt);
        assertThat(authentification.getPrincipal()).isEqualTo(Donnees.connecte(utilisateur));
        assertThat(authentification.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MANAGER");

        utilisateur.setActif(false);
        assertThatThrownBy(() -> convertisseur.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);

        when(depot.findById(utilisateur.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> convertisseur.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
    }

    private JwtService service(Clock horloge) {
        return new JwtService(configuration.jwtEncoder(proprietes), proprietes, horloge);
    }
}
