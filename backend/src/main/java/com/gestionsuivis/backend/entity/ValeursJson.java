package com.gestionsuivis.backend.entity;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Conversion des valeurs JSON du frontend (minuscules anglaises) vers les
 * énumérations du cahier des charges.
 */
public final class ValeursJson {

    private ValeursJson() {
    }

    public static <E extends Enum<E>> E depuis(Class<E> type, E[] valeurs, Function<E, String> json, String valeur) {
        if (valeur == null) {
            return null;
        }
        String recherche = valeur.trim();
        for (E candidat : valeurs) {
            if (json.apply(candidat).equalsIgnoreCase(recherche)) {
                return candidat;
            }
        }
        throw new IllegalArgumentException("Valeur '" + valeur + "' non prise en charge. Valeurs acceptées : "
                + Arrays.stream(valeurs).map(json).collect(Collectors.joining(", ")) + ".");
    }
}
