package com.gestionsuivis.backend.service.ia;

import com.gestionsuivis.backend.config.ServiceIaClientConfig;
import com.gestionsuivis.backend.config.ServiceIaProperties;
import com.gestionsuivis.backend.dto.ia.ChatIaReponse;
import com.gestionsuivis.backend.dto.ia.ChatIaRequete;
import com.gestionsuivis.backend.dto.ia.MessageIa;
import com.gestionsuivis.backend.dto.ia.RoleMessageIa;
import com.gestionsuivis.backend.dto.ia.SanteServiceIa;
import com.gestionsuivis.backend.exception.ServiceIaException;
import com.gestionsuivis.backend.exception.TypeErreurServiceIa;
import com.gestionsuivis.backend.support.CaptureLogs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests du client IA avec un serveur HTTP simulé ({@link MockRestServiceServer}) :
 * aucun FastAPI, aucun contexte Spring, aucune base de données.
 * Les formats JSON reproduisent ceux de {@code ai/app/models/schemas.py} et
 * {@code ai/app/core/error_handlers.py} (branche origin/develop).
 */
class ServiceIaClientTest {

    private static final String URL = "http://ia.test";
    private static final String CLE = "cle-interne-de-test-1234567890";
    private static final String QUESTION = "Quels projets du département Finance sont en retard ?";
    private static final String MESSAGE_GEMINI_BRUT = "Requête invalide envoyée au LLM : 400 INVALID_ARGUMENT secret-gemini";

    private static final String REPONSE_CHAT = """
            {
              "message": { "role": "assistant", "content": "Deux projets sont en retard." },
              "conversation_id": "c-1",
              "usage": { "prompt_tokens": 103, "completion_tokens": 40, "total_tokens": 517 },
              "model": "gemini-3.8-flash",
              "created_at": "2026-10-09T11:31:24",
              "champ_inconnu": true
            }
            """;

    private MockRestServiceServer serveur;
    private ServiceIaClient client;
    private CaptureLogs logs;

    @BeforeEach
    void initialiser() {
        ServiceIaProperties proprietes = new ServiceIaProperties(URL, CLE,
                Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofSeconds(3));
        RestClient.Builder builder = ServiceIaClientConfig.preparerBuilder(proprietes);
        serveur = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new ServiceIaClient(proprietes, restClient, restClient);
        logs = CaptureLogs.de(ServiceIaClient.class);
    }

    @AfterEach
    void verifierLogsEtFermer() {
        try {
            String journal = logs.tout();
            assertThat(journal).as("la clé interne ne doit jamais être journalisée").doesNotContain(CLE);
            assertThat(journal).as("le contenu des messages ne doit jamais être journalisé")
                    .doesNotContain(QUESTION).doesNotContain("Deux projets sont en retard");
            assertThat(journal).as("le message brut de FastAPI/Gemini ne doit pas être journalisé")
                    .doesNotContain("secret-gemini");
        } finally {
            logs.close();
        }
    }

    // ---------------------------------------------------------------- chat : succès et JSON

    @Test
    void chat_envoieLeJsonExactAvecLaCleInterne() {
        serveur.expect(requestTo(URL + "/api/ai/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(ServiceIaClient.ENTETE_CLE_INTERNE, CLE))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"messages\":["
                        + "{\"role\":\"user\",\"content\":\"Bonjour\"},"
                        + "{\"role\":\"assistant\",\"content\":\"Bonjour, que puis-je faire ?\"},"
                        + "{\"role\":\"user\",\"content\":\"" + QUESTION + "\"}],"
                        + "\"user_id\":\"u-42\",\"conversation_id\":\"c-1\",\"temperature\":0.3}"))
                .andRespond(withSuccess(REPONSE_CHAT, MediaType.APPLICATION_JSON));

        client.envoyerChat(new ChatIaRequete(List.of(
                MessageIa.utilisateur("Bonjour"),
                MessageIa.assistant("Bonjour, que puis-je faire ?"),
                MessageIa.utilisateur(QUESTION)), "u-42", "c-1", 0.3));

        serveur.verify();
    }

    @Test
    void chat_nEnvoiePasLesChampsAbsentsNiLeChampStream() {
        serveur.expect(requestTo(URL + "/api/ai/chat"))
                .andExpect(content().string("{\"messages\":[{\"role\":\"user\",\"content\":\"" + QUESTION + "\"}]}"))
                .andRespond(withSuccess(REPONSE_CHAT, MediaType.APPLICATION_JSON));

        client.envoyerChat(ChatIaRequete.de(List.of(MessageIa.utilisateur(QUESTION)), null, null));

        serveur.verify();
    }

