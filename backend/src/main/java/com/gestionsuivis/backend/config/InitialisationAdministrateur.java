package com.gestionsuivis.backend.config;

import com.gestionsuivis.backend.entity.Organisation;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.repository.OrganisationRepository;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Sur une base vide (aucune organisation), crée l'organisation initiale et son
 * compte administrateur, pour pouvoir se connecter. Si la base contient déjà des
 * organisations (par exemple le jeu de données de développement
 * {@code 20260930_002_projectmanager_dev_seed.sql}), rien n'est créé.
 * Le mot de passe est encodé avec BCrypt et n'est jamais journalisé ; il doit être
 * changé après la première connexion ({@code POST /api/auth/change-password}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialisationAdministrateur implements ApplicationRunner {

    static final String NOM_ORGANISATION = "Gestion-Suivis";
    static final String SLUG_ORGANISATION = "gestion-suivis";
    static final String EMAIL = "admin@gestion-suivis.com";
    static final String MOT_DE_PASSE_INITIAL = "Admin@2026";
    static final String NOM = "Administrateur";

    private final OrganisationRepository organisations;
    private final UtilisateurRepository utilisateurs;
    private final ExecuteurOrganisation executeur;
    private final PasswordEncoder encodeur;

    @Override
    public void run(ApplicationArguments arguments) {
        if (organisations.count() > 0) {
            log.info("Organisations déjà présentes : aucun compte initial créé.");
            return;
        }
        Organisation organisation = new Organisation();
        organisation.setNom(NOM_ORGANISATION);
        organisation.setSlug(SLUG_ORGANISATION);
        Organisation creee = organisations.save(organisation);

        executeur.ecrire(creee.getId(), () -> {
            Utilisateur administrateur = new Utilisateur();
            administrateur.setEmail(EMAIL);
            administrateur.setNom(NOM);
            administrateur.setRole(Role.ADMIN);
            administrateur.setActif(true);
            administrateur.setMotDePasseHash(encodeur.encode(MOT_DE_PASSE_INITIAL));
            utilisateurs.save(administrateur);
        });
        log.warn("Organisation « {} » et compte administrateur initial créés ({}). "
                + "Changez son mot de passe dès la première connexion.", SLUG_ORGANISATION, EMAIL);
    }
}
