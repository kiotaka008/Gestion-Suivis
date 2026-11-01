package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Commentaire;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface CommentaireRepository extends JpaRepository<Commentaire, UUID> {

    @EntityGraph(attributePaths = "auteur")
    Page<Commentaire> findByActiviteId(UUID activiteId, Pageable pageable);

    boolean existsByAuteurId(UUID auteurId);

    @Modifying
    @Query("delete from Commentaire c where c.activite.id in :activites")
    void supprimerParActivites(@Param("activites") Collection<UUID> activites);
}
