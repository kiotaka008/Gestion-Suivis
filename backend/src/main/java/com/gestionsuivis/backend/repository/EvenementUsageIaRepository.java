package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.EvenementUsageIa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvenementUsageIaRepository extends JpaRepository<EvenementUsageIa, UUID> {
}
