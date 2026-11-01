package com.gestionsuivis.backend.dto.departement;

import java.time.Instant;
import java.util.UUID;

/**
 * Département renvoyé par l'API (nouveau type, absent du frontend actuel).
 */
public record DepartementResponse(
        UUID id,
        String name,
        String description,
        UUID managerId,
        String managerName,
        long memberCount,
        Instant createdAt,
        Instant updatedAt) {
}
