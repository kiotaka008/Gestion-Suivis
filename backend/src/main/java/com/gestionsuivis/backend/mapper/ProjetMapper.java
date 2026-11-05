package com.gestionsuivis.backend.mapper;

import com.gestionsuivis.backend.dto.projet.ProjetResponse;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Conversion {@link Projet} → {@link ProjetResponse}. À appeler dans une transaction
 * (membres chargés paresseusement).
 */
@Component
@RequiredArgsConstructor
public class ProjetMapper {

    private final UtilisateurMapper utilisateurMapper;

    public ProjetResponse versDto(Projet projet, LocalDate aujourdHui) {
        List<Utilisateur> membres = projet.utilisateursMembres().stream()
                .sorted(Comparator.comparing(Utilisateur::getNom, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<UUID> idsMembres = membres.stream().map(Utilisateur::getId).toList();
        List<UtilisateurResponse> dtoMembres = membres.stream().map(utilisateurMapper::versDto).toList();
        Utilisateur chef = projet.getChefDeProjet();
        return new ProjetResponse(
                projet.getId(),
                projet.getNom(),
                projet.getDescription(),
                projet.getStatut(),
                projet.getPriorite(),
                projet.getAvancement(),
                chef == null ? null : chef.getId(),
                utilisateurMapper.versDto(chef),
                idsMembres,
                dtoMembres,
                projet.getDateDebut(),
                projet.getDateEcheance(),
                projet.estEnRetard(aujourdHui),
                projet.getDateCreation(),
                projet.getDateModification());
    }
}
