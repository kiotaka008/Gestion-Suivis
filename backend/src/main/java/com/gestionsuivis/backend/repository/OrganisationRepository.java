package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Organisations (tenants) : table non filtrée par organisation ni par le RLS. */
public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {

    Optional<Organisation> findBySlugIgnoreCase(String slug);
}
