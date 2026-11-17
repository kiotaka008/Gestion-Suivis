package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Utilisateurs de l'organisation courante (l'email est unique par organisation). */
public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID>, JpaSpecificationExecutor<Utilisateur> {

    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    long countByRoleAndActifTrue(Role role);

    /**
     * Enregistre la date de dernière connexion d'un compte encore actif, par une mise à jour
     * ciblée de {@code last_login_at} : aucune autre colonne de {@code users} n'est réécrite, ce
     * qui préserve une désactivation, un changement de rôle ou de mot de passe concurrent.
     * PostgreSQL réévalue {@code actif = true} sur la dernière version validée de la ligne.
     * Ne modifie ni {@code updated_at} ni une éventuelle version (pas de {@code update versioned}).
     * Le contexte de persistance est vidé après la mise à jour : relire le compte ensuite.
     *
     * @return 1 si le compte actif a été mis à jour, 0 s'il est désactivé ou n'existe plus
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Utilisateur u set u.derniereConnexion = :instant where u.id = :id and u.actif = true")
    int enregistrerConnexion(@Param("id") UUID id, @Param("instant") Instant instant);
}
