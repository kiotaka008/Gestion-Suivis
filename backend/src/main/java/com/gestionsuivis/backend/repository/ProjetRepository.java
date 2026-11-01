package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Projet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ProjetRepository extends JpaRepository<Projet, UUID>, JpaSpecificationExecutor<Projet> {

    boolean existsByIdAndMembresId(UUID projetId, UUID utilisateurId);

    long countByDepartementId(UUID departementId);

    boolean existsByChefDeProjetId(UUID utilisateurId);

    List<Projet> findByMembresId(UUID utilisateurId);

    @Override
    @EntityGraph(attributePaths = {"chefDeProjet", "departement"})
    Page<Projet> findAll(Specification<Projet> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"chefDeProjet", "departement"})
    List<Projet> findAll(Specification<Projet> specification);
}
