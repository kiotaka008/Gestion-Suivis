package com.gestionsuivis.backend.support;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.security.UtilisateurConnecte;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fabrique d'objets de test en mémoire (aucune base de données). */
public final class Donnees {

    private Donnees() {
    }

    public static Departement departement(String nom) {
        Departement departement = new Departement();
        departement.setId(UUID.randomUUID());
        departement.setNom(nom);
        return departement;
    }

    public static Utilisateur utilisateur(String nom, Role role, Departement departement) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(UUID.randomUUID());
        utilisateur.setNom(nom);
        utilisateur.setEmail(nom.toLowerCase().replace(' ', '.') + "@exemple.com");
        utilisateur.setRole(role);
        utilisateur.setActif(true);
        utilisateur.setDepartement(departement);
        utilisateur.setMotDePasseHash("$2a$10$hash");
        utilisateur.setDateCreation(Instant.parse("2025-01-10T09:00:00Z"));
        return utilisateur;
    }

    public static UtilisateurConnecte connecte(Utilisateur utilisateur) {
        return new UtilisateurConnecte(utilisateur.getId(), utilisateur.getEmail(), utilisateur.getRole(),
                utilisateur.getDepartement() == null ? null : utilisateur.getDepartement().getId());
    }

    public static Projet projet(String nom, Utilisateur chef, Departement departement) {
        Projet projet = new Projet();
        projet.setId(UUID.randomUUID());
        projet.setNom(nom);
        projet.setStatut(StatutProjet.EN_COURS);
        projet.setChefDeProjet(chef);
        projet.setDepartement(departement);
        projet.setDateCreation(Instant.parse("2025-02-01T09:00:00Z"));
        projet.setDateModification(Instant.parse("2025-02-01T09:00:00Z"));
        return projet;
    }

    public static Activite activite(String titre, Projet projet, Utilisateur responsable, StatutActivite statut,
                                    LocalDate echeance) {
        Activite activite = new Activite();
        activite.setId(UUID.randomUUID());
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

    public static Activite avecHeures(Activite activite, String prevues, String reelles) {
        activite.setHeuresPrevues(prevues == null ? null : new BigDecimal(prevues));
        activite.setHeuresReelles(reelles == null ? null : new BigDecimal(reelles));
        return activite;
    }
}
