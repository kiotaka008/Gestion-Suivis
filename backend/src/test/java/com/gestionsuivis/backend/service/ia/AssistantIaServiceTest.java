package com.gestionsuivis.backend.service.ia;

import com.gestionsuivis.backend.dto.assistant.MessageChatRequest;
import com.gestionsuivis.backend.dto.assistant.MessageChatResponse;
import com.gestionsuivis.backend.dto.ia.ChatIaReponse;
import com.gestionsuivis.backend.dto.ia.ChatIaRequete;
import com.gestionsuivis.backend.dto.ia.MessageIa;
import com.gestionsuivis.backend.dto.ia.RoleMessageIa;
import com.gestionsuivis.backend.dto.ia.UsageIa;
import com.gestionsuivis.backend.entity.ConversationIa;
import com.gestionsuivis.backend.entity.EvenementUsageIa;
import com.gestionsuivis.backend.entity.MessageConversationIa;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.exception.ServiceIaException;
import com.gestionsuivis.backend.exception.TypeErreurServiceIa;
import com.gestionsuivis.backend.mapper.AssistantMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ConversationIaRepository;
import com.gestionsuivis.backend.repository.EvenementUsageIaRepository;
import com.gestionsuivis.backend.repository.MessageConversationIaRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.service.CalculAvancement;
import com.gestionsuivis.backend.service.rapport.BilanService;
import com.gestionsuivis.backend.service.rapport.SuiviService;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Assistant IA : propriété des conversations, historique limité à 49 messages,
 * contexte ajouté uniquement au message envoyé, enregistrement après réponse.
 */
@SuppressWarnings("unchecked")
class AssistantIaServiceTest {

