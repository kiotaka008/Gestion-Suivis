package com.gestionsuivis.backend.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Messages d'erreur génériques en français, partagés entre le gestionnaire
 * central et les composants Spring Security.
 * <p>
 * Ces messages ne contiennent jamais de détail technique (requête SQL, classe Java,
 * pile d'appels) ni de donnée sensible.
 */
public final class MessagesErreur {

    public static final String DONNEES_INVALIDES = "Les données envoyées sont invalides.";
    public static final String CORPS_ILLISIBLE = "Le corps de la requête est absent ou mal formé.";
    public static final String NON_AUTHENTIFIE = "Authentification requise pour accéder à cette ressource.";
    public static final String IDENTIFIANTS_INVALIDES = "Email ou mot de passe incorrect.";
    public static final String ACCES_INTERDIT = "Vous n'avez pas les droits nécessaires pour accéder à cette ressource.";
    public static final String JETON_CSRF_INVALIDE = "Jeton de sécurité (CSRF) absent ou invalide. Rechargez la page.";
    public static final String RESSOURCE_INEXISTANTE = "La ressource demandée n'existe pas.";
    public static final String CONFLIT_DONNEES = "L'opération est en conflit avec des données existantes.";
    public static final String TYPE_CONTENU_NON_SUPPORTE =
            "Le type de contenu n'est pas pris en charge. Utilisez application/json.";
    public static final String VALEUR_INVALIDE = "Valeur invalide.";
    public static final String ERREUR_INTERNE =
            "Une erreur interne est survenue. Veuillez réessayer plus tard.";

    private MessagesErreur() {
    }

    /**
     * Code d'erreur stable (champ {@code code} de la réponse), aligné sur
     * {@code ApiErrorCode} du frontend.
     */
    public static String code(HttpStatusCode statut) {
        return switch (statut.value()) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "VALIDATION_ERROR";
            case 429 -> "RATE_LIMITED";
            default -> statut.is5xxServerError() ? "SERVER_ERROR" : "UNKNOWN";
        };
    }

    /** Message générique pour un code HTTP sans message métier. */
    public static String generique(HttpStatusCode statut) {
        return switch (statut.value()) {
            case 400 -> CORPS_ILLISIBLE;
            case 401 -> NON_AUTHENTIFIE;
            case 403 -> ACCES_INTERDIT;
            case 404 -> RESSOURCE_INEXISTANTE;
            case 405 -> "Méthode HTTP non autorisée pour cette URL.";
            case 406 -> "Format de réponse non acceptable.";
            case 409 -> CONFLIT_DONNEES;
            case 413 -> "Contenu trop volumineux.";
            case 415 -> TYPE_CONTENU_NON_SUPPORTE;
            case 422 -> DONNEES_INVALIDES;
            case 429 -> "Trop de requêtes. Veuillez patienter un instant.";
            default -> ERREUR_INTERNE;
        };
    }
}
