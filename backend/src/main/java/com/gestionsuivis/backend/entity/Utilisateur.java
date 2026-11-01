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

import java.time.Instant;
import java.util.UUID;

/**
 * Utilisateur de l'application.
 * <p>
 * PROVISOIRE : noms de table et de colonnes supposés (schéma PostgreSQL non fourni).
 * Voir docs/HYPOTHESES_SCHEMA.md ; {@code ddl-auto=validate} signalera tout écart.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class Utilisateur {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String motDePasseHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean actif = true;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "bio", length = 1000)
    private String bio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Departement departement;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant dateModification;
}
