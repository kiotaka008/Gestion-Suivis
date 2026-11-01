package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.CompteurResponse;
import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.notification.NotificationResponse;
import com.gestionsuivis.backend.entity.Notification;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.NotificationMapper;
import com.gestionsuivis.backend.repository.NotificationRepository;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Notifications : création par les autres services (affectation, fin d'activité,
 * échéance, commentaire, ajout à un projet) et consultation par leur destinataire
 * uniquement.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int LONGUEUR_MAX_TITRE = 200;
    private static final int LONGUEUR_MAX_MESSAGE = 1000;

    private final NotificationRepository notifications;
    private final NotificationMapper mapper;
    private final UtilisateurCourant utilisateurCourant;

    /** Crée une notification ; ignorée si le destinataire est absent, inactif ou l'auteur lui-même. */
    @Transactional
    public void notifier(Utilisateur destinataire, UUID auteurId, TypeNotification type, String titre,
                         String message, UUID projetId, UUID activiteId) {
        if (destinataire == null || !destinataire.isActif()
                || (auteurId != null && auteurId.equals(destinataire.getId()))) {
            return;
        }
        Notification notification = new Notification();
        notification.setDestinataire(destinataire);
        notification.setType(type);
        notification.setTitre(tronquer(titre, LONGUEUR_MAX_TITRE));
        notification.setMessage(tronquer(message, LONGUEUR_MAX_MESSAGE));
        notification.setProjetId(projetId);
        notification.setActiviteId(activiteId);
        notifications.save(notification);
    }

    /**
     * Notifications de l'utilisateur connecté.
     *
     * @param type   filtre facultatif sur le type
     * @param lues   {@code true} = lues, {@code false} = non lues, {@code null} = toutes
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> lister(TypeNotification type, Boolean lues, Pageable pageable) {
        UUID destinataire = utilisateurCourant.obtenir().id();
        Specification<Notification> specification = (racine, requete, cb) -> {
            List<Predicate> et = new ArrayList<>();
            et.add(cb.equal(racine.get("destinataire").get("id"), destinataire));
            if (type != null) {
                et.add(cb.equal(racine.get("type"), type));
            }
            if (lues != null) {
                et.add(cb.equal(racine.get("lue"), lues));
            }
            return cb.and(et.toArray(Predicate[]::new));
        };
        return PageResponse.de(notifications.findAll(specification, pageable), mapper::versDto);
    }

    @Transactional(readOnly = true)
    public CompteurResponse compterNonLues() {
        return new CompteurResponse(notifications.countByDestinataireIdAndLueFalse(utilisateurCourant.obtenir().id()));
    }

    @Transactional
    public void marquerCommeLue(UUID id) {
        Notification notification = chargerSienne(id);
        notification.setLue(true);
    }

    @Transactional
    public void marquerToutesCommeLues() {
        notifications.marquerToutesCommeLues(utilisateurCourant.obtenir().id());
    }

    @Transactional
    public void supprimer(UUID id) {
        notifications.delete(chargerSienne(id));
    }

    /** Une notification d'un autre utilisateur est traitée comme introuvable. */
    private Notification chargerSienne(UUID id) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        return notifications.findByIdAndDestinataireId(id, u.id())
                .orElseThrow(() -> new RessourceIntrouvableException("Notification introuvable."));
    }

    private static String tronquer(String texte, int longueur) {
        if (texte == null) {
            return null;
        }
        return texte.length() <= longueur ? texte : texte.substring(0, longueur - 1) + "…";
    }
}
