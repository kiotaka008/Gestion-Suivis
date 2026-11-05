package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.config.ExecuteurOrganisation;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Organisation;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.NotificationRepository;
import com.gestionsuivis.backend.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Traitement quotidien des échéances (tous les jours à 7 h, heure du serveur),
 * organisation par organisation :
 * <ul>
 *   <li>le responsable de chaque activité non terminée dont l'échéance est dépassée
 *       est notifié (le retard est calculé, ce n'est pas un statut du schéma) ;</li>
 *   <li>un rappel d'échéance est envoyé pour les activités dues aujourd'hui ou
 *       dans les {@value #JOURS_RAPPEL} jours.</li>
 * </ul>
 * Une même notification n'est envoyée qu'une fois par jour et par activité.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EcheancesService {

    static final int JOURS_RAPPEL = 2;

    private final OrganisationRepository organisations;
    private final ActiviteRepository activites;
    private final NotificationRepository notificationsRepository;
    private final NotificationService notifications;
    private final ExecuteurOrganisation executeur;
    private final Clock horloge;

    @Scheduled(cron = "${ECHEANCES_CRON:0 0 7 * * *}")
    public void traiterEcheances() {
        for (Organisation organisation : organisations.findAll()) {
            try {
                executeur.ecrire(organisation.getId(), this::traiterOrganisation);
            } catch (RuntimeException e) {
                log.error("Échec du traitement des échéances pour l'organisation {}", organisation.getId(), e);
            }
        }
    }

    private void traiterOrganisation() {
        LocalDate aujourdHui = LocalDate.now(horloge);
        Instant debutJournee = aujourdHui.atStartOfDay(horloge.getZone()).toInstant();

        List<Activite> enRetard = activites.findByStatutNotAndDateEcheanceBefore(StatutActivite.TERMINE, aujourdHui);
        for (Activite activite : enRetard) {
            notifierUneFois(activite, debutJournee, "Activité en retard",
                    "L'échéance de l'activité « " + activite.getTitre() + " » (" + activite.getDateEcheance()
                            + ") est dépassée.");
        }

        List<Activite> bientotDues = activites.findByStatutNotAndDateEcheanceBetween(StatutActivite.TERMINE,
                aujourdHui, aujourdHui.plusDays(JOURS_RAPPEL));
        for (Activite activite : bientotDues) {
            notifierUneFois(activite, debutJournee, "Échéance proche",
                    "L'activité « " + activite.getTitre() + " » est due le " + activite.getDateEcheance() + ".");
        }
        log.info("Échéances traitées : {} activité(s) en retard, {} rappel(s) d'échéance",
                enRetard.size(), bientotDues.size());
    }

    private void notifierUneFois(Activite activite, Instant debutJournee, String titre, String message) {
        if (activite.getResponsable() == null || notificationsRepository
                .existsByDestinataireIdAndTypeAndMessageAndDateCreationAfter(activite.getResponsable().getId(),
                        TypeNotification.ECHEANCE, message, debutJournee)) {
            return;
        }
        notifications.notifier(activite.getResponsable(), null, TypeNotification.ECHEANCE, titre, message);
    }
}
