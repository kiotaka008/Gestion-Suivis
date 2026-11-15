package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.entity.JournalAudit;
import com.gestionsuivis.backend.repository.JournalAuditRepository;
import com.gestionsuivis.backend.security.ContexteOrganisation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Journal d'audit ({@code audit_logs}) des actions sensibles : connexion, création,
 * modification et suppression de projets, d'activités et d'utilisateurs, membres des projets,
 * changement de mot de passe. L'action est enregistrée au format du jeu de données officiel :
 * {@code <type>.<action>} (ex. {@code project.created}, {@code user.login}).
 * <p>
 * L'organisation est celle du contexte courant. Les détails ({@code metadata}) se limitent aux
 * clés de {@link #CLES_AUTORISEES} : noms des champs modifiés (jamais leurs valeurs), statut,
 * identifiants et titre d'activité. Aucun email, mot de passe ou autre donnée personnelle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String PROJET = "project";
    public static final String ACTIVITE = "activity";
    public static final String UTILISATEUR = "user";

    /** Noms (API) des champs modifiés. */
    public static final String CLE_CHAMPS = "fields";
    /** Nouveau statut (valeur JSON de l'énumération). */
    public static final String CLE_STATUT = "status";
    /** Activité concernée par une action sur un projet (création, suppression). */
    public static final String CLE_ACTIVITE = "activityId";
    public static final String CLE_TITRE_ACTIVITE = "activityTitle";
    /** Membre ajouté ou retiré d'un projet. */
    public static final String CLE_MEMBRE = "memberId";

    static final Set<String> CLES_AUTORISEES =
            Set.of(CLE_CHAMPS, CLE_STATUT, CLE_ACTIVITE, CLE_TITRE_ACTIVITE, CLE_MEMBRE);

    private final JournalAuditRepository journal;

    /**
     * @param action      action au participe passé ({@code created}, {@code updated}, {@code deleted}...)
     * @param typeEntite  {@link #PROJET}, {@link #ACTIVITE} ou {@link #UTILISATEUR}
     */
    @Transactional
    public void journaliser(UUID auteurId, String action, String typeEntite, UUID entiteId) {
        journaliser(auteurId, action, typeEntite, entiteId, Map.of());
    }

    /** Variante avec détails ; les clés hors de {@link #CLES_AUTORISEES} et les valeurs nulles sont ignorées. */
    @Transactional
    public void journaliser(UUID auteurId, String action, String typeEntite, UUID entiteId,
                            Map<String, ?> details) {
        if (ContexteOrganisation.courante().isEmpty()) {
            log.debug("Audit ignoré hors organisation : {} {}", action, typeEntite);
            return;
        }
        JournalAudit entree = new JournalAudit();
        entree.setUtilisateurId(auteurId);
        entree.setAction(typeEntite + "." + action);
        entree.setTypeEntite(typeEntite);
        entree.setEntiteId(entiteId);
        entree.setMetadonnees(filtrer(details));
        journal.save(entree);
    }

    /**
     * Détails à partir de paires clé, valeur ({@code details(CLE_ACTIVITE, id, CLE_TITRE_ACTIVITE, titre)}).
     * Contrairement à {@code Map.of}, accepte les valeurs nulles (ignorées ensuite par le filtre).
     */
    public static Map<String, Object> details(Object... clesEtValeurs) {
        if (clesEtValeurs.length % 2 != 0) {
            throw new IllegalArgumentException("Nombre pair d'arguments attendu (clé, valeur)");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < clesEtValeurs.length; i += 2) {
            details.put((String) clesEtValeurs[i], clesEtValeurs[i + 1]);
        }
        return details;
    }

    /** Ajoute {@code champ} à la liste si la nouvelle valeur est fournie et diffère de l'ancienne. */
    public static void siModifie(List<String> champs, String champ, Object avant, Object apres) {
        if (apres != null && !Objects.equals(avant, apres)) {
            champs.add(champ);
        }
    }

    static Map<String, Object> filtrer(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        Map<String, Object> filtres = new LinkedHashMap<>();
        details.forEach((cle, valeur) -> {
            if (valeur == null || !CLES_AUTORISEES.contains(cle)) {
                return;
            }
            if (valeur instanceof List<?> liste && liste.isEmpty()) {
                return;
            }
            filtres.put(cle, valeur instanceof UUID id ? id.toString() : valeur);
        });
        return filtres.isEmpty() ? null : filtres;
    }
}
