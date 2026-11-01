package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.config.PaginationConfig;
import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.assistant.AnalyseRisquesResponse;
import com.gestionsuivis.backend.dto.assistant.ConversationResponse;
import com.gestionsuivis.backend.dto.assistant.MessageAssistantResponse;
import com.gestionsuivis.backend.dto.assistant.MessageChatRequest;
import com.gestionsuivis.backend.dto.assistant.MessageChatResponse;
import com.gestionsuivis.backend.dto.assistant.RapportIaRequest;
import com.gestionsuivis.backend.dto.assistant.RapportIaResponse;
import com.gestionsuivis.backend.dto.ia.SanteServiceIa;
import com.gestionsuivis.backend.service.ia.AssistantIaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Assistant IA côté backend. Le frontend appelle uniquement ces routes ; Spring
 * Boot appelle le service FastAPI (jamais Gemini directement).
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AssistantIaController {

    private final AssistantIaService service;

    @PostMapping("/chat")
    public MessageChatResponse discuter(@Valid @RequestBody MessageChatRequest requete) {
        return service.discuter(requete);
    }

    @GetMapping("/conversations")
    public PageResponse<ConversationResponse> conversations(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer perPage) {
        return service.listerConversations(PaginationConfig.creer(page, perPage, null, null, Map.of(),
                Sort.by(Sort.Direction.DESC, "dateModification")));
    }

    @GetMapping("/conversations/{id}/messages")
    public PageResponse<MessageAssistantResponse> messages(
            @PathVariable UUID id, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage) {
        return service.listerMessages(id, PaginationConfig.creer(page, perPage, null, null, Map.of(), Sort.unsorted()));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> supprimerConversation(@PathVariable UUID id) {
        service.supprimerConversation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{id}/risk-analysis")
    public AnalyseRisquesResponse analyserRisques(@PathVariable UUID id) {
        return service.analyserRisques(id);
    }

    @PostMapping("/reports")
    public RapportIaResponse genererRapport(@Valid @RequestBody RapportIaRequest requete) {
        return service.genererRapport(requete);
    }

    /** Disponibilité du service IA (ADMIN). */
    @GetMapping("/health")
    public SanteServiceIa sante() {
        return service.sante();
    }
}
