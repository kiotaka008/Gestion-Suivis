package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.config.ExecuteurOrganisation;
import com.gestionsuivis.backend.controller.AuthController;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.IdentifiantsInvalidesException;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.service.AuthService;
import com.gestionsuivis.backend.support.Donnees;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaîne de sécurité HTTP réelle (filtres Spring Security, CSRF, CORS, cookie JWT,
 * réponses 401/403 JSON) sur la tranche Web MVC : pas de serveur, pas de base de
 * données (le dépôt des utilisateurs et le service d'authentification sont simulés).
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtService.class, VerificateurGoogle.class, LimiteurConnexion.class,
        SecuriteHttpTest.Configuration.class})
class SecuriteHttpTest {

    /** Exécuteur d'organisation sans base : la transaction est simulée. */
    @org.springframework.boot.test.context.TestConfiguration
    static class Configuration {
        @org.springframework.context.annotation.Bean
        ExecuteurOrganisation executeurOrganisation() {
            return new ExecuteurOrganisation(org.mockito.Mockito.mock(
                    org.springframework.transaction.PlatformTransactionManager.class));
        }
    }

    private static final String CORPS_CONNEXION = "{\"email\":\"rickael@company.com\",\"password\":\"Admin@2026\"}";
    private static final String CORPS_MOT_DE_PASSE = "{\"currentPassword\":\"Admin@2026\",\"newPassword\":\"Nouveau@2026\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UtilisateurRepository utilisateurs;

    private Utilisateur utilisateur;
    private UtilisateurResponse dto;

    @BeforeEach
    void initialiser() {
        utilisateur = Donnees.utilisateur("Rickael", Role.ADMIN);
        utilisateur.setEmail("rickael@company.com");
        dto = new UtilisateurMapper().versDto(utilisateur);
        when(utilisateurs.findById(utilisateur.getId())).thenReturn(Optional.of(utilisateur));
        when(authService.moi()).thenReturn(dto);
        when(authService.connecter(any())).thenReturn(new AuthService.ResultatConnexion(dto, jwtService.generer(utilisateur)));
    }

    @Test
    void sansSession_meRenvoie401JsonEtPoseLeCookieCsrf() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andReturn();

        assertThat(resultat.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(c -> c.startsWith("XSRF-TOKEN="));
    }

    @Test
    void connexion_sansJetonCsrf_poseLeCookieHttpOnlyEtRenvoieUser() throws Exception {
        MvcResult resultat = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPS_CONNEXION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("rickael@company.com"))
                .andExpect(jsonPath("$.user.role").value("admin"))
                .andExpect(jsonPath("$.user.isActive").value(true))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        assertThat(resultat.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(c -> c.startsWith("access_token=")
                && c.contains("HttpOnly") && c.contains("SameSite=Lax") && c.contains("Path=/api"));
    }

    @Test
    void connexion_echouee_renvoie401AvecMessage() throws Exception {
        when(authService.connecter(any())).thenThrow(new IdentifiantsInvalidesException());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CORPS_CONNEXION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect."));
    }

    @Test
    void connexion_avecUnCookieExpire_fonctionneQuandMeme() throws Exception {
        mockMvc.perform(post("/api/auth/login").cookie(new Cookie("access_token", "jeton.expire.invalide"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPS_CONNEXION))
                .andExpect(status().isOk());
    }

    @Test
    void connexionInvalide_renvoie422AvecErreursParChamp() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void avecCookieJwtValide_meRenvoieLUtilisateur() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(cookieJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(utilisateur.getId().toString()));
    }

