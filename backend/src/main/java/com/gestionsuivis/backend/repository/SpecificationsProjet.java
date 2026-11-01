package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtres des projets : périmètre de visibilité selon le rôle, puis critères de recherche.
 */
public final class SpecificationsProjet {

    private SpecificationsProjet() {
    }

    /** Projets visibles (voir {@code ControleAcces}). */
    public static Specification<Projet> visiblesPar(UtilisateurConnecte u) {
        if (u.estAdmin()) {
            return Specification.unrestricted();
        }
        return (racine, requete, cb) -> {
            List<Predicate> ou = new ArrayList<>();
            ou.add(cb.equal(racine.get("chefDeProjet").get("id"), u.id()));

            Subquery<UUID> membre = requete.subquery(UUID.class);
            Root<Projet> p = membre.from(Projet.class);
            Join<Projet, Utilisateur> m = p.join("membres");
            membre.select(p.get("id")).where(cb.equal(p.get("id"), racine.get("id")), cb.equal(m.get("id"), u.id()));
            ou.add(cb.exists(membre));

            Subquery<UUID> affecte = requete.subquery(UUID.class);
            Root<Activite> a = affecte.from(Activite.class);
            affecte.select(a.get("id")).where(cb.equal(a.get("projet").get("id"), racine.get("id")),
                    cb.equal(a.get("responsable").get("id"), u.id()));
            ou.add(cb.exists(affecte));

            if (u.estManager() && u.departementId() != null) {
                ou.add(cb.equal(racine.join("departement", JoinType.LEFT).get("id"), u.departementId()));
            }
            return cb.or(ou.toArray(Predicate[]::new));
        };
    }

    public static Specification<Projet> filtres(String recherche, StatutProjet statut, Priorite priorite,
                                                UUID departementId, UUID chefId) {
        return (racine, requete, cb) -> {
            List<Predicate> et = new ArrayList<>();
            if (recherche != null && !recherche.isBlank()) {
                String motif = "%" + recherche.trim().toLowerCase() + "%";
                et.add(cb.or(cb.like(cb.lower(racine.get("nom")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("description"), "")), motif)));
            }
            if (statut != null) {
                et.add(cb.equal(racine.get("statut"), statut));
            }
            if (priorite != null) {
                et.add(cb.equal(racine.get("priorite"), priorite));
            }
            if (departementId != null) {
                et.add(cb.equal(racine.get("departement").get("id"), departementId));
            }
            if (chefId != null) {
                et.add(cb.equal(racine.get("chefDeProjet").get("id"), chefId));
            }
            return cb.and(et.toArray(Predicate[]::new));
        };
    }
}
