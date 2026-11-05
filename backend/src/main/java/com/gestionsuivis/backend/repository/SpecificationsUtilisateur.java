package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtres des utilisateurs : ADMIN et MANAGER voient toute leur organisation,
 * EMPLOYE lui-même.
 */
public final class SpecificationsUtilisateur {

    private SpecificationsUtilisateur() {
    }

    public static Specification<Utilisateur> visiblesPar(UtilisateurConnecte u) {
        if (u.estAdmin() || u.estManager()) {
            return Specification.unrestricted();
        }
        return (racine, requete, cb) -> cb.equal(racine.get("id"), u.id());
    }

    public static Specification<Utilisateur> filtres(String recherche, Role role, Boolean actif) {
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
            return cb.and(et.toArray(Predicate[]::new));
        };
    }
}
