package com.gestionsuivis.backend.config;

import com.gestionsuivis.backend.exception.ParametreInvalideException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Règles de pagination alignées sur le frontend ({@code page} à partir de 1,
 * {@code perPage}, {@code sortBy}, {@code sortOrder}), sans contexte Spring.
 */
class PaginationConfigTest {

    private static final Map<String, String> TRIS = Map.of("name", "nom", "dueDate", "dateEcheance");
    private static final Sort DEFAUT = Sort.by("nom");

    @Test
    void sansParametre_premierePageEtTailleParDefaut() {
        Pageable pageable = PaginationConfig.creer(null, null, null, null, TRIS, DEFAUT);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(PaginationConfig.TAILLE_PAR_DEFAUT);
        assertThat(pageable.getSort()).isEqualTo(DEFAUT);
    }

    @Test
    void page1_estLaPremierePage_etPage3LaTroisieme() {
        assertThat(PaginationConfig.creer(1, 10, null, null, TRIS, DEFAUT).getPageNumber()).isZero();
        assertThat(PaginationConfig.creer(3, 10, null, null, TRIS, DEFAUT).getPageNumber()).isEqualTo(2);
        assertThat(PaginationConfig.creer(0, 10, null, null, TRIS, DEFAUT).getPageNumber()).isZero();
    }

    @Test
    void perPageSuperieurAuMaximum_estRameneA100() {
        assertThat(PaginationConfig.creer(1, 500, null, null, TRIS, DEFAUT).getPageSize())
                .isEqualTo(PaginationConfig.TAILLE_MAXIMALE);
        assertThat(PaginationConfig.creer(1, -2, null, null, TRIS, DEFAUT).getPageSize())
                .isEqualTo(PaginationConfig.TAILLE_PAR_DEFAUT);
    }

    @Test
    void sortByEtSortOrder_utilisentLaProprieteDeLEntite() {
        Sort tri = PaginationConfig.creer(1, 20, "dueDate", "desc", TRIS, DEFAUT).getSort();

        assertThat(tri.getOrderFor("dateEcheance")).isNotNull();
        assertThat(tri.getOrderFor("dateEcheance").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(PaginationConfig.creer(1, 20, "name", null, TRIS, DEFAUT).getSort().getOrderFor("nom").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void triNonAutorise_ouSensInvalide_donne400() {
        assertThatThrownBy(() -> PaginationConfig.creer(1, 20, "motDePasseHash", null, TRIS, DEFAUT))
                .isInstanceOf(ParametreInvalideException.class)
                .hasMessageContaining("motDePasseHash").hasMessageContaining("dueDate, name");
        assertThatThrownBy(() -> PaginationConfig.creer(1, 20, "name", "up", TRIS, DEFAUT))
                .isInstanceOf(ParametreInvalideException.class);
    }

    @Test
    void resolveurPageable_estAligneSurLeFrontend() throws Exception {
        PageableHandlerMethodArgumentResolver resolveur = new PageableHandlerMethodArgumentResolver();
        new PaginationConfig().pageableCustomizer().customize(resolveur);
        MethodParameter parametre = new MethodParameter(
                PaginationConfigTest.class.getDeclaredMethod("endpointFictif", Pageable.class), 0);
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.addParameter("page", "2");
        requete.addParameter("perPage", "500");

        Pageable pageable = resolveur.resolveArgument(parametre, null, new ServletWebRequest(requete), null);

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(PaginationConfig.TAILLE_MAXIMALE);
    }

    @SuppressWarnings("unused")
    private void endpointFictif(Pageable pageable) {
    }
}
