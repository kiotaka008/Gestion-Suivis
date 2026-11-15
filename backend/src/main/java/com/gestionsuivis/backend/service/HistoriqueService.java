package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.historique.EvenementHistoriqueResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.JournalAudit;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.JournalAuditRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consultation de l'historique ({@code audit_logs}) d'un projet, d'une activité ou d'un utilisateur.
 * <ul>
 *   <li>isolation par organisation : {@code @TenantId} sur {@link JournalAudit} (et RLS en base) ;</li>
 *   <li>droits : ceux de la consultation de l'élément (projet, activité visibles ; historique d'un
 *       utilisateur réservé aux administrateurs et à l'utilisateur lui-même) ;</li>
 *   <li>tri chronologique du plus récent au plus ancien, paginé ;</li>
 *   <li>aucune donnée sensible : auteur réduit à son nom, détails limités aux noms des champs
 *       modifiés et au nouveau statut (voir {@link AuditService}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class HistoriqueService {

    /** Ordre chronologique inverse ; l'identifiant départage deux actions de la même milliseconde. */
    public static final Sort TRI = Sort.by(Sort.Direction.DESC, "dateCreation").and(Sort.by(Sort.Direction.DESC, "id"));

    private final JournalAuditRepository journal;
    private final ProjetRepository projets;
    private final ActiviteRepository activites;
    private final UtilisateurRepository utilisateurs;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;

    /** Actions sur le projet et sur ses activités actuelles (y compris activités ajoutées et supprimées). */
    @Transactional(readOnly = true)
    public PageResponse<EvenementHistoriqueResponse> projet(UUID id, Pageable pageable) {
        Projet projet = projets.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Projet", id));
        controle.verifierVoirProjet(utilisateurCourant.obtenir(), projet);
        List<UUID> ids = activites.idsDuProjet(id);
        Page<JournalAudit> page = ids.isEmpty()
                ? journal.findByTypeEntiteAndEntiteId(AuditService.PROJET, id, trier(pageable))
                : journal.historiqueProjet(AuditService.PROJET, id, AuditService.ACTIVITE, ids, trier(pageable));
        return convertir(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<EvenementHistoriqueResponse> activite(UUID id, Pageable pageable) {
        Activite activite = activites.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Activité", id));
        controle.verifierVoirActivite(utilisateurCourant.obtenir(), activite);
        return convertir(journal.findByTypeEntiteAndEntiteId(AuditService.ACTIVITE, id, trier(pageable)));
    }

    /** Historique d'un compte (connexions, modifications) : ADMIN, ou l'utilisateur lui-même. */
    @Transactional(readOnly = true)
    public PageResponse<EvenementHistoriqueResponse> utilisateur(UUID id, Pageable pageable) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        if (!u.estAdmin() && !u.id().equals(id)) {
            throw new AccesInterditException("L'historique d'un compte est réservé aux administrateurs.");
        }
        utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", id));
        return convertir(journal.findByTypeEntiteAndEntiteId(AuditService.UTILISATEUR, id, trier(pageable)));
    }

    // ---------------------------------------------------------------- conversion

    static Pageable trier(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), TRI);
    }

    private PageResponse<EvenementHistoriqueResponse> convertir(Page<JournalAudit> page) {
        // Noms des auteurs et des membres cités, en une seule requête (organisation courante uniquement).
        Set<UUID> ids = new HashSet<>();
        page.forEach(e -> {
            if (e.getUtilisateurId() != null) {
                ids.add(e.getUtilisateurId());
            }
            UUID membre = uuid(details(e).get(AuditService.CLE_MEMBRE));
            if (membre != null) {
                ids.add(membre);
            }
        });
        Map<UUID, String> noms = ids.isEmpty() ? Map.of() : utilisateurs.findAllById(ids).stream()
                .collect(Collectors.toMap(Utilisateur::getId, Utilisateur::getNom));
        return PageResponse.de(page, e -> versDto(e, noms::get));
    }

    static EvenementHistoriqueResponse versDto(JournalAudit e, Function<UUID, String> nomDe) {
        Map<String, Object> details = details(e);
        List<String> champs = details.get(AuditService.CLE_CHAMPS) instanceof List<?> liste
                ? liste.stream().filter(Objects::nonNull).map(Object::toString).toList()
                : List.of();
        String statut = details.get(AuditService.CLE_STATUT) instanceof String s ? s : null;
        EvenementHistoriqueResponse.Auteur auteur = null;
        if (e.getUtilisateurId() != null) {
            String nom = nomDe.apply(e.getUtilisateurId());
            auteur = new EvenementHistoriqueResponse.Auteur(e.getUtilisateurId(), nom != null ? nom : "Utilisateur supprimé");
        }
        UUID membre = uuid(details.get(AuditService.CLE_MEMBRE));
        String nomMembre = membre == null ? null : nomDe.apply(membre);
        String titreActivite = details.get(AuditService.CLE_TITRE_ACTIVITE) instanceof String t ? t : null;
        String description = LibellesHistorique.decrire(e.getAction(), e.getTypeEntite(), champs, statut,
                titreActivite, nomMembre);
        return new EvenementHistoriqueResponse(e.getId(), e.getAction(), e.getTypeEntite(), e.getEntiteId(),
                description, auteur, champs, statut, e.getDateCreation());
    }

    private static Map<String, Object> details(JournalAudit e) {
        return e.getMetadonnees() == null ? Map.of() : e.getMetadonnees();
    }

    private static UUID uuid(Object valeur) {
        if (valeur == null) {
            return null;
        }
        try {
            return UUID.fromString(valeur.toString());
        } catch (IllegalArgumentException ignoree) {
            return null;
        }
    }
}
