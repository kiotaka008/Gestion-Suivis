package com.gestionsuivis.backend.service.ia;

import com.gestionsuivis.backend.config.ServiceIaClientConfig;
import com.gestionsuivis.backend.config.ServiceIaProperties;
import com.gestionsuivis.backend.dto.ia.ChatIaReponse;
import com.gestionsuivis.backend.dto.ia.ChatIaRequete;
import com.gestionsuivis.backend.dto.ia.MessageIa;
import com.gestionsuivis.backend.dto.ia.SanteServiceIa;
import com.gestionsuivis.backend.exception.ServiceIaException;
import com.gestionsuivis.backend.exception.TypeErreurServiceIa;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Tests réseau réels du client IA contre un petit serveur HTTP local du JDK
 * ({@code com.sun.net.httpserver}) : vrais délais d'attente, vraie connexion
 * refusée, vrais en-têtes et vraie conversion JSON (Jackson 3).
 * Aucun FastAPI, aucun contexte Spring, aucune base de données.
 */
class ServiceIaClientReseauTest {

    private static final String CLE = "cle-interne-de-test-1234567890";
    private static final Duration DELAI_COURT = Duration.ofMillis(300);

    private HttpServer serveur;
    private ExecutorService executeur;
    private String url;

    private final AtomicReference<String> cleRecue = new AtomicReference<>();
    private final AtomicReference<String> typeContenuRecu = new AtomicReference<>();
    private final AtomicReference<String> corpsRecu = new AtomicReference<>();

    @BeforeEach
    void demarrerServeurLocal() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        executeur = Executors.newCachedThreadPool();
        serveur.setExecutor(executeur);
        serveur.createContext("/api/ai/chat", echange -> {
            cleRecue.set(echange.getRequestHeaders().getFirst(ServiceIaClient.ENTETE_CLE_INTERNE));
            typeContenuRecu.set(echange.getRequestHeaders().getFirst("Content-Type"));
            corpsRecu.set(new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            repondre(echange, 200, """
                    {"message":{"role":"assistant","content":"Réponse réelle"},"conversation_id":"c-9",
                     "usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3},
                     "model":"gemini-test","created_at":"2026-11-01T08:00:00"}""");
        });
        serveur.createContext("/lent/api/ai/chat", echange -> attendrePuisRepondre(echange));
        serveur.createContext("/lent/health", echange -> attendrePuisRepondre(echange));
        serveur.createContext("/health", echange -> repondre(echange, 200,
                "{\"status\":\"ok\",\"service\":\"ai-service\",\"version\":\"0.1.0\",\"environment\":\"test\"}"));
        serveur.start();
        url = "http://" + serveur.getAddress().getHostString() + ":" + serveur.getAddress().getPort();
    }

    @AfterEach
    void arreterServeurLocal() {
        serveur.stop(0);
        executeur.shutdownNow();
    }

    @Test
    void echangeReel_envoieCleEtJsonEtLitLaReponse() {
        ServiceIaClient client = ServiceIaClientConfig.creerClient(proprietes(url, Duration.ofSeconds(5)));

        ChatIaReponse reponse = client.envoyerChat(
                ChatIaRequete.de(List.of(MessageIa.utilisateur("Bonjour")), "u-1", null));

        assertThat(cleRecue.get()).isEqualTo(CLE);
        assertThat(typeContenuRecu.get()).startsWith("application/json");
        assertThat(corpsRecu.get()).isEqualTo("{\"messages\":[{\"role\":\"user\",\"content\":\"Bonjour\"}],\"user_id\":\"u-1\"}");
        assertThat(reponse.message().contenu()).isEqualTo("Réponse réelle");
        assertThat(reponse.identifiantConversation()).isEqualTo("c-9");
    }

    @Test
    void santeReelle_serviceDisponible() {
        SanteServiceIa sante = ServiceIaClientConfig.creerClient(proprietes(url, Duration.ofSeconds(5))).verifierSante();

        assertThat(sante.disponible()).isTrue();
        assertThat(sante.environnement()).isEqualTo("test");
    }

    @Test
    void delaiDeLectureDuChatDepasse_donneIndisponibleRapidement() {
        ServiceIaClient client = ServiceIaClientConfig.creerClient(proprietes(url + "/lent", DELAI_COURT));

        long debut = System.nanoTime();
        ServiceIaException erreur = catchThrowableOfType(ServiceIaException.class, () -> client.envoyerChat(
                ChatIaRequete.de(List.of(MessageIa.utilisateur("Bonjour")), null, null)));
        long dureeMs = (System.nanoTime() - debut) / 1_000_000;

        assertThat(erreur).isNotNull();
        assertThat(erreur.getType()).isEqualTo(TypeErreurServiceIa.INDISPONIBLE);
        assertThat(erreur.getStatutDistant()).isEmpty();
        assertThat(dureeMs).isLessThan(2_500);
    }

    @Test
    void delaiDuControleDeSanteDepasse_donneIndisponibleSansException() {
        ServiceIaProperties proprietes = new ServiceIaProperties(url + "/lent", CLE,
                Duration.ofSeconds(2), Duration.ofSeconds(60), DELAI_COURT);

        long debut = System.nanoTime();
        SanteServiceIa sante = ServiceIaClientConfig.creerClient(proprietes).verifierSante();
        long dureeMs = (System.nanoTime() - debut) / 1_000_000;

        assertThat(sante.disponible()).isFalse();
        assertThat(sante.statut()).isEqualTo(SanteServiceIa.STATUT_INJOIGNABLE);
        assertThat(dureeMs).isLessThan(2_500);
    }

    @Test
    void connexionRefusee_donneIndisponible() throws IOException {
        int portFerme;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            portFerme = socket.getLocalPort();
        }
        ServiceIaClient client = ServiceIaClientConfig.creerClient(
                proprietes("http://127.0.0.1:" + portFerme, Duration.ofSeconds(5)));

        ServiceIaException erreur = catchThrowableOfType(ServiceIaException.class, () -> client.envoyerChat(
                ChatIaRequete.de(List.of(MessageIa.utilisateur("Bonjour")), null, null)));

        assertThat(erreur.getType()).isEqualTo(TypeErreurServiceIa.INDISPONIBLE);
        assertThat(client.verifierSante().statut()).isEqualTo(SanteServiceIa.STATUT_INJOIGNABLE);
    }

    private static ServiceIaProperties proprietes(String url, Duration delaiLecture) {
        return new ServiceIaProperties(url, CLE, Duration.ofSeconds(2), delaiLecture, delaiLecture);
    }

    private static void attendrePuisRepondre(HttpExchange echange) throws IOException {
        try {
            Thread.sleep(3_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        repondre(echange, 200, "{\"status\":\"ok\"}");
    }

    private static void repondre(HttpExchange echange, int statut, String json) throws IOException {
        byte[] octets = json.getBytes(StandardCharsets.UTF_8);
        echange.getResponseHeaders().add("Content-Type", "application/json");
        echange.sendResponseHeaders(statut, octets.length);
        try (OutputStream sortie = echange.getResponseBody()) {
            sortie.write(octets);
        }
    }
}
