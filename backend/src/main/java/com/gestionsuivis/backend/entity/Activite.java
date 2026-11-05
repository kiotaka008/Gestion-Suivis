package com.gestionsuivis.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Activité (tâche) d'un projet : table {@code activities} (migration
 * {@code 20261104_004} comprise pour {@code start_date}).
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

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organisationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Projet projet;

    @Column(name = "title", nullable = false, length = 255)
    private String titre;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Convert(converter = StatutActivite.Convertisseur.class)
    @ColumnTransformer(read = "status::text", write = "?::activity_status")
    @Column(name = "status", nullable = false, columnDefinition = "activity_status")
    private StatutActivite statut = StatutActivite.A_FAIRE;

    @Convert(converter = Priorite.Convertisseur.class)
    @ColumnTransformer(read = "priority::text", write = "?::item_priority")
    @Column(name = "priority", nullable = false, columnDefinition = "item_priority")
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant dateModification;

    public boolean estTerminee() {
        return statut == StatutActivite.TERMINE;
    }

    /** En retard : échéance dépassée et activité non terminée. */
    public boolean estEnRetard(LocalDate aujourdHui) {
        return !estTerminee() && dateEcheance != null && dateEcheance.isBefore(aujourdHui);
    }

    /**
     * Date de fin approximative : le schéma ne conserve pas de date d'achèvement,
     * la dernière modification d'une activité terminée en tient lieu.
     */
    public Instant dateFin() {
        return estTerminee() ? dateModification : null;
    }
}
