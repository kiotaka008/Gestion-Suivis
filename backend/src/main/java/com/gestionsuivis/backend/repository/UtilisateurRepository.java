package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Utilisateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID>, JpaSpecificationExecutor<Utilisateur> {

    @EntityGraph(attributePaths = "departement")
    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    long countByDepartementId(UUID departementId);

    long countByRoleAndActifTrue(com.gestionsuivis.backend.entity.Role role);

    java.util.List<Utilisateur> findByDepartementId(UUID departementId);

    @Override
    @EntityGraph(attributePaths = "departement")
    Page<Utilisateur> findAll(Specification<Utilisateur> specification, Pageable pageable);
}
