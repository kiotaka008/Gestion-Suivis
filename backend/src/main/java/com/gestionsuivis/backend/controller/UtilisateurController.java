package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.config.PaginationConfig;
import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.utilisateur.CreerUtilisateurRequest;
import com.gestionsuivis.backend.dto.utilisateur.ModifierProfilRequest;
import com.gestionsuivis.backend.dto.utilisateur.ModifierUtilisateurRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.service.AuthService;
import com.gestionsuivis.backend.service.UtilisateurService;
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
 * Utilisateurs, aligné sur {@code src/services/userService.ts} du frontend
 * ({@code GET /users}, {@code GET/PATCH /users/{id}}, {@code POST /users/{id}/deactivate}),
 * complété par la création, l'activation, la suppression et le profil.
 * Les autorisations sont vérifiées dans {@link UtilisateurService}.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UtilisateurController {

    static final Map<String, String> TRIS = Map.of(
            "name", "nom", "email", "email", "role", "role", "isActive", "actif", "createdAt", "dateCreation");

    private final UtilisateurService service;
    private final AuthService authService;

    @GetMapping
    public PageResponse<UtilisateurResponse> lister(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer perPage,
            @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String search, @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isActive, @RequestParam(required = false) UUID departmentId) {
        return service.lister(search, ParametresRequete.enumeration(role, Role::depuisJson, "role"), isActive,
                departmentId, PaginationConfig.creer(page, perPage, sortBy, sortOrder, TRIS, Sort.by("nom")));
    }

    @GetMapping("/me")
    public UtilisateurResponse moi() {
        return authService.moi();
    }

    @PatchMapping("/me")
    public UtilisateurResponse modifierProfil(@Valid @RequestBody ModifierProfilRequest requete) {
        return service.modifierProfil(requete);
    }

    @GetMapping("/{id}")
    public UtilisateurResponse obtenir(@PathVariable UUID id) {
        return service.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<UtilisateurResponse> creer(@Valid @RequestBody CreerUtilisateurRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(requete));
    }

    @PatchMapping("/{id}")
    public UtilisateurResponse modifier(@PathVariable UUID id, @Valid @RequestBody ModifierUtilisateurRequest requete) {
        return service.modifier(id, requete);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activer(@PathVariable UUID id) {
        service.activer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        service.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
