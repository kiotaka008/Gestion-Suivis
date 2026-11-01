package com.gestionsuivis.backend.repository;

import com.gestionsuivis.backend.entity.ConversationIa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationIaRepository extends JpaRepository<ConversationIa, UUID> {

    Page<ConversationIa> findByProprietaireId(UUID proprietaireId, Pageable pageable);

    Optional<ConversationIa> findByIdAndProprietaireId(UUID id, UUID proprietaireId);

    boolean existsByProprietaireId(UUID proprietaireId);
}
