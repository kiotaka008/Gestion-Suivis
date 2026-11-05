package com.gestionsuivis.backend.entity;

import jakarta.persistence.AttributeConverter;

import java.util.function.Function;

/**
 * Conversion entre une énumération Java et un type {@code ENUM} PostgreSQL du schéma
 * officiel ({@code user_role}, {@code project_status}...). La valeur stockée est la
 * valeur JSON de l'énumération (minuscules anglaises, identiques en base et dans le frontend).
 * <p>
 * Chaque colonne concernée déclare aussi {@code @ColumnTransformer(read = "col::text",
 * write = "?::type_pg")} : PostgreSQL refuse sinon de comparer ou d'écrire un
 * {@code varchar} dans une colonne de type énuméré.
 */
public abstract class ConvertisseurEnumPg<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;
    private final Function<E, String> valeur;

    protected ConvertisseurEnumPg(Class<E> type, Function<E, String> valeur) {
        this.type = type;
        this.valeur = valeur;
    }

    @Override
    public String convertToDatabaseColumn(E attribut) {
        return attribut == null ? null : valeur.apply(attribut);
    }

    @Override
    public E convertToEntityAttribute(String colonne) {
        if (colonne == null) {
            return null;
        }
        for (E candidat : type.getEnumConstants()) {
            if (valeur.apply(candidat).equals(colonne)) {
                return candidat;
            }
        }
        throw new IllegalStateException("Valeur '" + colonne + "' inconnue pour " + type.getSimpleName() + ".");
    }
}
