package com.gestionsuivis.backend.mapper;

import com.gestionsuivis.backend.dto.departement.DepartementResponse;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Utilisateur;
import org.springframework.stereotype.Component;

/** Conversion {@link Departement} → {@link DepartementResponse}. */
@Component
public class DepartementMapper {

    public DepartementResponse versDto(Departement departement, long nombreMembres) {
        Utilisateur responsable = departement.getResponsable();
        return new DepartementResponse(
                departement.getId(),
                departement.getNom(),
                departement.getDescription(),
                responsable == null ? null : responsable.getId(),
                responsable == null ? null : responsable.getNom(),
                nombreMembres,
                departement.getDateCreation(),
                departement.getDateModification());
    }
}
