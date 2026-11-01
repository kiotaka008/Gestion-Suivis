package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.Notification;
import com.gestionsuivis.backend.entity.TypeNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    long countByDestinataireIdAndLueFalse(UUID destinataireId);

    Optional<Notification> findByIdAndDestinataireId(UUID id, UUID destinataireId);

    boolean existsByDestinataireIdAndActiviteIdAndTypeAndDateCreationAfter(
            UUID destinataireId, UUID activiteId, TypeNotification type, Instant apres);

    @Modifying
    @Query("update Notification n set n.lue = true where n.destinataire.id = :destinataire and n.lue = false")
    int marquerToutesCommeLues(@Param("destinataire") UUID destinataireId);

    @Modifying
    @Query("delete from Notification n where n.destinataire.id = :destinataire")
    void supprimerParDestinataire(@Param("destinataire") UUID destinataireId);
}
