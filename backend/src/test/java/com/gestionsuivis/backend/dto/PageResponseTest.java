package com.gestionsuivis.backend.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link PageResponse} : format {@code PaginatedResponse<T>} du frontend
 * ({@code { data, meta: { page, perPage, total, totalPages } }}, page à partir de 1).
 */
class PageResponseTest {

    @Test
    void de_convertitLaPageSpringEnPageCommencantA1() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> reponse = PageResponse.de(page);

        assertThat(reponse.data()).containsExactly("a", "b");
        assertThat(reponse.meta()).isEqualTo(new PageResponse.Meta(2, 2, 5, 3));
    }

    @Test
    void de_avecConversion_appliqueLeMapper() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 3), 3);

        PageResponse<String> reponse = PageResponse.de(page, nombre -> "n" + nombre);

        assertThat(reponse.data()).containsExactly("n1", "n2", "n3");
        assertThat(reponse.meta().page()).isEqualTo(1);
        assertThat(reponse.meta().totalPages()).isEqualTo(1);
    }

    @Test
    void pageVide_renvoieUnTableauVide() {
        PageResponse<String> reponse = PageResponse.de(Page.empty(PageRequest.of(0, 20)));

        assertThat(reponse.data()).isEmpty();
        assertThat(reponse.meta()).isEqualTo(new PageResponse.Meta(1, 20, 0, 0));
    }

    @Test
    void serialisationJson_identiqueAuTypeFrontend() {
        Page<String> page = new PageImpl<>(List.of("a"), PageRequest.of(0, 20), 1);

        String json = JsonMapper.builder().build().writeValueAsString(PageResponse.de(page));

        assertThat(json).isEqualTo("{\"data\":[\"a\"],\"meta\":{\"page\":1,\"perPage\":20,\"total\":1,\"totalPages\":1}}");
    }
}
