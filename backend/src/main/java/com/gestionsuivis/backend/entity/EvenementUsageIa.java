package com.gestionsuivis.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Trace de consommation d'un appel au service IA (jetons, modèle, opération).
 * Aucun contenu de message n'y est stocké.
 * <p>
 * PROVISOIRE : structure supposée de {@code ai_usage_events}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_usage_events")
public class EvenementUsageIa {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID utilisateurId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "operation", nullable = false, length = 50)
    private String operation;

    @Column(name = "model", length = 100)
    private String modele;

    @Column(name = "prompt_tokens", nullable = false)
    private int jetonsPrompt;

    @Column(name = "completion_tokens", nullable = false)
    private int jetonsReponse;

    @Column(name = "total_tokens", nullable = false)
    private int jetonsTotal;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;
}
