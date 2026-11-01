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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Activité (tâche) d'un projet : affectation, priorité, statut, dates et heures.
 * <p>
 * PROVISOIRE : structure supposée (schéma PostgreSQL non fourni).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "activities")
public class Activite {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Projet projet;

    @Column(name = "title", nullable = false, length = 200)
    private String titre;

    @Column(name = "description", length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatutActivite statut = StatutActivite.A_FAIRE;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private Priorite priorite = Priorite.MOYENNE;

    @Column(name = "progress", nullable = false)
    private int avancement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Utilisateur responsable;

    @Column(name = "start_date")
    private LocalDate dateDebut;

    @Column(name = "due_date")
    private LocalDate dateEcheance;

    @Column(name = "planned_hours", precision = 7, scale = 2)
    private BigDecimal heuresPrevues;

    @Column(name = "actual_hours", precision = 7, scale = 2)
    private BigDecimal heuresReelles;

    @Column(name = "completed_at")
    private Instant dateFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Utilisateur createur;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant dateModification;

    public boolean estTerminee() {
        return statut == StatutActivite.TERMINE;
    }

    /** En retard : échéance dépassée et activité non terminée (statut EN_RETARD ou calcul). */
    public boolean estEnRetard(LocalDate aujourdHui) {
        return !estTerminee() && (statut == StatutActivite.EN_RETARD
                || (dateEcheance != null && dateEcheance.isBefore(aujourdHui)));
    }
}
