package com.gestionsuivis.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Paramètres de sécurité lus dans les variables d'environnement :
 * <ul>
 *   <li>{@value #VAR_SECRET} : secret de signature HMAC des JWT (32 octets minimum).
 *       Absent : un secret aléatoire est généré au démarrage (les sessions ne
 *       survivent alors pas à un redémarrage) ;</li>
 *   <li>{@value #VAR_DUREE} : durée de validité du jeton, en minutes (60 par défaut) ;</li>
 *   <li>{@value #VAR_COOKIE_SECURE} : {@code true} en production (HTTPS) ;</li>
 *   <li>{@value #VAR_ORIGINES} : origines CORS supplémentaires, séparées par des virgules ;</li>
 *   <li>{@value #VAR_INSCRIPTION} : {@code true} pour ouvrir {@code POST /api/auth/register}.</li>
 * </ul>
 * {@link #toString()} masque le secret.
 */
public record SecuriteProperties(
        byte[] secretJwt,
        boolean secretGenere,
        Duration dureeJeton,
        boolean cookieSecurise,
        List<String> originesAutorisees,
        boolean inscriptionOuverte) {

    public static final String VAR_SECRET = "JWT_SECRET";
    public static final String VAR_DUREE = "JWT_EXPIRATION_MINUTES";
    public static final String VAR_COOKIE_SECURE = "COOKIE_SECURE";
    public static final String VAR_ORIGINES = "CORS_ALLOWED_ORIGINS";
    public static final String VAR_INSCRIPTION = "AUTH_REGISTRATION_ENABLED";

    public static final int LONGUEUR_MIN_SECRET = 32;
    public static final Duration DUREE_PAR_DEFAUT = Duration.ofMinutes(60);
    public static final List<String> ORIGINES_PAR_DEFAUT = List.of("http://localhost:5173", "http://localhost:3000");

    private static final Logger LOG = LoggerFactory.getLogger(SecuriteProperties.class);

    public static SecuriteProperties depuis(Environment environnement) {
        String secret = environnement.getProperty(VAR_SECRET);
        byte[] octets;
        boolean genere;
        if (secret != null && secret.trim().getBytes(StandardCharsets.UTF_8).length >= LONGUEUR_MIN_SECRET) {
            octets = secret.trim().getBytes(StandardCharsets.UTF_8);
            genere = false;
        } else {
            if (secret != null && !secret.isBlank()) {
                LOG.warn("{} trop court ({} octets minimum) : un secret aléatoire est utilisé.", VAR_SECRET,
                        LONGUEUR_MIN_SECRET);
            } else {
                LOG.warn("{} absent : un secret aléatoire est utilisé, les sessions seront perdues au redémarrage.",
                        VAR_SECRET);
            }
            octets = new byte[LONGUEUR_MIN_SECRET];
            new SecureRandom().nextBytes(octets);
            genere = true;
        }

        Duration duree = DUREE_PAR_DEFAUT;
        String valeurDuree = environnement.getProperty(VAR_DUREE);
        if (valeurDuree != null && !valeurDuree.isBlank()) {
            try {
                long minutes = Long.parseLong(valeurDuree.trim());
                if (minutes >= 5 && minutes <= 24 * 60) {
                    duree = Duration.ofMinutes(minutes);
                } else {
                    LOG.warn("{} hors limites (5 à 1440 minutes) : 60 minutes utilisées.", VAR_DUREE);
                }
            } catch (NumberFormatException e) {
                LOG.warn("{} invalide : 60 minutes utilisées.", VAR_DUREE);
            }
        }

        List<String> origines = new ArrayList<>(ORIGINES_PAR_DEFAUT);
        String supplementaires = environnement.getProperty(VAR_ORIGINES);
        if (supplementaires != null) {
            Arrays.stream(supplementaires.split(","))
                    .map(String::trim)
                    .filter(origine -> origine.startsWith("http://") || origine.startsWith("https://"))
                    .filter(origine -> !origines.contains(origine))
                    .forEach(origines::add);
        }

        return new SecuriteProperties(octets, genere, duree,
                Boolean.parseBoolean(environnement.getProperty(VAR_COOKIE_SECURE, "false").trim()),
                List.copyOf(origines),
                Boolean.parseBoolean(environnement.getProperty(VAR_INSCRIPTION, "false").trim()));
    }

    @Override
    public String toString() {
        return "SecuriteProperties[secretJwt=***, secretGenere=" + secretGenere + ", dureeJeton=" + dureeJeton
                + ", cookieSecurise=" + cookieSecurise + ", originesAutorisees=" + originesAutorisees
                + ", inscriptionOuverte=" + inscriptionOuverte + "]";
    }

    @Override
    public boolean equals(Object autre) {
        return autre instanceof SecuriteProperties p && Arrays.equals(secretJwt, p.secretJwt)
                && secretGenere == p.secretGenere && dureeJeton.equals(p.dureeJeton)
                && cookieSecurise == p.cookieSecurise && originesAutorisees.equals(p.originesAutorisees)
                && inscriptionOuverte == p.inscriptionOuverte;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(secretJwt) * 31 + dureeJeton.hashCode();
    }
}
