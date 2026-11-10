package com.gestionsuivis.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortsSecondairesConfigTest {

    @Test
    void parDefaut_8080_enPlusDu_3000() {
        assertThat(PortsSecondairesConfig.lire(PortsSecondairesConfig.DEFAUT, 3000)).containsExactly(8080);
    }

    @Test
    void portIdentiqueAuPrincipal_ignore() {
        assertThat(PortsSecondairesConfig.lire("8080", 8080)).isEmpty();
    }

    @Test
    void valeurVide_desactiveLesPortsSupplementaires() {
        assertThat(PortsSecondairesConfig.lire("", 3000)).isEmpty();
        assertThat(PortsSecondairesConfig.lire(null, 3000)).isEmpty();
    }

    @Test
    void liste_dedoublonnee_etValeursInvalidesIgnorees() {
        assertThat(PortsSecondairesConfig.lire(" 8080, abc, 8080, 0, 70000, 9090 ,", 3000))
                .containsExactly(8080, 9090);
    }
}
