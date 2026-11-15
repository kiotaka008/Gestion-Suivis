package com.gestionsuivis.backend.exception;

import lombok.Getter;

import java.time.Duration;

/**
 * Trop de tentatives de connexion échouées : HTTP 429 avec l'en-tête {@code Retry-After}.
 * Le message ne révèle pas si le compte existe.
 */
@Getter
public class TropDeTentativesException extends RuntimeException {

    private final Duration delaiAvantNouvelEssai;

    public TropDeTentativesException(Duration delaiAvantNouvelEssai) {
        super(message(delaiAvantNouvelEssai));
        this.delaiAvantNouvelEssai = delaiAvantNouvelEssai;
    }

    private static String message(Duration delai) {
        long minutes = Math.max(1, (delai.toSeconds() + 59) / 60);
        return "Trop de tentatives de connexion échouées. Réessayez dans " + minutes
                + (minutes > 1 ? " minutes." : " minute.");
    }
}
