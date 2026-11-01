package com.gestionsuivis.backend.mapper;

import com.gestionsuivis.backend.dto.projet.ProjetResponse;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Conversion {@link Projet} → {@link ProjetResponse}. L'avancement est fourni
 * par l'appelant (calculé à partir des activités). À appeler dans une transaction.
 */
@Component
@RequiredArgsConstructor
public class ProjetMapper {

    private final UtilisateurMapper utilisateurMapper;

    public ProjetResponse versDto(Projet projet, int avancement, LocalDate aujourdHui) {
        List<Utilisateur> membres = projet.getMembres().stream()
                .sorted(Comparator.comparing(Utilisateur::getNom, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<UUID> idsMembres = membres.stream().map(Utilisateur::getId).toList();
        List<UtilisateurResponse> dtoMembres = membres.stream().map(utilisateurMapper::versDto).toList();
        Departement departement = projet.getDepartement();
        Utilisateur chef = projet.getChefDeProjet();
        return new ProjetResponse(
                projet.getId(),
                projet.getNom(),
                projet.getDescription(),
                projet.getStatut(),
                projet.getPriorite(),
                avancement,
                chef == null ? null : chef.getId(),
                utilisateurMapper.versDto(chef),
                idsMembres,
                dtoMembres,
                projet.getDateDebut(),
                projet.getDateEcheance(),
                departement == null ? null : departement.getId(),
                departement == null ? null : departement.getNom(),
                projet.estEnRetard(aujourdHui),
                projet.getDateCreation(),
                projet.getDateModification());
    }
}
