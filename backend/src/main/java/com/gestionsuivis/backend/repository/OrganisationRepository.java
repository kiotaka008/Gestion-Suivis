package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Organisation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Organisations (tenants) : table non filtrée par organisation ni par le RLS. */
public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {

    Optional<Organisation> findBySlugIgnoreCase(String slug);

    /**
     * Verrouille la ligne de l'organisation jusqu'à la fin de la transaction courante : point de
     * synchronisation des opérations qui peuvent retirer un administrateur actif. Deux transactions
     * qui verrouillent la même organisation s'attendent. Sous PostgreSQL, le dialecte d'Hibernate
     * 7.4 traduit en principe {@code PESSIMISTIC_WRITE} par {@code FOR NO KEY UPDATE}, compatible
     * avec le {@code FOR KEY SHARE} posé par l'insertion d'un utilisateur (à confirmer sur une
     * vraie base).
     * Les requêtes exécutées ensuite dans la transaction voient les validations précédentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organisation o where o.id = :id")
    Optional<Organisation> verrouiller(@Param("id") UUID id);
}
