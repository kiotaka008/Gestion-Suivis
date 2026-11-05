package com.gestionsuivis.backend.mapper;

import com.gestionsuivis.backend.dto.activite.ActiviteResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Conversion des activités en DTO. À appeler dans une transaction. */
@Component
@RequiredArgsConstructor
public class ActiviteMapper {

    private final UtilisateurMapper utilisateurMapper;

    public ActiviteResponse versDto(Activite activite, LocalDate aujourdHui) {
        Utilisateur responsable = activite.getResponsable();
        int avancement = activite.estTerminee() ? 100 : activite.getAvancement();
        return new ActiviteResponse(
                activite.getId(),
                activite.getProjet().getId(),
                activite.getProjet().getNom(),
                activite.getTitre(),
                activite.getDescription(),
                activite.getStatut(),
                activite.getPriorite(),
                avancement,
                responsable == null ? null : responsable.getId(),
                utilisateurMapper.versDto(responsable),
                activite.getDateDebut(),
                activite.getDateEcheance(),
                activite.estEnRetard(aujourdHui),
                activite.dateFin(),
                activite.getDateCreation(),
                activite.getDateModification());
    }
}
