package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.config.ExecuteurOrganisation;
import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.historique.EvenementHistoriqueResponse;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.CookieAuthentification;
import com.gestionsuivis.backend.security.JwtService;
import com.gestionsuivis.backend.security.LimiteurConnexion;
import com.gestionsuivis.backend.security.SecurityConfig;
import com.gestionsuivis.backend.security.VerificateurGoogle;
import com.gestionsuivis.backend.service.ActiviteService;
import com.gestionsuivis.backend.service.AuthService;
import com.gestionsuivis.backend.service.HistoriqueService;
import com.gestionsuivis.backend.service.ProjetService;
import com.gestionsuivis.backend.service.UtilisateurService;
import com.gestionsuivis.backend.support.Donnees;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrôleurs métier avec la vraie chaîne de sécurité (cookie JWT, CSRF, réponses d'erreur JSON),
 * services simulés : authentification exigée, traduction des refus du service, validation,
 * pagination de l'historique. Sans serveur ni base de données.
 */
@WebMvcTest(controllers = {AuthController.class, ProjetController.class, ActiviteController.class,
        UtilisateurController.class})
@Import({SecurityConfig.class, JwtService.class, VerificateurGoogle.class, LimiteurConnexion.class,
        ControleursHttpTest.Configuration.class})
class ControleursHttpTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class Configuration {
        @org.springframework.context.annotation.Bean
        ExecuteurOrganisation executeurOrganisation() {
            return new ExecuteurOrganisation(org.mockito.Mockito.mock(
                    org.springframework.transaction.PlatformTransactionManager.class));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UtilisateurRepository utilisateurs;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private ProjetService projetService;
    @MockitoBean
    private ActiviteService activiteService;
    @MockitoBean
    private UtilisateurService utilisateurService;
    @MockitoBean
    private HistoriqueService historiqueService;

    private Utilisateur utilisateur;
    private final UUID projetId = UUID.randomUUID();

    @BeforeEach
    void initialiser() {
        utilisateur = Donnees.utilisateur("Manager", Role.MANAGER);
        when(utilisateurs.findById(utilisateur.getId())).thenReturn(Optional.of(utilisateur));
    }

    private Cookie session() {
        return new Cookie(CookieAuthentification.NOM, jwtService.generer(utilisateur));
    }

    private Cookie[] sessionEtCsrf() throws Exception {
        Cookie xsrf = mockMvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return new Cookie[]{session(), xsrf};
    }

    private static PageResponse<EvenementHistoriqueResponse> unePage() {
        EvenementHistoriqueResponse e = new EvenementHistoriqueResponse(UUID.randomUUID(), "project.updated", "project",
                UUID.randomUUID(), "Projet modifié : statut (Archivé)",
                new EvenementHistoriqueResponse.Auteur(UUID.randomUUID(), "Alice"), List.of("status"), "archived",
                Instant.parse("2026-11-12T09:00:00Z"));
        return PageResponse.de(new PageImpl<>(List.of(e), PageRequest.of(0, 20), 1));
    }

    // ---------------------------------------------------------------- authentification

    @Test
    void sansSession_historiqueEtListes_401() throws Exception {
        mockMvc.perform(get("/api/projects/" + projetId + "/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/activities")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        verify(historiqueService, never()).projet(any(), any());
    }

    @Test
    void sessionDUnCompteDesactive_401() throws Exception {
        utilisateur.setActif(false);
        mockMvc.perform(get("/api/projects/" + projetId + "/history").cookie(session()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- historique

    @Test
    void historiqueDuProjet_paginationTransmiseEtReponseSansEmail() throws Exception {
        when(historiqueService.projet(eq(projetId), any())).thenReturn(unePage());

        mockMvc.perform(get("/api/projects/" + projetId + "/history?page=2&perPage=5").cookie(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].description").value("Projet modifié : statut (Archivé)"))
                .andExpect(jsonPath("$.data[0].author.name").value("Alice"))
                .andExpect(jsonPath("$.data[0].author.email").doesNotExist())
                .andExpect(jsonPath("$.data[0].fields[0]").value("status"))
                .andExpect(jsonPath("$.meta.total").value(1));

        ArgumentCaptor<Pageable> pagination = ArgumentCaptor.forClass(Pageable.class);
        verify(historiqueService).projet(eq(projetId), pagination.capture());
        assertThat(pagination.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pagination.getValue().getPageSize()).isEqualTo(5);
        assertThat(pagination.getValue().getSort()).isEqualTo(HistoriqueService.TRI);
    }

    @Test
    void historique_taillePlafonneeA100() throws Exception {
        UUID activite = UUID.randomUUID();
        when(historiqueService.activite(eq(activite), any())).thenReturn(unePage());

        mockMvc.perform(get("/api/activities/" + activite + "/history?perPage=5000").cookie(session()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pagination = ArgumentCaptor.forClass(Pageable.class);
        verify(historiqueService).activite(eq(activite), pagination.capture());
        assertThat(pagination.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void historique_refusParLeService_403Json() throws Exception {
        UUID cible = UUID.randomUUID();
        when(historiqueService.utilisateur(eq(cible), any()))
                .thenThrow(new AccesInterditException("L'historique d'un compte est réservé aux administrateurs."));

        mockMvc.perform(get("/api/users/" + cible + "/history").cookie(session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("L'historique d'un compte est réservé aux administrateurs."));
    }

    @Test
    void historique_elementInexistant_404() throws Exception {
        when(historiqueService.projet(eq(projetId), any()))
                .thenThrow(new RessourceIntrouvableException("Projet", projetId));
        mockMvc.perform(get("/api/projects/" + projetId + "/history").cookie(session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void identifiantInvalide_400SansAppelerLeService() throws Exception {
        mockMvc.perform(get("/api/projects/pas-un-uuid/history").cookie(session()))
                .andExpect(status().isBadRequest());
        verify(historiqueService, never()).projet(any(), any());
    }

    // ---------------------------------------------------------------- modifications (CSRF, droits, validation)

    @Test
    void modificationDeProjet_sansJetonCsrf_403_etServiceNonAppele() throws Exception {
        mockMvc.perform(patch("/api/projects/" + projetId).cookie(session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nouveau\"}"))
                .andExpect(status().isForbidden());
        verify(projetService, never()).modifier(any(), any());
    }

    @Test
    void modificationDeProjet_avecJetonCsrf_transmiseAuService() throws Exception {
        Cookie[] cookies = sessionEtCsrf();
        mockMvc.perform(patch("/api/projects/" + projetId).cookie(cookies)
                        .header("X-XSRF-TOKEN", cookies[1].getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"archived\"}"))
                .andExpect(status().isOk());
        verify(projetService).modifier(eq(projetId), any());
    }

    @Test
    void modificationDeProjet_refuseeParLeService_403() throws Exception {
        when(projetService.modifier(eq(projetId), any()))
                .thenThrow(new AccesInterditException("Seul un administrateur peut changer le chef de projet."));
        Cookie[] cookies = sessionEtCsrf();
        mockMvc.perform(patch("/api/projects/" + projetId).cookie(cookies)
                        .header("X-XSRF-TOKEN", cookies[1].getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seul un administrateur peut changer le chef de projet."));
    }

    @Test
    void creationDUtilisateur_invalide_422AvecLesChampsEnErreur() throws Exception {
        Cookie[] cookies = sessionEtCsrf();
        mockMvc.perform(post("/api/users").cookie(cookies)
                        .header("X-XSRF-TOKEN", cookies[1].getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"pas-un-email\",\"role\":\"member\",\"password\":\"court\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
        verify(utilisateurService, never()).creer(any());
    }

    @Test
    void creationDUtilisateur_valide_201() throws Exception {
        Cookie[] cookies = sessionEtCsrf();
        mockMvc.perform(post("/api/users").cookie(cookies)
                        .header("X-XSRF-TOKEN", cookies[1].getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nouveau\",\"email\":\"n@exemple.com\",\"role\":\"member\",\"password\":\"MotDePasse123\"}"))
                .andExpect(status().isCreated());
        verify(utilisateurService).creer(any());
    }

    // ---------------------------------------------------------------- conflits de verrou (sous-lot 4.1)

    @Test
    void verrouIndisponible_503AvecRetryAfterEtFormatErreurResponse() throws Exception {
        when(projetService.modifier(eq(projetId), any())).thenThrow(new CannotAcquireLockException(
                "could not obtain lock on row in relation \"projects\"",
                new java.sql.SQLException("canceling statement due to lock timeout", "55P03")));
        Cookie[] cookies = sessionEtCsrf();

        String corps = mockMvc.perform(patch("/api/projects/" + projetId).cookie(cookies)
                        .header("X-XSRF-TOKEN", cookies[1].getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nouveau\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Le service est temporairement occupé. Réessayez dans quelques instants."))
                .andExpect(jsonPath("$.path").value("/api/projects/" + projetId))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(corps).doesNotContain("relation").doesNotContain("lock").doesNotContain("55P03")
                .doesNotContain("Exception");
    }

    @Test
    void ressourceModifieeEntreTemps_409SansRetryAfter() throws Exception {
        when(projetService.modifier(eq(projetId), any())).thenThrow(new ObjectOptimisticLockingFailureException(
                "com.gestionsuivis.backend.entity.Projet", projetId));
        Cookie[] cookies = sessionEtCsrf();

        mockMvc.perform(patch("/api/projects/" + projetId).cookie(cookies)
                        .header("X-XSRF-TOKEN", cookies[1].getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nouveau\"}"))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Cette ressource a été modifiée par quelqu'un d'autre. Rechargez-la avant de réessayer."));
    }
}
