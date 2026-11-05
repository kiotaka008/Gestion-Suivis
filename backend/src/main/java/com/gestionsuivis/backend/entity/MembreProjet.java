package com.gestionsuivis.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Appartenance d'un utilisateur à un projet : table {@code project_members}
 * (clé primaire {@code (project_id, user_id)}, rôle {@code owner | member}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_members")
public class MembreProjet {

    @EmbeddedId
    private Cle id = new Cle();

    @MapsId("projetId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Projet projet;

    @MapsId("utilisateurId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Utilisateur utilisateur;

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organisationId;

    @Convert(converter = RoleMembreProjet.Convertisseur.class)
    @ColumnTransformer(read = "role::text", write = "?::project_member_role")
    @Column(name = "role", nullable = false, columnDefinition = "project_member_role")
    private RoleMembreProjet role = RoleMembreProjet.MEMBRE;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant dateAjout;

    public MembreProjet(Projet projet, Utilisateur utilisateur, RoleMembreProjet role) {
        this.projet = projet;
        this.utilisateur = utilisateur;
        this.role = role;
    }

    /** Clé composée {@code (project_id, user_id)}. */
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    @Embeddable
    public static class Cle implements Serializable {

        @Column(name = "project_id", nullable = false)
        private UUID projetId;

        @Column(name = "user_id", nullable = false)
        private UUID utilisateurId;
    }
}
