package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.auth.ChangerMotDePasseRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionRequest;
import com.gestionsuivis.backend.dto.auth.InscriptionRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.IdentifiantsInvalidesException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.JwtService;
import com.gestionsuivis.backend.security.SecuriteProperties;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Authentification : connexion (JWT), profil connecté, changement de mot de passe
 * et inscription libre (désactivée par défaut). Les mots de passe ne sont jamais
 * journalisés ni renvoyés.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final JwtService jwtService;
    private final UtilisateurMapper mapper;
    private final UtilisateurCourant utilisateurCourant;
    private final SecuriteProperties securite;

    /** Hash BCrypt factice : rend la durée d'un échec identique, que l'email existe ou non. */
    private volatile String hashFactice;

    /** Résultat d'une connexion réussie : l'utilisateur et le jeton à poser dans le cookie. */
    public record ResultatConnexion(UtilisateurResponse utilisateur, String jeton) {
        @Override
        public String toString() {
            return "ResultatConnexion[utilisateur=" + utilisateur.email() + ", jeton=***]";
        }
    }

    @Transactional(readOnly = true)
    public ResultatConnexion connecter(ConnexionRequest requete) {
        Optional<Utilisateur> trouve = utilisateurs.findByEmailIgnoreCase(normaliser(requete.email()));
        String hash = trouve.map(Utilisateur::getMotDePasseHash).orElseGet(this::hashFactice);
        boolean motDePasseCorrect = encodeur.matches(requete.password(), hash);
        if (trouve.isEmpty() || !motDePasseCorrect || !trouve.get().isActif()) {
            log.info("Échec de connexion");
            throw new IdentifiantsInvalidesException();
        }
        Utilisateur utilisateur = trouve.get();
        log.info("Connexion réussie de l'utilisateur {}", utilisateur.getId());
        return new ResultatConnexion(mapper.versDto(utilisateur), jwtService.generer(utilisateur));
    }

    @Transactional(readOnly = true)
    public UtilisateurResponse moi() {
        return mapper.versDto(charger(utilisateurCourant.obtenir().id()));
    }

    @Transactional
    public void changerMotDePasse(ChangerMotDePasseRequest requete) {
        Utilisateur utilisateur = charger(utilisateurCourant.obtenir().id());
        if (!encodeur.matches(requete.currentPassword(), utilisateur.getMotDePasseHash())) {
            throw new RequeteInvalideException("currentPassword", "Le mot de passe actuel est incorrect.");
        }
        if (requete.currentPassword().equals(requete.newPassword())) {
            throw new RequeteInvalideException("newPassword",
                    "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        utilisateur.setMotDePasseHash(encodeur.encode(requete.newPassword()));
        log.info("Mot de passe modifié pour l'utilisateur {}", utilisateur.getId());
    }

    @Transactional
    public UtilisateurResponse inscrire(InscriptionRequest requete) {
        if (!securite.inscriptionOuverte()) {
            throw new AccesInterditException("L'inscription libre est désactivée. Contactez un administrateur.");
        }
        String email = normaliser(requete.email());
        if (utilisateurs.existsByEmailIgnoreCase(email)) {
            throw new ConflitMetierException("Cet email est déjà utilisé.");
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(requete.name().trim());
        utilisateur.setEmail(email);
        utilisateur.setMotDePasseHash(encodeur.encode(requete.password()));
        utilisateur.setRole(Role.EMPLOYE);
        utilisateur.setActif(true);
        return mapper.versDto(utilisateurs.save(utilisateur));
    }

    private Utilisateur charger(java.util.UUID id) {
        return utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", id));
    }

    private String hashFactice() {
        if (hashFactice == null) {
            hashFactice = encodeur.encode(java.util.UUID.randomUUID().toString());
        }
        return hashFactice;
    }

    static String normaliser(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
