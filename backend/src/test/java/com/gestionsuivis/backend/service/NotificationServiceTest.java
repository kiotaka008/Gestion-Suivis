package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.entity.Notification;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.NotificationMapper;
import com.gestionsuivis.backend.repository.NotificationRepository;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Notifications : création filtrée et consultation réservée au destinataire. */
class NotificationServiceTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UtilisateurCourant courant = mock(UtilisateurCourant.class);
    private final NotificationService service = new NotificationService(notifications, new NotificationMapper(), courant);

    private final Utilisateur destinataire = Donnees.utilisateur("Destinataire", Role.EMPLOYE);
    private final Utilisateur autre = Donnees.utilisateur("Autre", Role.EMPLOYE);

    @BeforeEach
    void initialiser() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(destinataire));
    }

    @Test
    void notifier_enregistreLaNotificationDuDestinataire() {
        service.notifier(destinataire, autre.getId(), TypeNotification.ACTIVITE, "Titre", "Message");

        ArgumentCaptor<Notification> enregistree = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(enregistree.capture());
        assertThat(enregistree.getValue().getDestinataire()).isEqualTo(destinataire);
        assertThat(enregistree.getValue().getType()).isEqualTo(TypeNotification.ACTIVITE);
        assertThat(enregistree.getValue().isLue()).isFalse();
    }

    @Test
    void notifier_ignoreLAuteurLuiMemeUnCompteInactifEtUnDestinataireAbsent() {
        service.notifier(destinataire, destinataire.getId(), TypeNotification.PROJET, "T", "M");
        service.notifier(null, autre.getId(), TypeNotification.PROJET, "T", "M");
        Utilisateur inactif = Donnees.utilisateur("Inactif", Role.EMPLOYE);
        inactif.setActif(false);
        service.notifier(inactif, autre.getId(), TypeNotification.PROJET, "T", "M");

        verify(notifications, never()).save(any());
    }

    @Test
    void notifier_actionAutomatiqueSansAuteur_estEnregistree() {
        service.notifier(destinataire, null, TypeNotification.ECHEANCE, "Échéance", "Demain");
        verify(notifications).save(any());
    }

    @Test
    void notifier_titreEtMessageTropLongs_tronquesAuxLimitesDuSchema() {
        service.notifier(destinataire, null, TypeNotification.ACTIVITE, "t".repeat(400), "m".repeat(3000));

        ArgumentCaptor<Notification> enregistree = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(enregistree.capture());
        assertThat(enregistree.getValue().getTitre()).hasSize(255).endsWith("…");
        assertThat(enregistree.getValue().getMessage()).hasSize(2000).endsWith("…");
    }

    @Test
    void marquerCommeLue_saPropreNotification() {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setDestinataire(destinataire);
        when(notifications.findByIdAndDestinataireId(n.getId(), destinataire.getId())).thenReturn(Optional.of(n));

        service.marquerCommeLue(n.getId());

        assertThat(n.isLue()).isTrue();
    }

    @Test
    void notificationDUnAutreUtilisateur_introuvable_niLueNiSupprimee() {
        UUID notificationDAutrui = UUID.randomUUID();
        when(notifications.findByIdAndDestinataireId(notificationDAutrui, destinataire.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marquerCommeLue(notificationDAutrui))
                .isInstanceOf(RessourceIntrouvableException.class);
        assertThatThrownBy(() -> service.supprimer(notificationDAutrui))
                .isInstanceOf(RessourceIntrouvableException.class);
        verify(notifications, never()).delete(any(Notification.class));
    }

    @Test
    void compteurEtToutMarquerCommeLu_limitesAuDestinataireConnecte() {
        when(notifications.countByDestinataireIdAndLueFalse(destinataire.getId())).thenReturn(3L);

        assertThat(service.compterNonLues().count()).isEqualTo(3L);
        service.marquerToutesCommeLues();

        verify(notifications).marquerToutesCommeLues(destinataire.getId());
    }
}
