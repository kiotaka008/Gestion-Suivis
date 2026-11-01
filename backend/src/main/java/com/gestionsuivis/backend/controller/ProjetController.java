package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.config.PaginationConfig;
import com.gestionsuivis.backend.dto.IdentifiantUtilisateurRequest;
import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.activite.ActiviteResponse;
import com.gestionsuivis.backend.dto.projet.CreerProjetRequest;
import com.gestionsuivis.backend.dto.projet.ModifierProjetRequest;
import com.gestionsuivis.backend.dto.projet.ProjetResponse;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.service.ActiviteService;
import com.gestionsuivis.backend.service.ProjetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Projets, aligné sur {@code src/services/projectService.ts} du frontend
 * ({@code GET/POST /projects}, {@code GET/PATCH/DELETE /projects/{id}}),
 * complété par la gestion des membres et la liste des activités d'un projet.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjetController {

    static final Map<String, String> TRIS = Map.of("name", "nom", "status", "statut", "priority", "priorite",
            "startDate", "dateDebut", "dueDate", "dateEcheance", "createdAt", "dateCreation",
            "updatedAt", "dateModification");

    private final ProjetService service;
    private final ActiviteService activiteService;

    @GetMapping
    public PageResponse<ProjetResponse> lister(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer perPage,
            @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String search, @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority, @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID ownerId) {
        return service.lister(search,
                ParametresRequete.enumeration(status, StatutProjet::depuisJson, "status"),
                ParametresRequete.enumeration(priority, Priorite::depuisJson, "priority"),
                departmentId, ownerId,
                PaginationConfig.creer(page, perPage, sortBy, sortOrder, TRIS, Sort.by(Sort.Direction.DESC, "dateCreation")));
    }

    @GetMapping("/{id}")
    public ProjetResponse obtenir(@PathVariable UUID id) {
        return service.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<ProjetResponse> creer(@Valid @RequestBody CreerProjetRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(requete));
    }

    @PatchMapping("/{id}")
    public ProjetResponse modifier(@PathVariable UUID id, @Valid @RequestBody ModifierProjetRequest requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    public ProjetResponse ajouterMembre(@PathVariable UUID id, @Valid @RequestBody IdentifiantUtilisateurRequest requete) {
        return service.ajouterMembre(id, requete.userId());
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ProjetResponse retirerMembre(@PathVariable UUID id, @PathVariable UUID userId) {
        return service.retirerMembre(id, userId);
    }

    /** Activités du projet visibles par l'utilisateur connecté. */
    @GetMapping("/{id}/activities")
    public PageResponse<ActiviteResponse> activites(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer perPage,
            @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String status, @RequestParam(required = false) String priority) {
        return activiteService.lister(null,
                ParametresRequete.enumeration(status, StatutActivite::depuisJson, "status"),
                ParametresRequete.enumeration(priority, Priorite::depuisJson, "priority"),
                id, null, null,
                PaginationConfig.creer(page, perPage, sortBy, sortOrder, ActiviteController.TRIS,
                        Sort.by("dateEcheance")));
    }
}
