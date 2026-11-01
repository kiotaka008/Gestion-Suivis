package com.gestionsuivis.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification destinée à un utilisateur (échéance, affectation, fin d'activité...).
 * <p>
 * PROVISOIRE : structure supposée (schéma PostgreSQL non fourni).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Utilisateur destinataire;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TypeNotification type;

    @Column(name = "title", nullable = false, length = 200)
    private String titre;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean lue;

    @Column(name = "project_id")
    private UUID projetId;

    @Column(name = "activity_id")
    private UUID activiteId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;
}
