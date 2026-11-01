package com.gestionsuivis.backend.entity;

import com.gestionsuivis.backend.dto.ia.RoleMessageIa;
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
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Message d'une conversation IA (question de l'utilisateur ou réponse de l'assistant).
 * <p>
 * PROVISOIRE : structure supposée de {@code ai_messages}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_messages")
public class MessageConversationIa {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationIa conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private RoleMessageIa role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String contenu;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dateCreation;
}
