package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Departement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DepartementRepository extends JpaRepository<Departement, UUID>, JpaSpecificationExecutor<Departement> {

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, UUID id);

    boolean existsByResponsableId(UUID responsableId);

    @Override
    @EntityGraph(attributePaths = "responsable")
    Page<Departement> findAll(Specification<Departement> specification, Pageable pageable);
}
