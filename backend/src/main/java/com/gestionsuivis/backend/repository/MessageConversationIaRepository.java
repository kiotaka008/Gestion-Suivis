package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.dto.ia.RoleMessageIa;
import com.gestionsuivis.backend.entity.MessageConversationIa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageConversationIaRepository extends JpaRepository<MessageConversationIa, UUID> {

    /** Messages les plus récents en premier, pour reconstruire un historique limité. */
    List<MessageConversationIa> findByConversationIdOrderByDateCreationDesc(UUID conversationId, Pageable pageable);

    Page<MessageConversationIa> findByConversationIdOrderByDateCreationAsc(UUID conversationId, Pageable pageable);

    long countByConversationId(UUID conversationId);

    Optional<MessageConversationIa> findFirstByConversationIdAndRoleOrderByDateCreationAsc(UUID conversationId,
                                                                                         RoleMessageIa role);

    @Modifying
    @Query("delete from MessageConversationIa m where m.conversation.id = :conversation")
    void supprimerParConversation(@Param("conversation") UUID conversationId);
}
