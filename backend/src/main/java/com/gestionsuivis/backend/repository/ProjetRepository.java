package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.StatutProjet;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjetRepository extends JpaRepository<Projet, UUID>, JpaSpecificationExecutor<Projet> {

    boolean existsByIdAndMembresUtilisateurId(UUID projetId, UUID utilisateurId);

    boolean existsByChefDeProjetId(UUID utilisateurId);

    List<Projet> findByMembresUtilisateurId(UUID utilisateurId);

    @Override
    @EntityGraph(attributePaths = "chefDeProjet")
    Page<Projet> findAll(Specification<Projet> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "chefDeProjet")
    List<Projet> findAll(Specification<Projet> specification);

    /**
     * Verrouille la ligne du projet jusqu'à la fin de la transaction (AUD-002, S3) : sérialise les
     * créations, modifications et suppressions d'activités d'un même projet et le recalcul de son
     * avancement. À appeler avant toute autre lecture du projet dans la transaction : le projet est
     * alors chargé par cette requête, après l'obtention du verrou. Sous PostgreSQL, le dialecte
     * d'Hibernate 7.4 traduit en principe {@code PESSIMISTIC_WRITE} par {@code FOR NO KEY UPDATE},
     * compatible avec le {@code FOR KEY SHARE} posé par l'insertion d'une activité (à confirmer sur
     * une vraie base).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Projet p where p.id = :id")
    Optional<Projet> verrouiller(@Param("id") UUID id);

    /**
     * Statut du projet lu en base par une requête (jamais celui d'une entité déjà en mémoire, qui
     * peut être périmée) : à appeler après {@link #verrouiller} pour appliquer la lecture seule des
     * projets clos (AUD-012) sur l'état validé le plus récent.
     */
    @Query("select p.statut from Projet p where p.id = :id")
    Optional<StatutProjet> statutActuel(@Param("id") UUID id);
}
