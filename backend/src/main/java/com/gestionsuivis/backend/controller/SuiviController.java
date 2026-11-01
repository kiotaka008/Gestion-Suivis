package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.config.PaginationConfig;
import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.suivi.IndicateurRisqueResponse;
import com.gestionsuivis.backend.dto.suivi.TacheNonAccomplieResponse;
import com.gestionsuivis.backend.service.rapport.SuiviService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Données de suivi structurées pour le module IA. Mêmes permissions que le reste
 * de l'API : chaque utilisateur ne reçoit que les données de son périmètre.
 */
@RestController
@RequestMapping("/api/suivi")
@RequiredArgsConstructor
public class SuiviController {

    private final SuiviService service;

    /** Tâches non accomplies : non terminées et échéance dépassée. */
    @GetMapping("/taches-non-accomplies")
    public PageResponse<TacheNonAccomplieResponse> tachesNonAccomplies(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer perPage,
            @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID assigneeId) {
        return service.tachesNonAccomplies(projectId, assigneeId,
                PaginationConfig.creer(page, perPage, sortBy, sortOrder, ActiviteController.TRIS, Sort.by("dateEcheance")));
    }

    /** Tâches sans progression depuis {@code days} jours (7 par défaut) ou jamais commencées. */
    @GetMapping("/taches-sans-progression")
    public PageResponse<TacheNonAccomplieResponse> tachesSansProgression(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer perPage,
            @RequestParam(required = false) Integer days, @RequestParam(required = false) UUID projectId) {
        return service.tachesSansProgression(days, projectId,
                PaginationConfig.creer(page, perPage, null, null, Map.of(), Sort.by("dateModification")));
    }

    /** Indicateurs de risque des projets visibles non clos, ou d'un projet ({@code projectId}). */
    @GetMapping("/indicateurs-risque")
    public List<IndicateurRisqueResponse> indicateursRisque(@RequestParam(required = false) UUID projectId) {
        return service.indicateursRisque(projectId);
    }
}
