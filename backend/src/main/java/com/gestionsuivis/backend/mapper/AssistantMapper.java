package com.gestionsuivis.backend.mapper;

import com.gestionsuivis.backend.dto.assistant.ConversationResponse;
import com.gestionsuivis.backend.dto.assistant.MessageAssistantResponse;
import com.gestionsuivis.backend.entity.ConversationIa;
import com.gestionsuivis.backend.entity.MessageConversationIa;
import org.springframework.stereotype.Component;

/** Conversion des conversations et messages IA en DTO. */
@Component
public class AssistantMapper {

    public static final int LONGUEUR_APERCU = 100;

    public MessageAssistantResponse versDto(MessageConversationIa message) {
        return new MessageAssistantResponse(message.getId(), message.getRole(), message.getContenu(),
                message.getDateCreation());
    }

    public ConversationResponse versDto(ConversationIa conversation, String premiereQuestion, long nombreMessages) {
        String apercu = premiereQuestion == null ? ""
                : premiereQuestion.length() <= LONGUEUR_APERCU ? premiereQuestion
                : premiereQuestion.substring(0, LONGUEUR_APERCU);
        return new ConversationResponse(conversation.getId(), conversation.getTitre(), apercu, nombreMessages,
                conversation.getDateCreation(), conversation.getDateModification());
    }
}
