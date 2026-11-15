package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.dto.auth.ChangerMotDePasseRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionGoogleRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionResponse;
import com.gestionsuivis.backend.dto.auth.InscriptionRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.exception.IdentifiantsInvalidesException;
import com.gestionsuivis.backend.security.CookieAuthentification;
import com.gestionsuivis.backend.security.LimiteurConnexion;
import com.gestionsuivis.backend.security.SecuriteProperties;
import com.gestionsuivis.backend.security.VerificateurGoogle;
import com.gestionsuivis.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.Map;

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
    private final VerificateurGoogle google;
    private final LimiteurConnexion limiteur;

    /**
     * Connexion : 200 {@code { user }} + cookie, 401 si les identifiants sont incorrects,
     * 429 après trop d'échecs (voir {@link LimiteurConnexion}).
     */
    @PostMapping("/login")
    public ResponseEntity<ConnexionResponse> connecter(@Valid @RequestBody ConnexionRequest requete,
                                                       HttpServletRequest http) {
        // Adresse du client telle que résolue par le serveur (X-Forwarded-For n'est pris en compte
        // que si les proxies de confiance sont configurés : server.forward-headers-strategy).
        String ip = http.getRemoteAddr();
        limiteur.verifier(requete.email(), ip);
        AuthService.ResultatConnexion resultat;
        try {
            resultat = authService.connecter(requete);
        } catch (IdentifiantsInvalidesException e) {
            limiteur.enregistrerEchec(requete.email(), ip);
            throw e;
        }
        limiteur.enregistrerSucces(requete.email(), ip);
        return avecCookie(resultat);
    }

    private ResponseEntity<ConnexionResponse> avecCookie(AuthService.ResultatConnexion resultat) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieAuthentification
                        .creer(resultat.jeton(), securite.dureeJeton(), securite.cookieSecurise()).toString())
                .body(new ConnexionResponse(resultat.utilisateur()));
    }

    /**
     * Connexion avec Google : {@code { credential, organization? }} où {@code credential} est le
     * jeton d'identité de Google Identity Services. Même réponse que {@code /login}.
     */
    @PostMapping("/google")
    public ResponseEntity<ConnexionResponse> connecterAvecGoogle(@Valid @RequestBody ConnexionGoogleRequest requete,
                                                                 HttpServletRequest http) {
        // L'email n'est connu qu'après vérification du jeton : seule la limite par adresse IP s'applique.
        String ip = http.getRemoteAddr();
        limiteur.verifier(null, ip);
        try {
            return avecCookie(authService.connecterAvecGoogle(requete));
        } catch (IdentifiantsInvalidesException e) {
            limiteur.enregistrerEchec(null, ip);
            throw e;
        }
    }

    /** Configuration publique de la connexion Google : {@code { enabled, clientId }}. */
    @GetMapping("/google/config")
    public Map<String, Object> configurationGoogle() {
        return Map.of("enabled", google.estActive(), "clientId", google.clientId().orElse(""));
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