    private final ServiceIaClient client = mock(ServiceIaClient.class);
    private final ConversationIaRepository conversations = mock(ConversationIaRepository.class);
    private final MessageConversationIaRepository messages = mock(MessageConversationIaRepository.class);
    private final EvenementUsageIaRepository usages = mock(EvenementUsageIaRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final ProjetRepository projets = mock(ProjetRepository.class);
    private final ActiviteRepository activites = mock(ActiviteRepository.class);
    private final UtilisateurCourant courant = mock(UtilisateurCourant.class);
    private final AssistantIaService service = new AssistantIaService(client, conversations, messages, usages,
            utilisateurs, projets, activites, new CalculAvancement(activites), new ControleAcces(projets, activites),
            courant, mock(SuiviService.class), mock(BilanService.class), new AssistantMapper(),
            JsonMapper.builder().build(), Clock.systemUTC(), mock(PlatformTransactionManager.class));

    private final Utilisateur employe = Donnees.utilisateur("Employe", Role.EMPLOYE, null);
    private final Utilisateur autre = Donnees.utilisateur("Autre", Role.EMPLOYE, null);

    @BeforeEach
    void initialiser() {
        when(courant.obtenir()).thenReturn(Donnees.connecte(employe));
        when(projets.findAll(any(Specification.class))).thenReturn(List.of());
        when(activites.findAll(any(Specification.class))).thenReturn(List.of());
        when(utilisateurs.getReferenceById(employe.getId())).thenReturn(employe);
        when(conversations.save(any())).thenAnswer(invocation -> {
            ConversationIa conversation = invocation.getArgument(0);
            conversation.setId(UUID.randomUUID());
            return conversation;
        });
        when(messages.save(any())).thenAnswer(invocation -> {
            MessageConversationIa message = invocation.getArgument(0);
            message.setId(UUID.randomUUID());
            message.setDateCreation(Instant.now());
            return message;
        });
        when(client.envoyerChat(any())).thenReturn(new ChatIaReponse(MessageIa.assistant("Deux projets sont en retard."),
                null, new UsageIa(10, 5, 15), "gemini-test", "2026-11-01T10:00:00"));
    }

    @Test
    void nouvelleConversation_creeeApresLaReponseEtQuestionEnregistreeSansContexte() {
        MessageChatResponse reponse = service.discuter(new MessageChatRequest(null, "Quels projets sont en retard ?"));

        ArgumentCaptor<ChatIaRequete> envoyee = ArgumentCaptor.forClass(ChatIaRequete.class);
        verify(client).envoyerChat(envoyee.capture());
        assertThat(envoyee.getValue().messages()).hasSize(1);
        assertThat(envoyee.getValue().messages().getFirst().contenu())
                .contains("Contexte de suivi").endsWith("Question de l'utilisateur : Quels projets sont en retard ?");
        assertThat(envoyee.getValue().identifiantUtilisateur()).isEqualTo(employe.getId().toString());

        ArgumentCaptor<MessageConversationIa> enregistres = ArgumentCaptor.forClass(MessageConversationIa.class);
        verify(messages, times(2)).save(enregistres.capture());
        assertThat(enregistres.getAllValues().get(0).getRole()).isEqualTo(RoleMessageIa.USER);
        assertThat(enregistres.getAllValues().get(0).getContenu()).isEqualTo("Quels projets sont en retard ?");
        assertThat(enregistres.getAllValues().get(1).getContenu()).isEqualTo("Deux projets sont en retard.");
        assertThat(reponse.conversationId()).isNotNull();
        assertThat(reponse.message().role()).isEqualTo(RoleMessageIa.ASSISTANT);

        ArgumentCaptor<EvenementUsageIa> usage = ArgumentCaptor.forClass(EvenementUsageIa.class);
        verify(usages).save(usage.capture());
        assertThat(usage.getValue().getJetonsTotal()).isEqualTo(15);
        assertThat(usage.getValue().getOperation()).isEqualTo("chat");
    }

    @Test
    void historique_limiteA49MessagesDansLOrdreChronologique() {
        ConversationIa conversation = conversation(employe);
        List<MessageConversationIa> recentsDabord = new ArrayList<>();
        for (int i = 49; i >= 1; i--) {
            MessageConversationIa m = new MessageConversationIa();
            m.setRole(i % 2 == 0 ? RoleMessageIa.ASSISTANT : RoleMessageIa.USER);
            m.setContenu("message " + i);
            recentsDabord.add(m);
        }
        when(messages.findByConversationIdOrderByDateCreationDesc(eq(conversation.getId()), any()))
                .thenReturn(recentsDabord);

        service.discuter(new MessageChatRequest(conversation.getId(), "Suite"));

        ArgumentCaptor<ChatIaRequete> envoyee = ArgumentCaptor.forClass(ChatIaRequete.class);
        verify(client).envoyerChat(envoyee.capture());
        assertThat(envoyee.getValue().messages()).hasSize(50);
        assertThat(envoyee.getValue().messages().getFirst().contenu()).isEqualTo("message 1");
        assertThat(envoyee.getValue().messages().get(48).contenu()).isEqualTo("message 49");
        verify(messages).findByConversationIdOrderByDateCreationDesc(eq(conversation.getId()),
                eq(org.springframework.data.domain.PageRequest.of(0, 49)));
    }

    @Test
    void conversationDUnAutreUtilisateur_estIntrouvableEtLIaNestPasAppelee() {
        ConversationIa conversationAutre = conversation(autre);

        assertThatThrownBy(() -> service.discuter(new MessageChatRequest(conversationAutre.getId(), "Bonjour")))
                .isInstanceOf(RessourceIntrouvableException.class);
        verify(client, never()).envoyerChat(any());
    }

    @Test
    void erreurDuServiceIa_rienNEstEnregistre() {
        when(client.envoyerChat(any())).thenThrow(new ServiceIaException(TypeErreurServiceIa.INDISPONIBLE));

        assertThatThrownBy(() -> service.discuter(new MessageChatRequest(null, "Bonjour")))
                .isInstanceOf(ServiceIaException.class);
        verify(conversations, never()).save(any());
        verify(messages, never()).save(any());
    }

    private ConversationIa conversation(Utilisateur proprietaire) {
        ConversationIa conversation = new ConversationIa();
        conversation.setId(UUID.randomUUID());
        conversation.setProprietaire(proprietaire);
        when(conversations.findByIdAndProprietaireId(conversation.getId(), proprietaire.getId()))
                .thenReturn(Optional.of(conversation));
        when(conversations.findByIdAndProprietaireId(eq(conversation.getId()),
                org.mockito.ArgumentMatchers.argThat(id -> !id.equals(proprietaire.getId()))))
                .thenReturn(Optional.empty());
        return conversation;
    }
}
