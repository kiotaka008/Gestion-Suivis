package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.JournalAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

/**
 * Journal d'audit. Les requêtes sont limitées à l'organisation courante par {@code @TenantId}
 * (et par la politique RLS de {@code audit_logs} en base).
 */
public interface JournalAuditRepository extends JpaRepository<JournalAudit, UUID> {

    /** Historique d'une entité (projet, activité ou utilisateur). */
    Page<JournalAudit> findByTypeEntiteAndEntiteId(String typeEntite, UUID entiteId, Pageable pageable);

    /** Historique d'un projet : ses propres actions et celles de ses activités actuelles. */
    @Query("""
            select j from JournalAudit j
            where (j.typeEntite = :typeProjet and j.entiteId = :projetId)
               or (j.typeEntite = :typeActivite and j.entiteId in :activites)""")
    Page<JournalAudit> historiqueProjet(@Param("typeProjet") String typeProjet, @Param("projetId") UUID projetId,
                                       @Param("typeActivite") String typeActivite,
                                       @Param("activites") Collection<UUID> activites, Pageable pageable);
}
