package com.gestionsuivis.backend.config;

import com.gestionsuivis.backend.dto.ia.ChatIaRequete;
import com.gestionsuivis.backend.dto.ia.MessageIa;
import com.gestionsuivis.backend.dto.ia.SanteServiceIa;
import com.gestionsuivis.backend.exception.ServiceIaException;
import com.gestionsuivis.backend.exception.TypeErreurServiceIa;
import com.gestionsuivis.backend.service.ia.ServiceIaClient;
import com.gestionsuivis.backend.support.CaptureLogs;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lecture des variables d'environnement du client IA, sans contexte Spring
 * (environnement simulé avec {@link MockEnvironment}).
 */
class ServiceIaPropertiesTest {

    private static final String CLE = "cle-interne-de-test-1234567890";

    @Test
    void configurationComplete_estValideEtUtiliseLesDelaisParDefaut() {
        ServiceIaProperties proprietes = ServiceIaProperties.depuis(new MockEnvironment()
                .withProperty(ServiceIaProperties.VAR_URL, "  http://localhost:8000/  ")
                .withProperty(ServiceIaProperties.VAR_CLE, CLE));

        assertThat(proprietes.estConfigure()).isTrue();
        assertThat(proprietes.urlService()).isEqualTo("http://localhost:8000");
        assertThat(proprietes.cleInterne()).isEqualTo(CLE);
        assertThat(proprietes.delaiConnexion()).isEqualTo(Duration.ofSeconds(5));
        assertThat(proprietes.delaiLectureChat()).isEqualTo(Duration.ofSeconds(60));
        assertThat(proprietes.delaiSante()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void variablesAbsentes_serviceNonConfigureSansErreur() {
        ServiceIaProperties proprietes = ServiceIaProperties.depuis(new MockEnvironment());

        assertThat(proprietes.estConfigure()).isFalse();
        assertThat(proprietes.urlService()).isNull();
        assertThat(proprietes.cleInterne()).isNull();
    }

    @Test
    void urlInvalide_estIgnoree() {
        for (String url : List.of("ftp://ia.local", "localhost:8000", "http://", "pas une url")) {
            ServiceIaProperties proprietes = ServiceIaProperties.depuis(new MockEnvironment()
                    .withProperty(ServiceIaProperties.VAR_URL, url)
                    .withProperty(ServiceIaProperties.VAR_CLE, CLE));

            assertThat(proprietes.urlService()).as(url).isNull();
            assertThat(proprietes.estConfigure()).as(url).isFalse();
        }
    }

    @Test
    void cleTropCourte_estIgnoreeSansApparaitreDansLesLogs() {
        try (CaptureLogs logs = CaptureLogs.de(ServiceIaProperties.class)) {
            ServiceIaProperties proprietes = ServiceIaProperties.depuis(new MockEnvironment()
                    .withProperty(ServiceIaProperties.VAR_URL, "http://localhost:8000")
                    .withProperty(ServiceIaProperties.VAR_CLE, "courte-123"));

            assertThat(proprietes.cleInterne()).isNull();
            assertThat(proprietes.estConfigure()).isFalse();
            assertThat(logs.tout()).contains(ServiceIaProperties.VAR_CLE).doesNotContain("courte-123");
        }
    }

    @Test
    void delais_sontConfigurablesParVariablesFacultatives() {
        ServiceIaProperties proprietes = ServiceIaProperties.depuis(new MockEnvironment()
                .withProperty(ServiceIaProperties.VAR_DELAI_CONNEXION, "10")
                .withProperty(ServiceIaProperties.VAR_DELAI_LECTURE_CHAT, " 120 ")
                .withProperty(ServiceIaProperties.VAR_DELAI_SANTE, "2"));

        assertThat(proprietes.delaiConnexion()).isEqualTo(Duration.ofSeconds(10));
        assertThat(proprietes.delaiLectureChat()).isEqualTo(Duration.ofSeconds(120));
        assertThat(proprietes.delaiSante()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void delaisInvalides_reviennentAuxValeursParDefaut() {
        ServiceIaProperties proprietes = ServiceIaProperties.depuis(new MockEnvironment()
                .withProperty(ServiceIaProperties.VAR_DELAI_CONNEXION, "abc")
                .withProperty(ServiceIaProperties.VAR_DELAI_LECTURE_CHAT, "0")
                .withProperty(ServiceIaProperties.VAR_DELAI_SANTE, "-3"));

        assertThat(proprietes.delaiConnexion()).isEqualTo(ServiceIaProperties.DELAI_CONNEXION_PAR_DEFAUT);
        assertThat(proprietes.delaiLectureChat()).isEqualTo(ServiceIaProperties.DELAI_LECTURE_CHAT_PAR_DEFAUT);
        assertThat(proprietes.delaiSante()).isEqualTo(ServiceIaProperties.DELAI_SANTE_PAR_DEFAUT);
    }

    @Test
    void toString_masqueLaCle() {
        ServiceIaProperties proprietes = new ServiceIaProperties("http://localhost:8000", CLE,
                Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofSeconds(3));

        assertThat(proprietes.toString()).doesNotContain(CLE).contains("cleInterne=***");
    }

    @Test
    void beanDeConfiguration_neJournaliseJamaisLaCle() {
        try (CaptureLogs logs = CaptureLogs.de(ServiceIaClientConfig.class)) {
            new ServiceIaClientConfig().serviceIaProperties(new MockEnvironment()
                    .withProperty(ServiceIaProperties.VAR_URL, "http://localhost:8000")
                    .withProperty(ServiceIaProperties.VAR_CLE, CLE));

            assertThat(logs.tout()).contains("http://localhost:8000").doesNotContain(CLE);
        }
    }

    @Test
    void sansConfiguration_leClientSeCreeEtSignaleLeServiceIndisponible() {
        ServiceIaClientConfig configuration = new ServiceIaClientConfig();
        ServiceIaClient client = configuration.serviceIaClient(
                configuration.serviceIaProperties(new MockEnvironment()));

        assertThat(client.estConfigure()).isFalse();
        assertThat(client.verifierSante()).isEqualTo(SanteServiceIa.nonConfigure());
        assertThatThrownBy(() -> client.envoyerChat(
                ChatIaRequete.de(List.of(MessageIa.utilisateur("Bonjour")), null, null)))
                .isInstanceOf(ServiceIaException.class)
                .satisfies(e -> assertThat(((ServiceIaException) e).getType())
                        .isEqualTo(TypeErreurServiceIa.INDISPONIBLE));
    }
}
