package com.gestionsuivis.backend.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Catégories d'erreurs du client du service IA (FastAPI).
 * <p>
 * Chaque catégorie porte le code HTTP que le backend devrait renvoyer au frontend
 * et un message générique en français. Le message d'origine de FastAPI n'est
 * jamais réutilisé : il peut contenir le texte brut de Gemini ou une partie des
 * données envoyées.
 */
public enum TypeErreurServiceIa {

    /** Service non configuré, injoignable, trop lent, ou en erreur côté IA/Gemini. */
    INDISPONIBLE(503,
            "Le service d'assistance IA est momentanément indisponible. Veuillez réessayer plus tard."),

    /** Message refusé (contenu trop long, tentative d'injection de prompt, etc.). */
    REQUETE_REFUSEE(422,
            "Votre message ne peut pas être traité par l'assistant IA. Veuillez le reformuler."),

    /** Limite de requêtes du service IA atteinte. */
    SURCHARGE(429,
            "Trop de requêtes vers l'assistant IA. Veuillez patienter quelques instants."),

    /** Réponse inattendue : incompatibilité de contrat ou erreur côté backend. */
    REPONSE_INVALIDE(502,
            "Le service d'assistance IA a renvoyé une réponse inattendue. Veuillez réessayer plus tard.");

    private final HttpStatusCode statutHttpSuggere;
    private final String messageUtilisateur;

    TypeErreurServiceIa(int statutHttpSuggere, String messageUtilisateur) {
        this.statutHttpSuggere = HttpStatusCode.valueOf(statutHttpSuggere);
        this.messageUtilisateur = messageUtilisateur;
    }

    public HttpStatusCode statutHttpSuggere() {
        return statutHttpSuggere;
    }

    public String messageUtilisateur() {
        return messageUtilisateur;
    }
}
