package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Activite;
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
 * Le filtrage par organisation est appliqué en plus par Hibernate ({@code @TenantId}).
 */
public final class SpecificationsActivite {

    private SpecificationsActivite() {
    }

    /** Activités visibles (voir {@code ControleAcces}). */
    public static Specification<Activite> visiblesPar(UtilisateurConnecte u) {
        if (u.estAdmin() || u.estManager()) {
            return Specification.unrestricted();
        }
        return (racine, requete, cb) -> {
            Join<Activite, Projet> projet = racine.join("projet", JoinType.LEFT);
            Join<Activite, Utilisateur> responsable = racine.join("responsable", JoinType.LEFT);
            return cb.or(cb.equal(responsable.get("id"), u.id()),
                    cb.equal(projet.get("chefDeProjet").get("id"), u.id()));
        };
    }

    /** Activité en retard : non terminée et échéance dépassée. */
    public static Specification<Activite> enRetard(LocalDate aujourdHui) {
        return (racine, requete, cb) -> cb.and(cb.notEqual(racine.get("statut"), StatutActivite.TERMINE),
                cb.isNotNull(racine.get("dateEcheance")), cb.lessThan(racine.get("dateEcheance"), aujourdHui));
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
                Predicate retard = enRetard(aujourdHui).toPredicate(racine, requete, cb);
                et.add(enRetard ? retard : cb.not(retard));
            }
            return cb.and(et.toArray(Predicate[]::new));
        };
    }
}
