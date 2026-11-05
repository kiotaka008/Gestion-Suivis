package com.gestionsuivis.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
import java.util.UUID;

/**
 * Utilisateur : table {@code users} du schéma officiel. L'email est unique au sein
 * d'une organisation ({@code users_org_email_unique}).
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

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organisationId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String motDePasseHash;

    @Column(name = "name", nullable = false, length = 255)
    private String nom;

    @Convert(converter = Role.Convertisseur.class)
    @ColumnTransformer(read = "role::text", write = "?::user_role")
    @Column(name = "role", nullable = false, columnDefinition = "user_role")
    private Role role = Role.EMPLOYE;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "is_active", nullable = false)
    private boolean actif = true;

    @Column(name = "last_login_at")
    private Instant derniereConnexion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant dateModification;
}