    @Test
    void compteDesactive_avecJetonEncoreValide_renvoie401() throws Exception {
        utilisateur.setActif(false);

        mockMvc.perform(get("/api/auth/me").cookie(cookieJwt()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void requeteModifiante_sansJetonCsrf_renvoie403Json() throws Exception {
        mockMvc.perform(post("/api/auth/change-password").cookie(cookieJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPS_MOT_DE_PASSE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CSRF")));
    }

    @Test
    void requeteModifiante_avecCookieEtEnteteXsrf_commeAxios_estAcceptee() throws Exception {
        MvcResult premier = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
        Cookie xsrf = premier.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        mockMvc.perform(post("/api/auth/change-password").cookie(cookieJwt(), xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPS_MOT_DE_PASSE))
                .andExpect(status().isNoContent());
    }

    @Test
    void jetonCsrf_resteStableEntreLesRequetes_pourLesAppelsParalleles() throws Exception {
        Cookie xsrf = mockMvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        for (int i = 0; i < 2; i++) {
            MvcResult resultat = mockMvc.perform(post("/api/auth/change-password").cookie(cookieJwt(), xsrf)
                            .header("X-XSRF-TOKEN", xsrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON).content(CORPS_MOT_DE_PASSE))
                    .andExpect(status().isNoContent())
                    .andReturn();
            assertThat(resultat.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                    .noneMatch(c -> c.startsWith("XSRF-TOKEN=") && !c.contains("Max-Age=0"));
        }
    }

    @Test
    void enteteBearer_dispenseDeCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generer(utilisateur))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPS_MOT_DE_PASSE))
                .andExpect(status().isNoContent());
    }

    @Test
    void deconnexion_effaceLeCookie() throws Exception {
        MvcResult premier = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        Cookie xsrf = premier.getResponse().getCookie("XSRF-TOKEN");

        MvcResult resultat = mockMvc.perform(post("/api/auth/logout").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(resultat.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(c -> c.startsWith("access_token=") && c.contains("Max-Age=0"));
    }

    @Test
    void deconnexion_sansJetonCsrf_effaceAussiLeCookie() throws Exception {
        // Cas du frontend Vite (port 5173) qui appelle l'API (port 3000) sans en-tête X-XSRF-TOKEN.
        MvcResult resultat = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("access_token", jwtService.generer(utilisateur))))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(resultat.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(c -> c.startsWith("access_token=") && c.contains("Max-Age=0"));
    }

    @Test
    void cors_autoriseLeFrontendLocalAvecCookies() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://site-malveillant.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }


    // ---------------------------------------------------------------- limitation des tentatives

    private org.springframework.test.web.servlet.ResultActions connexionDepuis(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(requete -> {
                    requete.setRemoteAddr(ip);
                    return requete;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"mauvais-mot-de-passe\"}"));
    }

    @Test
    void cinqEchecs_laSixiemeTentativeRenvoie429AvecRetryAfter_sansVerifierLeMotDePasse() throws Exception {
        doThrow(new IdentifiantsInvalidesException()).when(authService).connecter(any());
        for (int i = 0; i < LimiteurConnexion.MAX_ECHECS_EMAIL; i++) {
            connexionDepuis("192.0.2.50", "limite@exemple.com").andExpect(status().isUnauthorized());
        }

        connexionDepuis("192.0.2.50", "LIMITE@exemple.com ")
                .andExpect(status().isTooManyRequests())
                // Délai jusqu'à la sortie du premier échec de la fenêtre de 15 min (horloge réelle : 899 ou 900 s).
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, org.hamcrest.Matchers.matchesPattern("89[0-9]|900")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value(
                        "Trop de tentatives de connexion échouées. Réessayez dans 15 minutes."));

        // La 6e tentative est refusée avant l'appel au service (pas de calcul BCrypt).
        org.mockito.Mockito.verify(authService, org.mockito.Mockito.times(LimiteurConnexion.MAX_ECHECS_EMAIL))
                .connecter(any());
    }

    @Test
    void connexionReussie_remetLeCompteurEmailAZero() throws Exception {
        doThrow(new IdentifiantsInvalidesException()).when(authService).connecter(any());
        for (int i = 0; i < LimiteurConnexion.MAX_ECHECS_EMAIL - 1; i++) {
            connexionDepuis("192.0.2.60", "reprise@exemple.com").andExpect(status().isUnauthorized());
        }
        doReturn(new AuthService.ResultatConnexion(dto, jwtService.generer(utilisateur)))
                .when(authService).connecter(any());
        connexionDepuis("192.0.2.60", "reprise@exemple.com").andExpect(status().isOk());

        doThrow(new IdentifiantsInvalidesException()).when(authService).connecter(any());
        for (int i = 0; i < LimiteurConnexion.MAX_ECHECS_EMAIL; i++) {
            connexionDepuis("192.0.2.61", "reprise@exemple.com").andExpect(status().isUnauthorized());
        }
        connexionDepuis("192.0.2.61", "reprise@exemple.com").andExpect(status().isTooManyRequests());
    }

    @Test
    void enTeteXForwardedFor_ignore_parDefaut() throws Exception {
        doThrow(new IdentifiantsInvalidesException()).when(authService).connecter(any());
        // Un client qui change X-Forwarded-For à chaque essai reste compté sur son adresse réelle.
        for (int i = 0; i < LimiteurConnexion.MAX_ECHECS_IP; i++) {
            final int numero = i;
            mockMvc.perform(post("/api/auth/login")
                            .with(requete -> {
                                requete.setRemoteAddr("192.0.2.70");
                                return requete;
                            })
                            .header("X-Forwarded-For", "10.9.8." + numero)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"spray" + numero + "@exemple.com\",\"password\":\"x\"}"))
                    .andExpect(status().isUnauthorized());
        }
        connexionDepuis("192.0.2.70", "autre@exemple.com").andExpect(status().isTooManyRequests());
    }

    private Cookie cookieJwt() {
        return new Cookie(CookieAuthentification.NOM, jwtService.generer(utilisateur));
    }
}
