package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.MembreProjet;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtres des projets : périmètre de visibilité selon le rôle, puis critères de recherche.
 * Le filtrage par organisation est appliqué en plus par Hibernate ({@code @TenantId}).
 */
public final class SpecificationsProjet {

    private SpecificationsProjet() {
    }

    /** Projets visibles (voir {@code ControleAcces}). */
    public static Specification<Projet> visiblesPar(UtilisateurConnecte u) {
        if (u.estAdmin() || u.estManager()) {
            return Specification.unrestricted();
        }
        return (racine, requete, cb) -> {
            List<Predicate> ou = new ArrayList<>();
            ou.add(cb.equal(racine.get("chefDeProjet").get("id"), u.id()));

            Subquery<Integer> membre = requete.subquery(Integer.class);
            Root<MembreProjet> m = membre.from(MembreProjet.class);
            membre.select(cb.literal(1)).where(cb.equal(m.get("projet").get("id"), racine.get("id")),
                    cb.equal(m.get("utilisateur").get("id"), u.id()));
            ou.add(cb.exists(membre));

            Subquery<UUID> affecte = requete.subquery(UUID.class);
            Root<Activite> a = affecte.from(Activite.class);
            affecte.select(a.get("id")).where(cb.equal(a.get("projet").get("id"), racine.get("id")),
                    cb.equal(a.get("responsable").get("id"), u.id()));
            ou.add(cb.exists(affecte));
            return cb.or(ou.toArray(Predicate[]::new));
        };
    }

    public static Specification<Projet> filtres(String recherche, StatutProjet statut, Priorite priorite, UUID chefId) {
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
            if (chefId != null) {
                et.add(cb.equal(racine.get("chefDeProjet").get("id"), chefId));
            }
            return cb.and(et.toArray(Predicate[]::new));
        };
    }
}
