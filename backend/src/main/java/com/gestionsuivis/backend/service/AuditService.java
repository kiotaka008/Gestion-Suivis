package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.entity.JournalAudit;
import com.gestionsuivis.backend.repository.JournalAuditRepository;
import com.gestionsuivis.backend.security.ContexteOrganisation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Journal d'audit ({@code audit_logs}) des actions sensibles : connexion, création,
 * modification et suppression de projets, d'activités et d'utilisateurs. L'action est
 * enregistrée au format du jeu de données officiel : {@code <type>.<action>}
 * (ex. {@code project.created}, {@code user.login}).
 * L'organisation est celle du contexte courant ; aucune donnée personnelle n'est stockée.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String PROJET = "project";
    public static final String ACTIVITE = "activity";
    public static final String UTILISATEUR = "user";

    private final JournalAuditRepository journal;

    /**
     * @param action      action au participe passé ({@code created}, {@code updated}, {@code deleted}...)
     * @param typeEntite  {@link #PROJET}, {@link #ACTIVITE} ou {@link #UTILISATEUR}
     */
    @Transactional
    public void journaliser(UUID auteurId, String action, String typeEntite, UUID entiteId) {
        if (ContexteOrganisation.courante().isEmpty()) {
            log.debug("Audit ignoré hors organisation : {} {}", action, typeEntite);
            return;
        }
        JournalAudit entree = new JournalAudit();
        entree.setUtilisateurId(auteurId);
        entree.setAction(typeEntite + "." + action);
        entree.setTypeEntite(typeEntite);
        entree.setEntiteId(entiteId);
        journal.save(entree);
    }
}
