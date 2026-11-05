package com.gestionsuivis.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Projet : table {@code projects}, avec un chef de projet ({@code owner_id}) et des
 * membres ({@code project_members}). {@code progress} est recalculé par le backend à
 * chaque modification d'activité (moyenne des activités, une activité terminée = 100 %).
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

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organisationId;

    @Column(name = "name", nullable = false, length = 255)
    private String nom;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Convert(converter = StatutProjet.Convertisseur.class)
    @ColumnTransformer(read = "status::text", write = "?::project_status")
    @Column(name = "status", nullable = false, columnDefinition = "project_status")
    private StatutProjet statut = StatutProjet.EN_COURS;

    @Convert(converter = Priorite.Convertisseur.class)
    @ColumnTransformer(read = "priority::text", write = "?::item_priority")
    @Column(name = "priority", nullable = false, columnDefinition = "item_priority")
    private Priorite priorite = Priorite.MOYENNE;

    @Column(name = "progress", nullable = false)
    private int avancement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Utilisateur chefDeProjet;

    @Column(name = "start_date")
    private LocalDate dateDebut;

    @Column(name = "due_date")
    private LocalDate dateEcheance;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "projet", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MembreProjet> membres = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant dateModification;

    public boolean estEnRetard(LocalDate aujourdHui) {
        return dateEcheance != null && dateEcheance.isBefore(aujourdHui) && !statut.estClos();
    }

    /** Utilisateurs membres du projet (chef de projet compris s'il est inscrit comme membre). */
    public List<Utilisateur> utilisateursMembres() {
        return membres.stream().map(MembreProjet::getUtilisateur).toList();
    }

    public Optional<MembreProjet> membre(UUID utilisateurId) {
        return membres.stream().filter(m -> m.getUtilisateur().getId().equals(utilisateurId)).findFirst();
    }

    /** Ajoute un membre ; {@code false} s'il l'était déjà (son rôle est alors mis à jour si besoin). */
    public boolean ajouterMembre(Utilisateur utilisateur, RoleMembreProjet role) {
        Optional<MembreProjet> existant = membre(utilisateur.getId());
        if (existant.isPresent()) {
            if (role == RoleMembreProjet.PROPRIETAIRE) {
                existant.get().setRole(role);
            }
            return false;
        }
        membres.add(new MembreProjet(this, utilisateur, role));
        return true;
    }

    public boolean retirerMembre(UUID utilisateurId) {
        return membres.removeIf(m -> m.getUtilisateur().getId().equals(utilisateurId));
    }
}
