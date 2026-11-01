package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.config.PaginationConfig;
import com.gestionsuivis.backend.dto.IdentifiantUtilisateurRequest;
import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.departement.CreerDepartementRequest;
import com.gestionsuivis.backend.dto.departement.DepartementResponse;
import com.gestionsuivis.backend.dto.departement.ModifierDepartementRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.service.DepartementService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Départements : lecture pour tout utilisateur connecté, gestion réservée aux ADMIN. */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartementController {

    static final Map<String, String> TRIS = Map.of("name", "nom", "createdAt", "dateCreation");

    private final DepartementService service;

    @GetMapping
    public PageResponse<DepartementResponse> lister(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer perPage,
            @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String search) {
        return service.lister(search, PaginationConfig.creer(page, perPage, sortBy, sortOrder, TRIS, Sort.by("nom")));
    }

    @GetMapping("/{id}")
    public DepartementResponse obtenir(@PathVariable UUID id) {
        return service.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<DepartementResponse> creer(@Valid @RequestBody CreerDepartementRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(requete));
    }

    @PatchMapping("/{id}")
    public DepartementResponse modifier(@PathVariable UUID id, @Valid @RequestBody ModifierDepartementRequest requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/manager")
    public DepartementResponse definirResponsable(@PathVariable UUID id,
                                                  @Valid @RequestBody IdentifiantUtilisateurRequest requete) {
        return service.definirResponsable(id, requete.userId());
    }

    @DeleteMapping("/{id}/manager")
    public DepartementResponse retirerResponsable(@PathVariable UUID id) {
        return service.retirerResponsable(id);
    }

    @GetMapping("/{id}/members")
    public PageResponse<UtilisateurResponse> listerMembres(
            @PathVariable UUID id, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage) {
        return service.listerMembres(id, PaginationConfig.creer(page, perPage, null, null, Map.of(), Sort.by("nom")));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Void> ajouterMembre(@PathVariable UUID id,
                                              @Valid @RequestBody IdentifiantUtilisateurRequest requete) {
        service.ajouterMembre(id, requete.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> retirerMembre(@PathVariable UUID id, @PathVariable UUID userId) {
        service.retirerMembre(id, userId);
        return ResponseEntity.noContent().build();
    }
}
