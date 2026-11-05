package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.StatutActivite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ActiviteRepository extends JpaRepository<Activite, UUID>, JpaSpecificationExecutor<Activite> {

    boolean existsByProjetIdAndResponsableId(UUID projetId, UUID responsableId);

    List<Activite> findByProjetId(UUID projetId);

    long countByResponsableId(UUID responsableId);

    @EntityGraph(attributePaths = {"projet", "responsable"})
    List<Activite> findByStatutNotAndDateEcheanceBefore(StatutActivite statut, LocalDate date);

    @EntityGraph(attributePaths = {"projet", "responsable"})
    List<Activite> findByStatutNotAndDateEcheanceBetween(StatutActivite statut, LocalDate debut, LocalDate fin);

    /** Avancement moyen d'un projet ; une activité terminée compte pour 100 %. {@code null} sans activité. */
    @Query("""
            select avg(case when a.statut = com.gestionsuivis.backend.entity.StatutActivite.TERMINE
                            then 100 else a.avancement end)
            from Activite a where a.projet.id = :projet""")
    Double avancementMoyen(@Param("projet") UUID projetId);

    @Override
    @EntityGraph(attributePaths = {"projet", "responsable"})
    Page<Activite> findAll(Specification<Activite> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"projet", "responsable"})
    List<Activite> findAll(Specification<Activite> specification);

    @EntityGraph(attributePaths = {"projet", "responsable"})
    List<Activite> findByProjetIdIn(Collection<UUID> projets);
}
