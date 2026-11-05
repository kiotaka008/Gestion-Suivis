package com.gestionsuivis.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Trace de consommation d'un appel au service IA : table {@code ai_usage_events}.
 * Aucun contenu de message n'y est stocké.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_usage_events")
public class EvenementUsageIa {

    public static final String STATUT_SUCCES = "success";

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organisationId;

    @Column(name = "user_id", nullable = false)
    private UUID utilisateurId;

    /** Route du backend à l'origine de l'appel (ex. {@code /api/ai/chat}, comme dans le jeu de données officiel). */
    @Column(name = "endpoint", nullable = false, length = 255)
    private String endpoint;

    @Column(name = "model", nullable = false, length = 100)
    private String modele;

    @Column(name = "prompt_tokens", nullable = false)
    private int jetonsPrompt;

    @Column(name = "completion_tokens", nullable = false)
    private int jetonsReponse;

    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal coutUsd = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 30)
    private String statut = STATUT_SUCCES;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;
}
