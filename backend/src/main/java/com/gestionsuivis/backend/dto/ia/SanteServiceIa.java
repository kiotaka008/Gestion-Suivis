package com.gestionsuivis.backend.dto.ia;

/**
 * Résultat du contrôle de disponibilité du service IA ({@code GET /health}).
 *
 * @param disponible    {@code true} si FastAPI a répondu {@code "status": "ok"}
 * @param statut        valeur {@code status} renvoyée, ou {@code NON_CONFIGURE} / {@code INJOIGNABLE}
 * @param version       version du service IA, si connue
 * @param environnement environnement du service IA ({@code development}, ...), si connu
 * @param latenceMs     durée de l'appel en millisecondes, {@code null} si aucun appel n'a eu lieu
 */
public record SanteServiceIa(
        boolean disponible,
        String statut,
        String version,
        String environnement,
        Long latenceMs) {

    public static final String STATUT_NON_CONFIGURE = "NON_CONFIGURE";
    public static final String STATUT_INJOIGNABLE = "INJOIGNABLE";

    public static SanteServiceIa nonConfigure() {
        return new SanteServiceIa(false, STATUT_NON_CONFIGURE, null, null, null);
    }

    public static SanteServiceIa injoignable(long latenceMs) {
        return new SanteServiceIa(false, STATUT_INJOIGNABLE, null, null, latenceMs);
    }
}