    @Test
    void chat_litLaReponseAuFormatFastApi() {
        serveur.expect(requestTo(URL + "/api/ai/chat"))
                .andRespond(withSuccess(REPONSE_CHAT, MediaType.APPLICATION_JSON));

        ChatIaReponse reponse = client.envoyerChat(requeteSimple());

        assertThat(reponse.message()).isEqualTo(new MessageIa(RoleMessageIa.ASSISTANT, "Deux projets sont en retard."));
        assertThat(reponse.identifiantConversation()).isEqualTo("c-1");
        assertThat(reponse.usage().jetonsPrompt()).isEqualTo(103);
        assertThat(reponse.usage().jetonsReponse()).isEqualTo(40);
        assertThat(reponse.usage().jetonsTotal()).isEqualTo(517);
        assertThat(reponse.modele()).isEqualTo("gemini-3.8-flash");
        assertThat(reponse.horodatageUtc())
                .contains(OffsetDateTime.of(2026, 10, 9, 11, 31, 24, 0, ZoneOffset.UTC));
    }

    @Test
    void horodatage_accepteAussiUneDateAvecFuseauEtToléreUneValeurIllisible() {
        assertThat(new ChatIaReponse(null, null, null, null, "2026-10-09T14:31:24+03:00").horodatageUtc())
                .contains(OffsetDateTime.of(2026, 10, 9, 14, 31, 24, 0, ZoneOffset.ofHours(3)));
        assertThat(new ChatIaReponse(null, null, null, null, "hier").horodatageUtc()).isEmpty();
        assertThat(new ChatIaReponse(null, null, null, null, null).horodatageUtc()).isEmpty();
    }

    // ---------------------------------------------------------------- chat : validation avant envoi

    @Test
    void requetesInvalides_sontRefuseesSansAppelReseau() {
        List<MessageIa> cinquanteEtUn = new ArrayList<>(Collections.nCopies(51, MessageIa.utilisateur("x")));
        List<ChatIaRequete> invalides = List.of(
                ChatIaRequete.de(List.of(), null, null),
                ChatIaRequete.de(cinquanteEtUn, null, null),
                ChatIaRequete.de(List.of(MessageIa.utilisateur("   ")), null, null),
                ChatIaRequete.de(List.of(new MessageIa(null, "sans rôle")), null, null),
                ChatIaRequete.de(List.of(MessageIa.utilisateur("a".repeat(32_001))), null, null),
                ChatIaRequete.de(List.of(MessageIa.utilisateur("ok")), "u".repeat(65), null),
                ChatIaRequete.de(List.of(MessageIa.utilisateur("ok")), null, "c".repeat(65)),
                new ChatIaRequete(List.of(MessageIa.utilisateur("ok")), null, null, 2.5));

        for (ChatIaRequete requete : invalides) {
            ServiceIaException erreur = catchThrowableOfType(ServiceIaException.class, () -> client.envoyerChat(requete));
            assertThat(erreur).isNotNull();
            assertThat(erreur.getType()).isEqualTo(TypeErreurServiceIa.REQUETE_REFUSEE);
        }
        serveur.verify(); // aucune requête attendue, aucune reçue
    }

    @Test
    void limitesExactes_sontAcceptees() {
        serveur.expect(requestTo(URL + "/api/ai/chat"))
                .andRespond(withSuccess(REPONSE_CHAT, MediaType.APPLICATION_JSON));

        client.envoyerChat(new ChatIaRequete(
                new ArrayList<>(Collections.nCopies(50, MessageIa.utilisateur("a".repeat(32_000)))),
                "u".repeat(64), "c".repeat(64), 2.0));

        serveur.verify();
    }

    // ---------------------------------------------------------------- chat : erreurs FastAPI

    @Test
    void cleInterneRefusee_401FormatDetail_donneIndisponible() {
        ServiceIaException erreur = erreurPour(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"Clé API interne invalide.\"}"));

