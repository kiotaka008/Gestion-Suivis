package com.gestionsuivis.backend.mapper;

import com.gestionsuivis.backend.dto.notification.NotificationResponse;
import com.gestionsuivis.backend.entity.Notification;
import org.springframework.stereotype.Component;

/** Conversion {@link Notification} → {@link NotificationResponse}. */
@Component
public class NotificationMapper {

    public NotificationResponse versDto(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitre(),
                notification.getMessage(),
                notification.isLue(),
                notification.getDateCreation(),
                notification.getProjetId(),
                notification.getActiviteId());
    }
}
