package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.dto.auth.ChangerMotDePasseRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionResponse;
import com.gestionsuivis.backend.dto.auth.InscriptionRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.security.CookieAuthentification;
import com.gestionsuivis.backend.security.SecuriteProperties;
import com.gestionsuivis.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentification, alignée sur {@code src/services/authService.ts} du frontend.
 * Le JWT est transmis dans le cookie HttpOnly {@code access_token} ; le corps de
 * la connexion ne contient que {@code { user }}.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecuriteProperties securite;

    /** Connexion : 200 {@code { user }} + cookie, 401 si les identifiants sont incorrects. */
    @PostMapping("/login")
    public ResponseEntity<ConnexionResponse> connecter(@Valid @RequestBody ConnexionRequest requete) {
        AuthService.ResultatConnexion resultat = authService.connecter(requete);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieAuthentification
                        .creer(resultat.jeton(), securite.dureeJeton(), securite.cookieSecurise()).toString())
                .body(new ConnexionResponse(resultat.utilisateur()));
    }

    /** Déconnexion : efface le cookie (204), même si la session a déjà expiré. */
    @PostMapping("/logout")
    public ResponseEntity<Void> deconnecter() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, CookieAuthentification.supprimer(securite.cookieSecurise()).toString())
                .build();
    }

    /** Utilisateur connecté : 200, ou 401 sans session valide. */
    @GetMapping("/me")
    public UtilisateurResponse moi() {
        return authService.moi();
    }

    /** Pose le cookie {@code XSRF-TOKEN} (utile au premier chargement du frontend). */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changerMotDePasse(@Valid @RequestBody ChangerMotDePasseRequest requete) {
        authService.changerMotDePasse(requete);
        return ResponseEntity.noContent().build();
    }

    /** Inscription libre (EMPLOYE), uniquement si {@code AUTH_REGISTRATION_ENABLED=true}, sinon 403. */
    @PostMapping("/register")
    public ResponseEntity<UtilisateurResponse> inscrire(@Valid @RequestBody InscriptionRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.inscrire(requete));
    }
}
