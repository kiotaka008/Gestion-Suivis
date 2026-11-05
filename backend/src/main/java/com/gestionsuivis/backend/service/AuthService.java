package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.config.ExecuteurOrganisation;
import com.gestionsuivis.backend.dto.auth.ChangerMotDePasseRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionRequest;
import com.gestionsuivis.backend.dto.auth.InscriptionRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Organisation;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.IdentifiantsInvalidesException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.OrganisationRepository;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.JwtService;
import com.gestionsuivis.backend.security.SecuriteProperties;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Authentification : connexion (JWT), profil connecté, changement de mot de passe
 * et inscription libre (désactivée par défaut). Les mots de passe ne sont jamais
 * journalisés ni renvoyés.
 * <p>
 * Le schéma est multi-organisation et l'email n'est unique qu'au sein d'une
 * organisation : à la connexion, le compte est recherché dans chaque organisation
 * (ou dans celle indiquée par {@code organization}). Chaque recherche est faite dans
 * le contexte de l'organisation concernée, ce qui reste compatible avec le RLS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurs;
    private final OrganisationRepository organisations;
    private final ExecuteurOrganisation executeur;
    private final PasswordEncoder encodeur;
    private final JwtService jwtService;
    private final UtilisateurMapper mapper;
    private final UtilisateurCourant utilisateurCourant;
    private final SecuriteProperties securite;
    private final AuditService audit;
    private final Clock horloge;

    /** Hash BCrypt factice : rend la durée d'un échec identique, que l'email existe ou non. */
    private volatile String hashFactice;

    /** Résultat d'une connexion réussie : l'utilisateur et le jeton à poser dans le cookie. */
    public record ResultatConnexion(UtilisateurResponse utilisateur, String jeton) {
        @Override
        public String toString() {
            return "ResultatConnexion[utilisateur=" + utilisateur.email() + ", jeton=***]";
        }
    }

    public ResultatConnexion connecter(ConnexionRequest requete) {
        String email = normaliser(requete.email());
        List<Utilisateur> valides = new ArrayList<>();
        boolean trouve = false;
        for (UUID organisation : organisationsCandidates(requete.organization())) {
            Utilisateur candidat = executeur.lire(organisation, () -> utilisateurs.findByEmailIgnoreCase(email))
                    .orElse(null);
            if (candidat != null) {
                trouve = true;
                if (encodeur.matches(requete.password(), candidat.getMotDePasseHash()) && candidat.isActif()) {
                    valides.add(candidat);
                }
            }
        }
        if (!trouve) {
            encodeur.matches(requete.password(), hashFactice());
        }
        if (valides.isEmpty()) {
            log.info("Échec de connexion");
            throw new IdentifiantsInvalidesException();
        }
        if (valides.size() > 1) {
            throw new RequeteInvalideException("organization",
                    "Ce compte existe dans plusieurs organisations : précisez l'organisation.");
        }
        Utilisateur utilisateur = executeur.ecrire(valides.getFirst().getOrganisationId(), () -> {
            Utilisateur connecte = utilisateurs.findById(valides.getFirst().getId())
                    .orElseThrow(IdentifiantsInvalidesException::new);
            connecte.setDerniereConnexion(horloge.instant());
            audit.journaliser(connecte.getId(), "login", AuditService.UTILISATEUR, connecte.getId());
            return connecte;
        });
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

    public UtilisateurResponse inscrire(InscriptionRequest requete) {
        if (!securite.inscriptionOuverte()) {
            throw new AccesInterditException("L'inscription libre est désactivée. Contactez un administrateur.");
        }
        List<UUID> candidates = organisationsCandidates(requete.organization());
        if (candidates.size() != 1) {
            throw new RequeteInvalideException("organization", candidates.isEmpty()
                    ? "Aucune organisation n'est disponible."
                    : "Précisez l'organisation à rejoindre.");
        }
        String email = normaliser(requete.email());
        return executeur.ecrire(candidates.getFirst(), () -> {
            if (utilisateurs.existsByEmailIgnoreCase(email)) {
                throw new ConflitMetierException("Cet email est déjà utilisé.");
            }
            Utilisateur utilisateur = new Utilisateur();
            utilisateur.setNom(requete.name().trim());
            utilisateur.setEmail(email);
            utilisateur.setMotDePasseHash(encodeur.encode(requete.password()));
            utilisateur.setRole(Role.EMPLOYE);
            utilisateur.setActif(true);
            return mapper.versDto(utilisateurs.saveAndFlush(utilisateur));
        });
    }

    /** Organisation désignée par son slug, ou toutes les organisations si aucun slug n'est fourni. */
    private List<UUID> organisationsCandidates(String slug) {
        if (slug != null && !slug.isBlank()) {
            return organisations.findBySlugIgnoreCase(slug.trim()).map(Organisation::getId).map(List::of)
                    .orElseGet(List::of);
        }
        return organisations.findAll().stream().map(Organisation::getId).toList();
    }

    private Utilisateur charger(UUID id) {
        return utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", id));
    }

    private String hashFactice() {
        if (hashFactice == null) {
            hashFactice = encodeur.encode(UUID.randomUUID().toString());
        }
        return hashFactice;
    }

    static String normaliser(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