        verifierErreur(erreur, TypeErreurServiceIa.INDISPONIBLE, 503, 401);
        assertThat(erreur.getCodeDistant()).isEmpty();
        assertThat(logs.tout()).contains("clé interne refusée").contains(ServiceIaProperties.VAR_CLE);
    }

    @Test
    void cleGeminiInvalide_401FormatError_donneIndisponible() {
        verifierErreur(erreurPour(erreurIa(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Clé API Gemini invalide ou expirée.")),
                TypeErreurServiceIa.INDISPONIBLE, 503, 401);
    }

    @Test
    void quotaGemini_402_donneIndisponible() {
        verifierErreur(erreurPour(erreurIa(HttpStatus.PAYMENT_REQUIRED, "LLM_QUOTA_EXCEEDED", "Quota Gemini dépassé.")),
                TypeErreurServiceIa.INDISPONIBLE, 503, 402);
    }

    @Test
    void injectionDePrompt_400_donneRequeteRefusee() {
        ServiceIaException erreur = erreurPour(erreurIa(HttpStatus.BAD_REQUEST, "PROMPT_INJECTION_DETECTED",
                "Votre message contient des instructions non autorisees."));

        verifierErreur(erreur, TypeErreurServiceIa.REQUETE_REFUSEE, 422, 400);
        assertThat(erreur.getCodeDistant()).contains("PROMPT_INJECTION_DETECTED");
    }

    @Test
    void autre400_donneReponseInvalide() {
        verifierErreur(erreurPour(erreurIa(HttpStatus.BAD_REQUEST, "AUTRE_CODE", "x")),
                TypeErreurServiceIa.REPONSE_INVALIDE, 502, 400);
    }

    @Test
    void validationDuServiceIa_422VALIDATION_ERROR_donneRequeteRefusee() {
        verifierErreur(erreurPour(erreurIa(HttpStatus.valueOf(422), "VALIDATION_ERROR", "Message trop long (max 8000 caracteres).")),
                TypeErreurServiceIa.REQUETE_REFUSEE, 422, 422);
    }

    @Test
    void validationFastApi_422FormatDetail_donneReponseInvalideSansReprendreLesDonnees() {
        ServiceIaException erreur = erreurPour(withStatus(HttpStatus.valueOf(422))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":[{\"type\":\"string_too_long\",\"loc\":[\"body\",\"messages\",0,\"content\"],"
                        + "\"msg\":\"String should have at most 32000 characters\",\"input\":\"" + QUESTION + "\"}]}"));

        verifierErreur(erreur, TypeErreurServiceIa.REPONSE_INVALIDE, 502, 422);
    }

    @Test
    void limiteDeRequetes_429_donneSurchargeAvecRetryAfter() {
        ServiceIaException erreur = erreurPour(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "60")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"Trop de requetes. Veuillez patienter une minute.\"}"));

        verifierErreur(erreur, TypeErreurServiceIa.SURCHARGE, 429, 429);
        assertThat(erreur.getDelaiAvantNouvelEssai()).contains(Duration.ofSeconds(60));
    }

    @Test
    void erreurGemini_502_donneIndisponibleSansReprendreLeMessageBrut() {
        ServiceIaException erreur = erreurPour(erreurIa(HttpStatus.BAD_GATEWAY, "LLM_ERROR", MESSAGE_GEMINI_BRUT));

        verifierErreur(erreur, TypeErreurServiceIa.INDISPONIBLE, 503, 502);
        assertThat(erreur.getCodeDistant()).contains("LLM_ERROR");
    }

    @Test
    void erreurInterneFastApi_500NonJson_donneIndisponible() {
        verifierErreur(erreurPour(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN).body("Internal Server Error")),
                TypeErreurServiceIa.INDISPONIBLE, 503, 500);
    }

    @Test
    void routeIntrouvable_404_donneReponseInvalide() {
        verifierErreur(erreurPour(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"detail\":\"Not Found\"}")),
                TypeErreurServiceIa.REPONSE_INVALIDE, 502, 404);
    }

    @Test
    void codeErreurMalForme_nestPasConserve() {
        ServiceIaException erreur = erreurPour(erreurIa(HttpStatus.BAD_GATEWAY, "code\ninjecté dans les logs", "x"));

        assertThat(erreur.getCodeDistant()).isEmpty();
        assertThat(logs.tout()).doesNotContain("injecté");
    }

    @Test
    void reponse200Illisible_donneReponseInvalide() {
        serveur.expect(requestTo(URL + "/api/ai/chat"))
                .andRespond(withSuccess("{ pas du json " + QUESTION, MediaType.APPLICATION_JSON));

        ServiceIaException erreur = catchThrowableOfType(ServiceIaException.class, () -> client.envoyerChat(requeteSimple()));

        assertThat(erreur.getType()).isEqualTo(TypeErreurServiceIa.REPONSE_INVALIDE);
    }

    @Test
    void reponse200SansMessageAssistant_donneReponseInvalide() {
        for (String corps : List.of("{\"model\":\"gemini\"}",
                "{\"message\":{\"role\":\"user\",\"content\":\"écho\"},\"model\":\"gemini\"}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"  \"},\"model\":\"gemini\"}")) {
            serveur.reset();
            serveur.expect(requestTo(URL + "/api/ai/chat"))
                    .andRespond(withSuccess(corps, MediaType.APPLICATION_JSON));

            ServiceIaException erreur = catchThrowableOfType(ServiceIaException.class, () -> client.envoyerChat(requeteSimple()));

            assertThat(erreur.getType()).as(corps).isEqualTo(TypeErreurServiceIa.REPONSE_INVALIDE);
        }
    }

    @Test
    void aucuneNouvelleTentative_apresUneErreur() {
        serveur.expect(requestTo(URL + "/api/ai/chat"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        catchThrowableOfType(ServiceIaException.class, () -> client.envoyerChat(requeteSimple()));

        serveur.verify(); // une seule requête attendue : une deuxième ferait échouer le test
    }

    @Test
    void messageDeLException_estToujoursGeneriqueEtSansCle() {
        ServiceIaException erreur = erreurPour(erreurIa(HttpStatus.BAD_GATEWAY, "LLM_ERROR", MESSAGE_GEMINI_BRUT));

        assertThat(erreur.getMessage()).isEqualTo(TypeErreurServiceIa.INDISPONIBLE.messageUtilisateur());
        assertThat(erreur.toString()).doesNotContain(CLE).doesNotContain("secret-gemini");
    }

    // ---------------------------------------------------------------- analyse de risques et rapport

    @Test
    void analyseRisques_envoieLeFormatAnalyzeRisksRequestEtLitLaReponse() {
        serveur.expect(requestTo(URL + "/api/ai/analyze-risks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(ServiceIaClient.ENTETE_CLE_INTERNE, CLE))
                .andExpect(content().string("{\"project_name\":\"Budget\",\"activities\":[{\"title\":\"Saisie\","
                        + "\"status\":\"in_progress\",\"due_date\":\"2025-10-20\"}],\"language\":\"fr\"}"))
                .andRespond(withSuccess("{\"overall_risk\":\"high\",\"risks\":[{\"level\":\"high\",\"title\":\"Retard\","
                        + "\"description\":\"d\",\"recommendation\":\"r\"}],\"summary\":\"s\",\"model\":\"gemini\","
                        + "\"created_at\":\"2026-11-01T10:00:00\"}", MediaType.APPLICATION_JSON));

        java.util.Map<String, Object> ligne = new java.util.LinkedHashMap<>();
        ligne.put("title", "Saisie");
        ligne.put("status", "in_progress");
        ligne.put("due_date", "2025-10-20");
        var reponse = client.analyserRisques(new com.gestionsuivis.backend.dto.ia.AnalyseRisquesIaRequete(
                "Budget", null, List.of(ligne), "fr"));

        assertThat(reponse.risqueGlobal()).isEqualTo("high");
        assertThat(reponse.risks()).hasSize(1);
        serveur.verify();
    }

    @Test
    void analyseRisques_niveauInconnu_donneReponseInvalide() {
        serveur.expect(requestTo(URL + "/api/ai/analyze-risks"))
                .andRespond(withSuccess("{\"overall_risk\":\"extreme\",\"risks\":[],\"summary\":\"s\",\"model\":\"g\"}",
                        MediaType.APPLICATION_JSON));

        ServiceIaException erreur = catchThrowableOfType(ServiceIaException.class, () -> client.analyserRisques(
                new com.gestionsuivis.backend.dto.ia.AnalyseRisquesIaRequete("Budget", null, List.of(), "fr")));

        assertThat(erreur.getType()).isEqualTo(TypeErreurServiceIa.REPONSE_INVALIDE);
    }

    @Test
    void rapport_envoieLeFormatGenerateReportRequest() {
        serveur.expect(requestTo(URL + "/api/ai/generate-report"))
                .andExpect(content().string("{\"report_type\":\"weekly\",\"data\":{\"completionRate\":75.0},\"language\":\"fr\"}"))
                .andRespond(withSuccess("{\"title\":\"Rapport\",\"content\":\"Contenu\",\"sections\":{\"a\":\"b\"},"
                        + "\"model\":\"gemini\"}", MediaType.APPLICATION_JSON));

        var reponse = client.genererRapport(new com.gestionsuivis.backend.dto.ia.RapportIaRequete("weekly",
                java.util.Map.of("completionRate", 75.0), "fr"));

        assertThat(reponse.title()).isEqualTo("Rapport");
        assertThat(reponse.sections()).containsEntry("a", "b");
        serveur.verify();
    }

    @Test
    void rapport_typeInconnu_refuseSansAppel() {
        ServiceIaException erreur = catchThrowableOfType(ServiceIaException.class, () -> client.genererRapport(
                new com.gestionsuivis.backend.dto.ia.RapportIaRequete("daily", java.util.Map.of(), "fr")));

        assertThat(erreur.getType()).isEqualTo(TypeErreurServiceIa.REQUETE_REFUSEE);
        serveur.verify();
    }

    // ---------------------------------------------------------------- santé

    @Test
    void sante_serviceDisponible() {
        serveur.expect(requestTo(URL + "/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"ok\",\"service\":\"ai-service\",\"version\":\"0.1.0\","
                        + "\"environment\":\"development\",\"timestamp\":\"2026-11-01T10:15:30.123456+00:00\"}",
                        MediaType.APPLICATION_JSON));

        SanteServiceIa sante = client.verifierSante();

        assertThat(sante.disponible()).isTrue();
        assertThat(sante.statut()).isEqualTo("ok");
        assertThat(sante.version()).isEqualTo("0.1.0");
        assertThat(sante.environnement()).isEqualTo("development");
        assertThat(sante.latenceMs()).isNotNull().isGreaterThanOrEqualTo(0);
        serveur.verify();
    }

    @Test
    void sante_statutDifferentDeOk_estIndisponible() {
        serveur.expect(requestTo(URL + "/health"))
                .andRespond(withSuccess("{\"status\":\"degraded\"}", MediaType.APPLICATION_JSON));

        assertThat(client.verifierSante().disponible()).isFalse();
    }

    @Test
    void sante_erreurHttp_estIndisponibleSansException() {
        serveur.expect(requestTo(URL + "/health"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        SanteServiceIa sante = client.verifierSante();

        assertThat(sante.disponible()).isFalse();
        assertThat(sante.statut()).isEqualTo("HTTP_500");
    }

    @Test
    void sante_reponseNonJson_estIndisponibleSansException() {
        serveur.expect(requestTo(URL + "/health"))
                .andRespond(withSuccess("<html>proxy</html>", MediaType.TEXT_HTML));

        SanteServiceIa sante = client.verifierSante();

        assertThat(sante.disponible()).isFalse();
        assertThat(sante.statut()).isEqualTo(SanteServiceIa.STATUT_INJOIGNABLE);
    }

    // ---------------------------------------------------------------- utilitaires

    private ServiceIaException erreurPour(ResponseCreator reponse) {
        serveur.expect(requestTo(URL + "/api/ai/chat")).andRespond(reponse);
        ServiceIaException erreur = catchThrowableOfType(ServiceIaException.class,
                () -> client.envoyerChat(requeteSimple()));
        serveur.verify();
        assertThat(erreur).isNotNull();
        return erreur;
    }

    private static ResponseCreator erreurIa(HttpStatus statut, String code, String message) {
        String codeJson = code.replace("\n", "\\n");
        return withStatus(statut).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"" + codeJson + "\",\"message\":\"" + message + "\",\"details\":{}}}");
    }

    private static void verifierErreur(ServiceIaException erreur, TypeErreurServiceIa type,
                                       int statutSuggere, int statutDistant) {
        assertThat(erreur.getType()).isEqualTo(type);
        assertThat(erreur.getStatutHttpSuggere().value()).isEqualTo(statutSuggere);
        assertThat(erreur.getStatutDistant()).contains(statutDistant);
        assertThat(erreur.getMessage()).isEqualTo(type.messageUtilisateur())
                .doesNotContain("secret-gemini").doesNotContain(CLE).doesNotContain(QUESTION);
    }

    private static ChatIaRequete requeteSimple() {
        return ChatIaRequete.de(List.of(MessageIa.utilisateur(QUESTION)), "u-42", "c-1");
    }
}
