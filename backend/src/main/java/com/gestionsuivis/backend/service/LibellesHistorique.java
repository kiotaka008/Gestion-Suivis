package com.gestionsuivis.backend.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Phrases françaises de l'historique, à partir du code d'action et des détails non sensibles. */
final class LibellesHistorique {

    private LibellesHistorique() {
    }

    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("project.created", "Projet créé"),
            Map.entry("project.updated", "Projet modifié"),
            Map.entry("project.deleted", "Projet supprimé"),
            Map.entry("project.activity_created", "Activité ajoutée"),
            Map.entry("project.activity_deleted", "Activité supprimée"),
            Map.entry("project.member_added", "Membre ajouté"),
            Map.entry("project.member_removed", "Membre retiré"),
            Map.entry("activity.created", "Activité créée"),
            Map.entry("activity.updated", "Activité modifiée"),
            Map.entry("activity.deleted", "Activité supprimée"),
            Map.entry("user.created", "Compte créé"),
            Map.entry("user.updated", "Compte modifié"),
            Map.entry("user.activated", "Compte activé"),
            Map.entry("user.deactivated", "Compte désactivé"),
            Map.entry("user.deleted", "Compte supprimé"),
            Map.entry("user.login", "Connexion"),
            Map.entry("user.login.google", "Connexion avec Google"),
            Map.entry("user.password_changed", "Mot de passe modifié"));

    private static final Map<String, String> CHAMPS = Map.ofEntries(
            Map.entry("name", "nom"),
            Map.entry("title", "titre"),
            Map.entry("description", "description"),
            Map.entry("status", "statut"),
            Map.entry("priority", "priorité"),
            Map.entry("startDate", "date de début"),
            Map.entry("dueDate", "échéance"),
            Map.entry("ownerId", "chef de projet"),
            Map.entry("memberIds", "membres"),
            Map.entry("progress", "progression"),
            Map.entry("assigneeId", "responsable"),
            Map.entry("email", "email"),
            Map.entry("role", "rôle"),
            Map.entry("avatarUrl", "photo"));

    private static final Map<String, String> STATUTS_PROJET = Map.of(
            "active", "En cours", "on_hold", "En attente", "completed", "Terminé", "archived", "Archivé");

    private static final Map<String, String> STATUTS_ACTIVITE = Map.of(
            "todo", "À faire", "in_progress", "En cours", "done", "Terminée", "blocked", "Bloquée");

    static String decrire(String action, String typeEntite, List<String> champs, String statut,
                          String titreActivite, String nomMembre) {
        String base = ACTIONS.getOrDefault(action, action);
        if (titreActivite != null && action.startsWith("project.activity_")) {
            return base + " : « " + titreActivite + " »";
        }
        if (action.startsWith("project.member_")) {
            return base + (nomMembre != null ? " : " + nomMembre : "");
        }
        if (champs.isEmpty()) {
            return base;
        }
        Map<String, String> statuts = "activity".equals(typeEntite) ? STATUTS_ACTIVITE : STATUTS_PROJET;
        String liste = champs.stream()
                .map(champ -> {
                    String libelle = CHAMPS.getOrDefault(champ, champ);
                    return "status".equals(champ) && statut != null
                            ? libelle + " (" + statuts.getOrDefault(statut, statut) + ")"
                            : libelle;
                })
                .collect(Collectors.joining(", "));
        return base + " : " + liste;
    }
}
