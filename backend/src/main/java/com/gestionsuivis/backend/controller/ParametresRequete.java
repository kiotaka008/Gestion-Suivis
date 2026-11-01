package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.exception.ParametreInvalideException;

import java.util.function.Function;

/**
 * Lecture des paramètres de requête exprimés avec les valeurs JSON du frontend
 * (ex. {@code ?status=in_progress}, {@code ?role=member}, {@code ?status=unread}).
 */
final class ParametresRequete {

    private ParametresRequete() {
    }

    static <E> E enumeration(String valeur, Function<String, E> conversion, String nom) {
        if (valeur == null || valeur.isBlank() || valeur.equalsIgnoreCase("all")) {
            return null;
        }
        try {
            return conversion.apply(valeur);
        } catch (IllegalArgumentException e) {
            throw new ParametreInvalideException("Paramètre '" + nom + "' : " + e.getMessage());
        }
    }

    /** {@code read} → {@code true}, {@code unread} → {@code false}, {@code all} ou absent → {@code null}. */
    static Boolean etatLecture(String valeur) {
        if (valeur == null || valeur.isBlank() || valeur.equalsIgnoreCase("all")) {
            return null;
        }
        return switch (valeur.trim().toLowerCase()) {
            case "read" -> true;
            case "unread" -> false;
            default -> throw new ParametreInvalideException(
                    "Le paramètre 'status' doit valoir 'all', 'read' ou 'unread'.");
        };
    }
}
