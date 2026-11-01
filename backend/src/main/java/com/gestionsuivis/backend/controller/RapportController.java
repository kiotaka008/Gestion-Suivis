package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.dto.rapport.BilanResponse;
import com.gestionsuivis.backend.dto.rapport.TableauBordResponse;
import com.gestionsuivis.backend.service.rapport.BilanService;
import com.gestionsuivis.backend.service.rapport.TableauBordService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tableau de bord et bilans, calculés côté backend sur le périmètre de
 * l'utilisateur connecté. Paramètres de période : {@code period}
 * ({@code week|month|year|7d|30d|3m|6m|12m}, 30d par défaut) ou {@code from} et {@code to}.
 */
@RestController
@RequiredArgsConstructor
public class RapportController {

    private final TableauBordService tableauBord;
    private final BilanService bilans;

    @GetMapping("/api/dashboard/stats")
    public TableauBordResponse tableauBord() {
        return tableauBord.statistiques();
    }

    @GetMapping("/api/reports/overview")
    public BilanResponse global(@RequestParam(required = false) String period,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bilans.global(period, from, to);
    }

    @GetMapping("/api/reports/users/{id}")
    public BilanResponse utilisateur(@PathVariable UUID id, @RequestParam(required = false) String period,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bilans.utilisateur(id, period, from, to);
    }

    @GetMapping("/api/reports/departments/{id}")
    public BilanResponse departement(@PathVariable UUID id, @RequestParam(required = false) String period,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bilans.departement(id, period, from, to);
    }

    @GetMapping("/api/reports/projects/{id}")
    public BilanResponse projet(@PathVariable UUID id, @RequestParam(required = false) String period,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bilans.projet(id, period, from, to);
    }
}
