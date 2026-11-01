package com.gestionsuivis.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Projet, avec un chef de projet ({@code owner}), un département et des membres.
 * <p>
 * PROVISOIRE : structure supposée, membres dans la table d'association
 * {@code project_members(project_id, user_id)} (schéma PostgreSQL non fourni).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "projects")
public class Projet {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String nom;

    @Column(name = "description", length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatutProjet statut = StatutProjet.EN_COURS;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private Priorite priorite;

    @Column(name = "start_date")
    private LocalDate dateDebut;

    @Column(name = "due_date")
    private LocalDate dateEcheance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Utilisateur chefDeProjet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Departement departement;

    @BatchSize(size = 50)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "project_members",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<Utilisateur> membres = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant dateModification;

    public boolean estEnRetard(LocalDate aujourdHui) {
        return dateEcheance != null && dateEcheance.isBefore(aujourdHui) && !statut.estClos();
    }
}
