package com.gestionsuivis.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

/**
 * Configuration du client du service IA (FastAPI), lue directement dans les
 * variables d'environnement (aucune entrée dans application.properties).
 * <ul>
 *   <li>{@value #VAR_URL} : adresse du service, ex. {@code http://localhost:8000} ;</li>
 *   <li>{@value #VAR_CLE} : clé partagée envoyée dans {@code X-Internal-API-Key}
 *       (au moins {@value #LONGUEUR_MIN_CLE} caractères, comme l'exige FastAPI) ;</li>
 *   <li>{@value #VAR_DELAI_CONNEXION}, {@value #VAR_DELAI_LECTURE_CHAT},
 *       {@value #VAR_DELAI_SANTE} : délais facultatifs, en secondes.</li>
 * </ul>
 * Une valeur absente ou invalide n'empêche pas l'application de démarrer : le
 * service est alors considéré comme non configuré, donc indisponible.
 * <p>
 * {@link #toString()} masque toujours la clé.
 */
public record ServiceIaProperties(
        String urlService,
        String cleInterne,
        Duration delaiConnexion,
        Duration delaiLectureChat,
        Duration delaiSante) {

    public static final String VAR_URL = "AI_SERVICE_URL";
    public static final String VAR_CLE = "AI_INTERNAL_API_KEY";
    public static final String VAR_DELAI_CONNEXION = "AI_CONNECT_TIMEOUT_SECONDS";
    public static final String VAR_DELAI_LECTURE_CHAT = "AI_READ_TIMEOUT_SECONDS";
    public static final String VAR_DELAI_SANTE = "AI_HEALTH_TIMEOUT_SECONDS";

    public static final int LONGUEUR_MIN_CLE = 16;
    public static final Duration DELAI_CONNEXION_PAR_DEFAUT = Duration.ofSeconds(5);
    public static final Duration DELAI_LECTURE_CHAT_PAR_DEFAUT = Duration.ofSeconds(60);
    public static final Duration DELAI_SANTE_PAR_DEFAUT = Duration.ofSeconds(3);
    static final long DELAI_MAXIMAL_SECONDES = 600;

    private static final Logger LOG = LoggerFactory.getLogger(ServiceIaProperties.class);

    /**
     * Lit et valide la configuration. Les anomalies sont signalées dans les logs
     * sans jamais afficher la valeur de la clé.
     */
    public static ServiceIaProperties depuis(Environment environnement) {
        return new ServiceIaProperties(
                lireUrl(environnement.getProperty(VAR_URL)),
                lireCle(environnement.getProperty(VAR_CLE)),
                lireDelai(environnement, VAR_DELAI_CONNEXION, DELAI_CONNEXION_PAR_DEFAUT),
                lireDelai(environnement, VAR_DELAI_LECTURE_CHAT, DELAI_LECTURE_CHAT_PAR_DEFAUT),
                lireDelai(environnement, VAR_DELAI_SANTE, DELAI_SANTE_PAR_DEFAUT));
    }

    /** {@code true} si l'adresse et la clé sont présentes et valides. */
    public boolean estConfigure() {
        return urlService != null && cleInterne != null;
    }

    @Override
    public String toString() {
        return "ServiceIaProperties[urlService=" + urlService
                + ", cleInterne=" + (cleInterne == null ? "absente" : "***")
                + ", delaiConnexion=" + delaiConnexion
                + ", delaiLectureChat=" + delaiLectureChat
                + ", delaiSante=" + delaiSante + "]";
    }

    private static String lireUrl(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String url = valeur.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        try {
            URI uri = new URI(url);
            String schema = uri.getScheme();
            if (schema == null || !(schema.equalsIgnoreCase("http") || schema.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                LOG.warn("{} invalide : une adresse http(s) complète est attendue.", VAR_URL);
                return null;
            }
            return url;
        } catch (URISyntaxException e) {
            LOG.warn("{} invalide : l'adresse ne peut pas être analysée.", VAR_URL);
            return null;
        }
    }

    private static String lireCle(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String cle = valeur.trim();
        if (cle.length() < LONGUEUR_MIN_CLE) {
            LOG.warn("{} invalide : au moins {} caractères sont attendus.", VAR_CLE, LONGUEUR_MIN_CLE);
            return null;
        }
        return cle;
    }

    private static Duration lireDelai(Environment environnement, String variable, Duration parDefaut) {
        String valeur = environnement.getProperty(variable);
        if (valeur == null || valeur.isBlank()) {
            return parDefaut;
        }
        try {
            long secondes = Long.parseLong(valeur.trim());
            if (secondes >= 1 && secondes <= DELAI_MAXIMAL_SECONDES) {
                return Duration.ofSeconds(secondes);
            }
        } catch (NumberFormatException ignoree) {
            // valeur non numérique : traitée comme invalide ci-dessous
        }
        LOG.warn("{} invalide : un nombre entier de secondes entre 1 et {} est attendu. Valeur par défaut utilisée : {} s.",
                variable, DELAI_MAXIMAL_SECONDES, parDefaut.toSeconds());
        return parDefaut;
    }
}
