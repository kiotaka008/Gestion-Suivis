package com.gestionsuivis.backend.security;

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
@Import({SecurityConfig.class, JwtService.class})
class SecuriteHttpTest {

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
        utilisateur = Donnees.utilisateur("Rickael", Role.ADMIN, null);
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

    private Cookie cookieJwt() {
        return new Cookie(CookieAuthentification.NOM, jwtService.generer(utilisateur));
    }
}
