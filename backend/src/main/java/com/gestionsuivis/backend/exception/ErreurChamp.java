package com.gestionsuivis.backend.exception;

/**
 * Erreur de validation portant sur un champ précis, au format attendu par le
 * frontend ({@code ValidationError { field, message }} dans {@code src/types/api.ts}).
 * <p>
 * La valeur rejetée n'est volontairement jamais renvoyée : elle pourrait contenir
 * une donnée sensible (mot de passe, par exemple).
 *
 * @param field   nom du champ ou du paramètre concerné (camelCase)
 * @param message explication de l'erreur, en français
 */
public record ErreurChamp(String field, String message) {
}
