package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.JournalAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JournalAuditRepository extends JpaRepository<JournalAudit, UUID> {
}
