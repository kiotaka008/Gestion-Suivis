package com.gestionsuivis.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Conversation avec l'assistant IA, propriété d'un utilisateur. L'historique est
 * conservé par le backend (le service FastAPI est sans état).
 * <p>
 * PROVISOIRE : structure supposée de {@code ai_conversations}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_conversations")
public class ConversationIa {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Utilisateur proprietaire;

    @Column(name = "title", length = 200)
    private String titre;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant dateModification;
}
