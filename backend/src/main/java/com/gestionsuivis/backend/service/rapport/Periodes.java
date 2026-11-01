package com.gestionsuivis.backend.service.rapport;

import com.gestionsuivis.backend.dto.rapport.BilanResponse;
import com.gestionsuivis.backend.exception.ParametreInvalideException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Périodes de bilan. Codes acceptés :
 * <ul>
 *   <li>cahier des charges : {@code week} (semaine en cours, lundi-dimanche),
 *       {@code month} (mois en cours), {@code year} (année en cours) ;</li>
 *   <li>sélecteur du frontend ({@code PeriodSelector}) : {@code 7d}, {@code 30d},
 *       {@code 3m}, {@code 6m}, {@code 12m} (glissant jusqu'à aujourd'hui) ;</li>
 *   <li>période libre : paramètres {@code from} et {@code to} (code {@code custom}).</li>
 * </ul>
 */
public final class Periodes {

    public static final String PAR_DEFAUT = "30d";
    private static final long DUREE_MAX_JOURS = 5 * 366;

    private Periodes() {
    }

    public static BilanResponse.Periode resoudre(String code, LocalDate debut, LocalDate fin, LocalDate aujourdHui) {
        if (debut != null || fin != null) {
            if (debut == null || fin == null) {
                throw new ParametreInvalideException("Les paramètres 'from' et 'to' doivent être fournis ensemble.");
            }
            if (fin.isBefore(debut)) {
                throw new ParametreInvalideException("La date 'to' doit être postérieure ou égale à 'from'.");
            }
            if (ChronoUnit.DAYS.between(debut, fin) > DUREE_MAX_JOURS) {
                throw new ParametreInvalideException("La période ne doit pas dépasser 5 ans.");
            }
            return new BilanResponse.Periode("custom", debut, fin);
        }
        String valeur = code == null || code.isBlank() ? PAR_DEFAUT : code.trim().toLowerCase();
        return switch (valeur) {
            case "week" -> new BilanResponse.Periode(valeur, aujourdHui.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    aujourdHui.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            case "month" -> new BilanResponse.Periode(valeur, aujourdHui.withDayOfMonth(1),
                    aujourdHui.with(TemporalAdjusters.lastDayOfMonth()));
            case "year" -> new BilanResponse.Periode(valeur, aujourdHui.withDayOfYear(1),
                    aujourdHui.with(TemporalAdjusters.lastDayOfYear()));
            case "7d" -> new BilanResponse.Periode(valeur, aujourdHui.minusDays(6), aujourdHui);
            case "30d" -> new BilanResponse.Periode(valeur, aujourdHui.minusDays(29), aujourdHui);
            case "3m" -> new BilanResponse.Periode(valeur, aujourdHui.minusMonths(3).plusDays(1), aujourdHui);
            case "6m" -> new BilanResponse.Periode(valeur, aujourdHui.minusMonths(6).plusDays(1), aujourdHui);
            case "12m" -> new BilanResponse.Periode(valeur, aujourdHui.minusMonths(12).plusDays(1), aujourdHui);
            default -> throw new ParametreInvalideException("Période '" + code
                    + "' non reconnue. Valeurs acceptées : week, month, year, 7d, 30d, 3m, 6m, 12m.");
        };
    }

    public static boolean contient(BilanResponse.Periode periode, LocalDate date) {
        return date != null && !date.isBefore(periode.from()) && !date.isAfter(periode.to());
    }
}
