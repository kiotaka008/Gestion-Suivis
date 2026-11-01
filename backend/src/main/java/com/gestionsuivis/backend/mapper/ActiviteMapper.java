package com.gestionsuivis.backend.mapper;

import com.gestionsuivis.backend.dto.activite.ActiviteResponse;
import com.gestionsuivis.backend.dto.activite.CommentaireResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Commentaire;
import com.gestionsuivis.backend.entity.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Conversion des activités et de leurs commentaires en DTO. À appeler dans une transaction. */
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
                activite.getHeuresPrevues(),
                activite.getHeuresReelles(),
                activite.estEnRetard(aujourdHui),
                activite.getDateFin(),
                activite.getDateCreation(),
                activite.getDateModification());
    }

    public CommentaireResponse versDto(Commentaire commentaire) {
        return new CommentaireResponse(
                commentaire.getId(),
                commentaire.getActivite().getId(),
                commentaire.getAuteur().getId(),
                commentaire.getAuteur().getNom(),
                commentaire.getContenu(),
                commentaire.getDateCreation());
    }
}
