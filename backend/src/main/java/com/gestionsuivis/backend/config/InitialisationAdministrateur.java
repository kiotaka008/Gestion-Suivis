package com.gestionsuivis.backend.config;

import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le compte administrateur initial au démarrage s'il n'existe pas encore
 * (aucun doublon : la recherche se fait sur l'email, sans tenir compte de la casse).
 * Le mot de passe est encodé avec BCrypt et n'est jamais journalisé ; il doit être
 * changé après la première connexion ({@code POST /api/auth/change-password}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialisationAdministrateur implements ApplicationRunner {

    static final String EMAIL = "admin@gestion-suivis.com";
    static final String MOT_DE_PASSE_INITIAL = "Admin@2026";
    static final String NOM = "Administrateur";

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (utilisateurs.existsByEmailIgnoreCase(EMAIL)) {
            log.info("Compte administrateur initial déjà présent : aucune création.");
            return;
        }
        Utilisateur administrateur = new Utilisateur();
        administrateur.setEmail(EMAIL);
        administrateur.setNom(NOM);
        administrateur.setRole(Role.ADMIN);
        administrateur.setActif(true);
        administrateur.setMotDePasseHash(encodeur.encode(MOT_DE_PASSE_INITIAL));
        utilisateurs.save(administrateur);
        log.warn("Compte administrateur initial créé ({}). Changez son mot de passe dès la première connexion.", EMAIL);
    }
}
