package com.gestionsuivis.backend.support;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.RoleMembreProjet;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.security.UtilisateurConnecte;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fabrique d'objets de test en mémoire (aucune base de données). */
public final class Donnees {

    /** Organisation (tenant) commune aux objets de test. */
    public static final UUID ORGANISATION = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private Donnees() {
    }

    public static Utilisateur utilisateur(String nom, Role role) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(UUID.randomUUID());
        utilisateur.setOrganisationId(ORGANISATION);
        utilisateur.setNom(nom);
        utilisateur.setEmail(nom.toLowerCase().replace(' ', '.') + "@exemple.com");
        utilisateur.setRole(role);
        utilisateur.setActif(true);
        utilisateur.setMotDePasseHash("$2a$10$hash");
        utilisateur.setDateCreation(Instant.parse("2025-01-10T09:00:00Z"));
        return utilisateur;
    }

    public static UtilisateurConnecte connecte(Utilisateur utilisateur) {
        return new UtilisateurConnecte(utilisateur.getId(), utilisateur.getEmail(), utilisateur.getRole(),
                utilisateur.getOrganisationId());
    }

    /** Projet dont le chef est membre avec le rôle {@code owner}, comme dans le schéma officiel. */
    public static Projet projet(String nom, Utilisateur chef) {
        Projet projet = new Projet();
        projet.setId(UUID.randomUUID());
        projet.setOrganisationId(ORGANISATION);
        projet.setNom(nom);
        projet.setStatut(StatutProjet.EN_COURS);
        projet.setPriorite(Priorite.MOYENNE);
        projet.setChefDeProjet(chef);
        projet.ajouterMembre(chef, RoleMembreProjet.PROPRIETAIRE);
        projet.setDateCreation(Instant.parse("2025-02-01T09:00:00Z"));
        projet.setDateModification(Instant.parse("2025-02-01T09:00:00Z"));
        return projet;
    }

    public static Activite activite(String titre, Projet projet, Utilisateur responsable, StatutActivite statut,
                                    LocalDate echeance) {
        Activite activite = new Activite();
        activite.setId(UUID.randomUUID());
        activite.setOrganisationId(ORGANISATION);
        activite.setTitre(titre);
        activite.setProjet(projet);
        activite.setResponsable(responsable);
        activite.setStatut(statut);
        activite.setPriorite(Priorite.MOYENNE);
        activite.setDateEcheance(echeance);
        activite.setDateCreation(Instant.parse("2025-03-01T08:00:00Z"));
        activite.setDateModification(Instant.parse("2025-03-01T08:00:00Z"));
        return activite;
    }
}
