package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtres des utilisateurs : ADMIN voit tout, MANAGER son département, EMPLOYE lui-même.
 */
public final class SpecificationsUtilisateur {

    private SpecificationsUtilisateur() {
    }

    public static Specification<Utilisateur> visiblesPar(UtilisateurConnecte u) {
        if (u.estAdmin()) {
            return Specification.unrestricted();
        }
        return (racine, requete, cb) -> {
            Predicate soiMeme = cb.equal(racine.get("id"), u.id());
            if (u.estManager() && u.departementId() != null) {
                return cb.or(soiMeme,
                        cb.equal(racine.join("departement", JoinType.LEFT).get("id"), u.departementId()));
            }
            return soiMeme;
        };
    }

    public static Specification<Utilisateur> filtres(String recherche, Role role, Boolean actif, UUID departementId) {
        return (racine, requete, cb) -> {
            List<Predicate> et = new ArrayList<>();
            if (recherche != null && !recherche.isBlank()) {
                String motif = "%" + recherche.trim().toLowerCase() + "%";
                et.add(cb.or(cb.like(cb.lower(racine.get("nom")), motif),
                        cb.like(cb.lower(racine.get("email")), motif)));
            }
            if (role != null) {
                et.add(cb.equal(racine.get("role"), role));
            }
            if (actif != null) {
                et.add(cb.equal(racine.get("actif"), actif));
            }
            if (departementId != null) {
                et.add(cb.equal(racine.get("departement").get("id"), departementId));
            }
            return cb.and(et.toArray(Predicate[]::new));
        };
    }
}
