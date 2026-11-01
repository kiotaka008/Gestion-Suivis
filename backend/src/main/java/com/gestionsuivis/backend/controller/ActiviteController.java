package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.config.PaginationConfig;
import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.activite.ActiviteResponse;
import com.gestionsuivis.backend.dto.activite.CommentaireResponse;
import com.gestionsuivis.backend.dto.activite.CreerActiviteRequest;
import com.gestionsuivis.backend.dto.activite.CreerCommentaireRequest;
import com.gestionsuivis.backend.dto.activite.ModifierActiviteRequest;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.service.ActiviteService;
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
 * Activités, aligné sur {@code src/services/activityService.ts} du frontend
 * ({@code GET/POST /activities}, {@code GET/PATCH/DELETE /activities/{id}}),
 * complété par les commentaires.
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActiviteController {

    static final Map<String, String> TRIS = Map.of("title", "titre", "status", "statut", "priority", "priorite",
            "progress", "avancement", "startDate", "dateDebut", "dueDate", "dateEcheance",
            "createdAt", "dateCreation", "updatedAt", "dateModification");

    private final ActiviteService service;

    @GetMapping
    public PageResponse<ActiviteResponse> lister(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer perPage,
            @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String search, @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority, @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID assigneeId, @RequestParam(required = false) Boolean overdue) {
        return service.lister(search,
                ParametresRequete.enumeration(status, StatutActivite::depuisJson, "status"),
                ParametresRequete.enumeration(priority, Priorite::depuisJson, "priority"),
                projectId, assigneeId, overdue,
                PaginationConfig.creer(page, perPage, sortBy, sortOrder, TRIS, Sort.by(Sort.Direction.DESC, "dateCreation")));
    }

    @GetMapping("/{id}")
    public ActiviteResponse obtenir(@PathVariable UUID id) {
        return service.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<ActiviteResponse> creer(@Valid @RequestBody CreerActiviteRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(requete));
    }

    @PatchMapping("/{id}")
    public ActiviteResponse modifier(@PathVariable UUID id, @Valid @RequestBody ModifierActiviteRequest requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/comments")
    public PageResponse<CommentaireResponse> commentaires(
            @PathVariable UUID id, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage) {
        return service.listerCommentaires(id, PaginationConfig.creer(page, perPage, null, null, Map.of(),
                Sort.by("dateCreation")));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentaireResponse> commenter(@PathVariable UUID id,
                                                         @Valid @RequestBody CreerCommentaireRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.commenter(id, requete));
    }
}
