package com.gestionsuivis.backend.service.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestionsuivis.backend.config.ServiceIaProperties;
import com.gestionsuivis.backend.dto.ia.AnalyseRisquesIaReponse;
import com.gestionsuivis.backend.dto.ia.AnalyseRisquesIaRequete;
import com.gestionsuivis.backend.dto.ia.RapportIaReponse;
import com.gestionsuivis.backend.dto.ia.RapportIaRequete;
import com.gestionsuivis.backend.dto.ia.ChatIaReponse;
import com.gestionsuivis.backend.dto.ia.ChatIaRequete;
import com.gestionsuivis.backend.dto.ia.MessageIa;
import com.gestionsuivis.backend.dto.ia.RoleMessageIa;
import com.gestionsuivis.backend.dto.ia.SanteServiceIa;
import com.gestionsuivis.backend.exception.ServiceIaException;
import com.gestionsuivis.backend.exception.TypeErreurServiceIa;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Client du service IA FastAPI du projet. Spring Boot est le seul composant
 * autorisé à l'appeler ; Gemini n'est jamais contacté directement.
 * <p>
 * Endpoints utilisés :
 * <ul>
 *   <li>{@code GET /health} : contrôle de disponibilité, sans exception ;</li>
 *   <li>{@code POST /api/ai/chat} : conversation ;</li>
 *   <li>{@code POST /api/ai/analyze-risks} : synthèse des risques d'un projet ;</li>
 *   <li>{@code POST /api/ai/generate-report} : rédaction d'un rapport.</li>
 * </ul>
 * Les routes {@code /api/ai/*} sont protégées par {@code X-Internal-API-Key}.
 * Règles appliquées :
 * <ul>
 *   <li>toute erreur est convertie en {@link ServiceIaException} au message générique ;</li>
 *   <li>le message ou le détail renvoyé par FastAPI n'est jamais réutilisé ;</li>
 *   <li>les logs ne contiennent jamais la clé interne ni le contenu des messages ;</li>
 *   <li>aucune nouvelle tentative automatique n'est faite sur le chat.</li>
 * </ul>
 */
@Slf4j
public class ServiceIaClient {

    public static final String ENTETE_CLE_INTERNE = "X-Internal-API-Key";
    public static final String CHEMIN_SANTE = "/health";
    public static final String CHEMIN_CHAT = "/api/ai/chat";
    public static final String CHEMIN_ANALYSE_RISQUES = "/api/ai/analyze-risks";
    public static final String CHEMIN_RAPPORT = "/api/ai/generate-report";

    /** Limites de {@code ChatRequest} / {@code ChatMessage} dans {@code ai/app/models/schemas.py}. */
    public static final int NOMBRE_MAX_MESSAGES = 50;
    public static final int LONGUEUR_MAX_CONTENU = 32_000;
    public static final int LONGUEUR_MAX_IDENTIFIANT = 64;
    public static final double TEMPERATURE_MAX = 2.0;
    /** Limites de {@code AnalyzeRisksRequest}. */
    public static final int LONGUEUR_MAX_NOM_PROJET = 200;
    public static final int LONGUEUR_MAX_DESCRIPTION_PROJET = 5000;
    /** Valeurs de {@code RiskItem.level} et {@code GenerateReportRequest.report_type}. */
    public static final java.util.Set<String> NIVEAUX_RISQUE = java.util.Set.of("low", "medium", "high", "critical");
    public static final java.util.Set<String> TYPES_RAPPORT = java.util.Set.of("weekly", "monthly", "project", "productivity");

    private static final int TAILLE_MAX_CORPS_ERREUR = 64 * 1024;
    private static final long DELAI_MAX_RETRY_AFTER_SECONDES = 3600;
    private static final Pattern CODE_ERREUR_SUR = Pattern.compile("[A-Z0-9_]{1,64}");
    private static final JsonMapper LECTEUR_JSON = JsonMapper.builder().build();

    private final ServiceIaProperties proprietes;
    private final RestClient clientChat;
    private final RestClient clientSante;

    public ServiceIaClient(ServiceIaProperties proprietes, RestClient clientChat, RestClient clientSante) {
        this.proprietes = Objects.requireNonNull(proprietes);
        this.clientChat = Objects.requireNonNull(clientChat);
        this.clientSante = Objects.requireNonNull(clientSante);
    }

    public boolean estConfigure() {
        return proprietes.estConfigure();
    }

    // ---------------------------------------------------------------- santé

    /**
     * Interroge {@code GET /health}. Ne lève jamais d'exception : en cas de
     * problème, le service est simplement signalé comme indisponible.
     */
    public SanteServiceIa verifierSante() {
        if (!proprietes.estConfigure()) {
            return SanteServiceIa.nonConfigure();
        }
        long debut = System.nanoTime();
        try {
            ReponseSanteIa reponse = clientSante.get()
                    .uri(CHEMIN_SANTE)
                    .retrieve()
                    .body(ReponseSanteIa.class);
            long latence = dureeMs(debut);
            boolean disponible = reponse != null && "ok".equals(reponse.status());
            log.debug("Contrôle de santé du service IA : disponible={} en {} ms", disponible, latence);
            return new SanteServiceIa(disponible,
                    reponse == null ? null : reponse.status(),
                    reponse == null ? null : reponse.version(),
                    reponse == null ? null : reponse.environment(),
                    latence);
        } catch (RestClientResponseException e) {
            long latence = dureeMs(debut);
            log.warn("Contrôle de santé du service IA : HTTP {} en {} ms", e.getStatusCode().value(), latence);
            return new SanteServiceIa(false, "HTTP_" + e.getStatusCode().value(), null, null, latence);
        } catch (RuntimeException e) {
            long latence = dureeMs(debut);
            log.warn("Contrôle de santé du service IA : injoignable en {} ms ({})", latence, motifReseau(e));
            return SanteServiceIa.injoignable(latence);
        }
    }

    // ---------------------------------------------------------------- chat

    /**
     * Envoie une conversation à {@code POST /api/ai/chat} et renvoie la réponse
     * de l'assistant. Un seul appel est effectué, sans nouvelle tentative.
     *
     * @throws ServiceIaException en cas de configuration absente, de requête
     *                            invalide, d'erreur réseau ou d'erreur du service IA
     */
    public ChatIaReponse envoyerChat(ChatIaRequete requete) {
        Objects.requireNonNull(requete, "requete");
        if (!proprietes.estConfigure()) {
            log.warn("Appel IA {} impossible : service IA non configuré.", CHEMIN_CHAT);
            throw new ServiceIaException(TypeErreurServiceIa.INDISPONIBLE);
        }
        validerAvantEnvoi(requete);

        long debut = System.nanoTime();
        ChatIaReponse reponse = poster(CHEMIN_CHAT, requete, ChatIaReponse.class, debut);
        verifierReponse(reponse, dureeMs(debut));
        log.info("Appel IA POST {} réussi en {} ms ({} message(s) envoyé(s))",
                CHEMIN_CHAT, dureeMs(debut), requete.messages().size());
        return reponse;
    }

    // ---------------------------------------------------------------- analyse de risques

    /**
     * Demande à {@code POST /api/ai/analyze-risks} une synthèse des risques d'un
     * projet. Les données transmises doivent déjà être filtrées selon les droits
     * de l'utilisateur. Un seul appel, sans nouvelle tentative.
     */
    public AnalyseRisquesIaReponse analyserRisques(AnalyseRisquesIaRequete requete) {
        Objects.requireNonNull(requete, "requete");
        verifierConfigure(CHEMIN_ANALYSE_RISQUES);
        if (requete.nomProjet() == null || requete.nomProjet().isBlank()
                || requete.nomProjet().length() > LONGUEUR_MAX_NOM_PROJET
                || (requete.descriptionProjet() != null
                && requete.descriptionProjet().length() > LONGUEUR_MAX_DESCRIPTION_PROJET)) {
            refuserAvantEnvoi(CHEMIN_ANALYSE_RISQUES, "nom ou description du projet hors limites");
        }
        long debut = System.nanoTime();
        AnalyseRisquesIaReponse reponse = poster(CHEMIN_ANALYSE_RISQUES, requete, AnalyseRisquesIaReponse.class, debut);
        if (reponse == null || reponse.risqueGlobal() == null || !NIVEAUX_RISQUE.contains(reponse.risqueGlobal())) {
            log.error("Appel IA POST {} : réponse non conforme après {} ms", CHEMIN_ANALYSE_RISQUES, dureeMs(debut));
            throw new ServiceIaException(TypeErreurServiceIa.REPONSE_INVALIDE);
        }
        log.info("Appel IA POST {} réussi en {} ms", CHEMIN_ANALYSE_RISQUES, dureeMs(debut));
        return reponse;
    }

    // ---------------------------------------------------------------- rapport

    /**
     * Demande à {@code POST /api/ai/generate-report} la rédaction d'un rapport à
     * partir d'indicateurs déjà calculés par le backend. Un seul appel.
     */
    public RapportIaReponse genererRapport(RapportIaRequete requete) {
        Objects.requireNonNull(requete, "requete");
        verifierConfigure(CHEMIN_RAPPORT);
        if (requete.typeRapport() == null || !TYPES_RAPPORT.contains(requete.typeRapport()) || requete.data() == null) {
            refuserAvantEnvoi(CHEMIN_RAPPORT, "type de rapport ou données absents");
        }
        long debut = System.nanoTime();
        RapportIaReponse reponse = poster(CHEMIN_RAPPORT, requete, RapportIaReponse.class, debut);
        if (reponse == null || reponse.content() == null) {
            log.error("Appel IA POST {} : réponse non conforme après {} ms", CHEMIN_RAPPORT, dureeMs(debut));
            throw new ServiceIaException(TypeErreurServiceIa.REPONSE_INVALIDE);
        }
        log.info("Appel IA POST {} réussi en {} ms", CHEMIN_RAPPORT, dureeMs(debut));
        return reponse;
    }

    // ---------------------------------------------------------------- envoi commun

    private void verifierConfigure(String chemin) {
        if (!proprietes.estConfigure()) {
            log.warn("Appel IA {} impossible : service IA non configuré.", chemin);
            throw new ServiceIaException(TypeErreurServiceIa.INDISPONIBLE);
        }
    }

    private <T> T poster(String chemin, Object corps, Class<T> typeReponse, long debut) {
        try {
            return clientChat.post()
                    .uri(chemin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (requeteHttp, reponseHttp) -> {
                                throw traduireErreur(chemin, reponseHttp, dureeMs(debut));
                            })
                    .body(typeReponse);
        } catch (ServiceIaException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("Appel IA POST {} : service injoignable après {} ms ({})",
                    chemin, dureeMs(debut), motifReseau(e));
            throw new ServiceIaException(TypeErreurServiceIa.INDISPONIBLE, e);
        } catch (RestClientException e) {
            log.error("Appel IA POST {} : réponse illisible après {} ms ({})",
                    chemin, dureeMs(debut), e.getClass().getSimpleName());
            throw new ServiceIaException(TypeErreurServiceIa.REPONSE_INVALIDE, e);
        }
    }

    // ---------------------------------------------------------------- validation

    private void validerAvantEnvoi(ChatIaRequete requete) {
        int nombre = requete.messages().size();
        if (nombre < 1 || nombre > NOMBRE_MAX_MESSAGES) {
            refuserAvantEnvoi(CHEMIN_CHAT, "nombre de messages hors limites (" + nombre + ")");
        }
        for (MessageIa message : requete.messages()) {
            if (message == null || message.role() == null || message.contenu() == null
                    || message.contenu().strip().isEmpty()) {
                refuserAvantEnvoi(CHEMIN_CHAT, "message vide ou sans rôle");
            }
            if (message.contenu().length() > LONGUEUR_MAX_CONTENU) {
                refuserAvantEnvoi(CHEMIN_CHAT, "message trop long (" + message.contenu().length() + " caractères)");
            }
        }
        if (tropLong(requete.identifiantUtilisateur()) || tropLong(requete.identifiantConversation())) {
            refuserAvantEnvoi(CHEMIN_CHAT, "identifiant trop long");
        }
        Double temperature = requete.temperature();
        if (temperature != null && (temperature.isNaN() || temperature < 0 || temperature > TEMPERATURE_MAX)) {
            refuserAvantEnvoi(CHEMIN_CHAT, "température hors limites");
        }
    }

    private static boolean tropLong(String identifiant) {
        return identifiant != null && identifiant.length() > LONGUEUR_MAX_IDENTIFIANT;
    }

    private static void refuserAvantEnvoi(String chemin, String motif) {
        log.debug("Appel IA POST {} refusé avant envoi : {}", chemin, motif);
        throw new ServiceIaException(TypeErreurServiceIa.REQUETE_REFUSEE);
    }

    private static void verifierReponse(ChatIaReponse reponse, long dureeMs) {
        MessageIa message = reponse == null ? null : reponse.message();
        if (message == null || message.role() != RoleMessageIa.ASSISTANT
                || message.contenu() == null || message.contenu().isBlank()) {
            log.error("Appel IA POST {} : réponse incomplète ou non conforme après {} ms", CHEMIN_CHAT, dureeMs);
            throw new ServiceIaException(TypeErreurServiceIa.REPONSE_INVALIDE);
        }
    }

    // ---------------------------------------------------------------- erreurs FastAPI

    /**
     * Convertit une réponse d'erreur de FastAPI. Deux formats existent :
     * {@code {"error": {"code", "message", "details"}}} (erreurs du service IA) et
     * {@code {"detail": ...}} (clé interne refusée, validation FastAPI).
     */
    private ServiceIaException traduireErreur(String chemin, ClientHttpResponse reponse, long dureeMs) throws IOException {
        int statut = reponse.getStatusCode().value();
        CorpsErreurIa corps = lireCorpsErreur(reponse);
        String code = codeSur(corps);
        boolean formatDetail = corps != null && corps.error() == null && corps.detail() != null;

        TypeErreurServiceIa type;
        Duration retryAfter = null;
        if (statut == 429) {
            type = TypeErreurServiceIa.SURCHARGE;
            retryAfter = lireRetryAfter(reponse.getHeaders());
        } else if (statut == 400 && "PROMPT_INJECTION_DETECTED".equals(code)) {
            type = TypeErreurServiceIa.REQUETE_REFUSEE;
        } else if (statut == 422 && "VALIDATION_ERROR".equals(code)) {
            type = TypeErreurServiceIa.REQUETE_REFUSEE;
        } else if (statut == 401 || statut == 402 || statut >= 500) {
            type = TypeErreurServiceIa.INDISPONIBLE;
        } else {
            type = TypeErreurServiceIa.REPONSE_INVALIDE;
        }

        if (statut == 401 && formatDetail) {
            log.error("Appel IA POST {} : clé interne refusée par le service IA (HTTP 401). Vérifier {}.",
                    chemin, ServiceIaProperties.VAR_CLE);
        } else if (type == TypeErreurServiceIa.REQUETE_REFUSEE || type == TypeErreurServiceIa.SURCHARGE) {
            log.warn("Appel IA POST {} : HTTP {} (code {}) après {} ms", chemin, statut, code, dureeMs);
        } else {
            log.error("Appel IA POST {} : HTTP {} (code {}, format {}) après {} ms",
                    chemin, statut, code, formatCorps(corps), dureeMs);
        }
        return new ServiceIaException(type, statut, code, retryAfter, null);
    }

    private static CorpsErreurIa lireCorpsErreur(ClientHttpResponse reponse) {
        try (InputStream flux = reponse.getBody()) {
            byte[] octets = flux.readNBytes(TAILLE_MAX_CORPS_ERREUR);
            if (octets.length == 0) {
                return null;
            }
            return LECTEUR_JSON.readValue(octets, CorpsErreurIa.class);
        } catch (IOException | RuntimeException illisible) {
            return null;
        }
    }

    /** Code d'erreur FastAPI, uniquement s'il a la forme attendue (évite l'injection dans les logs). */
    private static String codeSur(CorpsErreurIa corps) {
        if (corps == null || corps.error() == null || corps.error().code() == null) {
            return null;
        }
        String code = corps.error().code();
        return CODE_ERREUR_SUR.matcher(code).matches() ? code : null;
    }

    private static String formatCorps(CorpsErreurIa corps) {
        if (corps == null) {
            return "vide ou illisible";
        }
        if (corps.error() != null) {
            return "error";
        }
        return corps.detail() != null ? "detail" : "inconnu";
    }

    private static Duration lireRetryAfter(HttpHeaders entetes) {
        String valeur = entetes.getFirst(HttpHeaders.RETRY_AFTER);
        if (valeur == null) {
            return null;
        }
        try {
            long secondes = Long.parseLong(valeur.trim());
            return secondes >= 0 && secondes <= DELAI_MAX_RETRY_AFTER_SECONDES ? Duration.ofSeconds(secondes) : null;
        } catch (NumberFormatException formatDate) {
            return null;
        }
    }

    // ---------------------------------------------------------------- utilitaires

    private static String motifReseau(Throwable erreur) {
        Throwable cause = erreur;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause instanceof HttpTimeoutException) {
            return "délai dépassé";
        }
        return cause.getClass().getSimpleName();
    }

    private static long dureeMs(long debutNanos) {
        return (System.nanoTime() - debutNanos) / 1_000_000;
    }

    /** Corps de {@code GET /health} ({@code ai/app/api/routes/health.py}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReponseSanteIa(String status, String service, String version, String environment) {
    }

    /** Corps d'erreur FastAPI : {@code {"error": {...}}} ou {@code {"detail": ...}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CorpsErreurIa(DetailErreurIa error, Object detail) {
    }

    /** Seul le code est exploité ; le message distant n'est jamais réutilisé. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DetailErreurIa(String code) {
    }
}
