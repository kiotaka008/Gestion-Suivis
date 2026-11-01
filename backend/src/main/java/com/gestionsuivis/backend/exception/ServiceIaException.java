package com.gestionsuivis.backend.exception;

import org.springframework.http.HttpStatusCode;

import java.time.Duration;
import java.util.Optional;

/**
 * Erreur contrôlée levée par le client du service IA (FastAPI).
 * <p>
 * {@link #getMessage()} renvoie toujours le message générique de la catégorie,
 * sans détail technique, sans clé interne et sans contenu provenant de FastAPI.
 * Le statut et le code d'erreur distants sont conservés uniquement pour le
 * diagnostic côté serveur.
 * <p>
 * La conversion en réponse HTTP sera ajoutée au gestionnaire central avec le
 * premier endpoint {@code /api/ai/*}.
 */
public class ServiceIaException extends RuntimeException {

    private final TypeErreurServiceIa type;
    private final Integer statutDistant;
    private final String codeDistant;
    private final Duration delaiAvantNouvelEssai;

    public ServiceIaException(TypeErreurServiceIa type) {
        this(type, null, null, null, null);
    }

    public ServiceIaException(TypeErreurServiceIa type, Throwable cause) {
        this(type, null, null, null, cause);
    }

    public ServiceIaException(TypeErreurServiceIa type, Integer statutDistant, String codeDistant,
                              Duration delaiAvantNouvelEssai, Throwable cause) {
        super(type.messageUtilisateur(), cause);
        this.type = type;
        this.statutDistant = statutDistant;
        this.codeDistant = codeDistant;
        this.delaiAvantNouvelEssai = delaiAvantNouvelEssai;
    }

    public TypeErreurServiceIa getType() {
        return type;
    }

    /** Code HTTP que le backend devrait renvoyer au frontend. */
    public HttpStatusCode getStatutHttpSuggere() {
        return type.statutHttpSuggere();
    }

    /** Code HTTP reçu de FastAPI, absent si l'erreur est réseau ou locale. */
    public Optional<Integer> getStatutDistant() {
        return Optional.ofNullable(statutDistant);
    }

    /** Code d'erreur FastAPI (ex. {@code PROMPT_INJECTION_DETECTED}), si présent. */
    public Optional<String> getCodeDistant() {
        return Optional.ofNullable(codeDistant);
    }

    /** Délai indiqué par l'en-tête {@code Retry-After}, si présent. */
    public Optional<Duration> getDelaiAvantNouvelEssai() {
        return Optional.ofNullable(delaiAvantNouvelEssai);
    }
}
