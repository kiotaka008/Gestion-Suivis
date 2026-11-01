package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Traitement quotidien des échéances (tous les jours à 7 h, heure du serveur) :
 * <ul>
 *   <li>les activités non terminées dont l'échéance est dépassée passent au statut
 *       EN_RETARD et leur responsable est notifié ;</li>
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
    private static final Set<StatutActivite> NON_TERMINEES = EnumSet.of(StatutActivite.A_FAIRE, StatutActivite.EN_COURS);

    private final ActiviteRepository activites;
    private final NotificationRepository notificationsRepository;
    private final NotificationService notifications;
    private final Clock horloge;

    @Scheduled(cron = "${ECHEANCES_CRON:0 0 7 * * *}")
    @Transactional
    public void traiterEcheances() {
        LocalDate aujourdHui = LocalDate.now(horloge);
        Instant debutJournee = aujourdHui.atStartOfDay(horloge.getZone()).toInstant();

        List<Activite> enRetard = activites.findByStatutInAndDateEcheanceBefore(NON_TERMINEES, aujourdHui);
        for (Activite activite : enRetard) {
            activite.setStatut(StatutActivite.EN_RETARD);
            notifierUneFois(activite, debutJournee, "Activité en retard",
                    "L'échéance de l'activité « " + activite.getTitre() + " » (" + activite.getDateEcheance()
                            + ") est dépassée.");
        }

        List<Activite> bientotDues = activites.findByStatutInAndDateEcheanceBetween(
                EnumSet.of(StatutActivite.A_FAIRE, StatutActivite.EN_COURS), aujourdHui, aujourdHui.plusDays(JOURS_RAPPEL));
        for (Activite activite : bientotDues) {
            notifierUneFois(activite, debutJournee, "Échéance proche",
                    "L'activité « " + activite.getTitre() + " » est due le " + activite.getDateEcheance() + ".");
        }
        log.info("Échéances traitées : {} activité(s) passée(s) en retard, {} rappel(s) d'échéance",
                enRetard.size(), bientotDues.size());
    }

    private void notifierUneFois(Activite activite, Instant debutJournee, String titre, String message) {
        if (activite.getResponsable() == null || notificationsRepository
                .existsByDestinataireIdAndActiviteIdAndTypeAndDateCreationAfter(activite.getResponsable().getId(),
                        activite.getId(), TypeNotification.ECHEANCE, debutJournee)) {
            return;
        }
        notifications.notifier(activite.getResponsable(), null, TypeNotification.ECHEANCE, titre, message,
                activite.getProjet().getId(), activite.getId());
    }
}
