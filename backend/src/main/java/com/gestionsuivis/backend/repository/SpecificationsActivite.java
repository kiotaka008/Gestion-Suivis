package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtres des activités : périmètre de visibilité selon le rôle, puis critères de recherche.
 */
public final class SpecificationsActivite {

    private SpecificationsActivite() {
    }

    /** Activités visibles (voir {@code ControleAcces}). */
    public static Specification<Activite> visiblesPar(UtilisateurConnecte u) {
        if (u.estAdmin()) {
            return Specification.unrestricted();
        }
        return (racine, requete, cb) -> {
            Join<Activite, Projet> projet = racine.join("projet", JoinType.LEFT);
            Join<Activite, Utilisateur> responsable = racine.join("responsable", JoinType.LEFT);
            List<Predicate> ou = new ArrayList<>();
            ou.add(cb.equal(responsable.get("id"), u.id()));
            ou.add(cb.equal(projet.get("chefDeProjet").get("id"), u.id()));
            if (u.estManager() && u.departementId() != null) {
                Join<Projet, Departement> departement = projet.join("departement", JoinType.LEFT);
                ou.add(cb.equal(departement.get("id"), u.departementId()));
            }
            return cb.or(ou.toArray(Predicate[]::new));
        };
    }

    public static Specification<Activite> filtres(String recherche, StatutActivite statut, Priorite priorite,
                                                  UUID projetId, UUID responsableId, Boolean enRetard,
                                                  LocalDate aujourdHui) {
        return (racine, requete, cb) -> {
            List<Predicate> et = new ArrayList<>();
            if (recherche != null && !recherche.isBlank()) {
                String motif = "%" + recherche.trim().toLowerCase() + "%";
                et.add(cb.or(cb.like(cb.lower(racine.get("titre")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("description"), "")), motif)));
            }
            if (statut != null) {
                et.add(cb.equal(racine.get("statut"), statut));
            }
            if (priorite != null) {
                et.add(cb.equal(racine.get("priorite"), priorite));
            }
            if (projetId != null) {
                et.add(cb.equal(racine.get("projet").get("id"), projetId));
            }
            if (responsableId != null) {
                et.add(cb.equal(racine.get("responsable").get("id"), responsableId));
            }
            if (enRetard != null) {
                Predicate retard = cb.and(cb.notEqual(racine.get("statut"), StatutActivite.TERMINE),
                        cb.or(cb.equal(racine.get("statut"), StatutActivite.EN_RETARD),
                                cb.and(cb.isNotNull(racine.get("dateEcheance")),
                                        cb.lessThan(racine.get("dateEcheance"), aujourdHui))));
                et.add(enRetard ? retard : cb.not(retard));
            }
            return cb.and(et.toArray(Predicate[]::new));
        };
    }
}
