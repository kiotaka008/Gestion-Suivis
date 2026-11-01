package com.gestionsuivis.backend.service.ia;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.assistant.AnalyseRisquesResponse;
import com.gestionsuivis.backend.dto.assistant.ConversationResponse;
import com.gestionsuivis.backend.dto.assistant.MessageAssistantResponse;
import com.gestionsuivis.backend.dto.assistant.MessageChatRequest;
import com.gestionsuivis.backend.dto.assistant.MessageChatResponse;
import com.gestionsuivis.backend.dto.assistant.RapportIaRequest;
import com.gestionsuivis.backend.dto.assistant.RapportIaResponse;
import com.gestionsuivis.backend.dto.ia.AnalyseRisquesIaReponse;
import com.gestionsuivis.backend.dto.ia.AnalyseRisquesIaRequete;
import com.gestionsuivis.backend.dto.ia.ChatIaReponse;
import com.gestionsuivis.backend.dto.ia.ChatIaRequete;
import com.gestionsuivis.backend.dto.ia.MessageIa;
import com.gestionsuivis.backend.dto.ia.RapportIaReponse;
import com.gestionsuivis.backend.dto.ia.RapportIaRequete;
import com.gestionsuivis.backend.dto.ia.RoleMessageIa;
import com.gestionsuivis.backend.dto.ia.SanteServiceIa;
import com.gestionsuivis.backend.dto.ia.UsageIa;
import com.gestionsuivis.backend.dto.rapport.BilanResponse;
import com.gestionsuivis.backend.dto.suivi.IndicateurRisqueResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.ConversationIa;
import com.gestionsuivis.backend.entity.EvenementUsageIa;
import com.gestionsuivis.backend.entity.MessageConversationIa;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.AssistantMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ConversationIaRepository;
import com.gestionsuivis.backend.repository.EvenementUsageIaRepository;
import com.gestionsuivis.backend.repository.MessageConversationIaRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.SpecificationsActivite;
import com.gestionsuivis.backend.repository.SpecificationsProjet;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import com.gestionsuivis.backend.service.CalculAvancement;
import com.gestionsuivis.backend.service.rapport.BilanService;
import com.gestionsuivis.backend.service.rapport.SuiviService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assistant IA côté backend (React → Spring Boot → FastAPI → Gemini).
 * <ul>
 *   <li>authentifie l'utilisateur et vérifie ses droits avant tout appel ;</li>
 *   <li>n'envoie au service IA que des données qu'il a le droit de voir ;</li>
 *   <li>conserve l'historique des conversations dans la base (FastAPI est sans état) ;</li>
 *   <li>n'appelle jamais Gemini directement et ne fait pas de nouvelle tentative.</li>
 * </ul>
 * L'appel au service IA (jusqu'à 60 s) est fait hors transaction pour ne pas
 * bloquer de connexion à la base.
 */
@Slf4j
@Service
public class AssistantIaService {

    /** FastAPI accepte 50 messages : 49 d'historique au plus + la nouvelle question. */
    static final int HISTORIQUE_MAX = ServiceIaClient.NOMBRE_MAX_MESSAGES - 1;
    static final int LONGUEUR_TITRE = 60;
    static final int ACTIVITES_MAX_ANALYSE = 100;
    private static final String LANGUE = "fr";

    private final ServiceIaClient client;
    private final ConversationIaRepository conversations;
    private final MessageConversationIaRepository messages;
    private final EvenementUsageIaRepository usages;
    private final UtilisateurRepository utilisateurs;
    private final ProjetRepository projets;
    private final ActiviteRepository activites;
    private final CalculAvancement avancement;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;
    private final SuiviService suivi;
    private final BilanService bilans;
    private final AssistantMapper mapper;
    private final JsonMapper json;
    private final Clock horloge;
    private final TransactionTemplate lecture;
    private final TransactionTemplate ecriture;

    public AssistantIaService(ServiceIaClient client, ConversationIaRepository conversations,
                              MessageConversationIaRepository messages, EvenementUsageIaRepository usages,
                              UtilisateurRepository utilisateurs, ProjetRepository projets,
                              ActiviteRepository activites, CalculAvancement avancement, ControleAcces controle,
                              UtilisateurCourant utilisateurCourant, SuiviService suivi, BilanService bilans,
                              AssistantMapper mapper, JsonMapper json, Clock horloge,
                              PlatformTransactionManager transactions) {
        this.client = client;
        this.conversations = conversations;
        this.messages = messages;
        this.usages = usages;
        this.utilisateurs = utilisateurs;
        this.projets = projets;
        this.activites = activites;
        this.avancement = avancement;
        this.controle = controle;
        this.utilisateurCourant = utilisateurCourant;
        this.suivi = suivi;
        this.bilans = bilans;
        this.mapper = mapper;
        this.json = json;
        this.horloge = horloge;
        this.lecture = new TransactionTemplate(transactions);
        this.lecture.setReadOnly(true);
        this.ecriture = new TransactionTemplate(transactions);
    }

    // ---------------------------------------------------------------- chat

    public MessageChatResponse discuter(MessageChatRequest requete) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        String question = requete.content().trim();

        // 1. Lecture : propriété de la conversation, historique et contexte autorisé.
        Preparation preparation = lecture.execute(statut -> preparer(u, requete.conversationId(), question));

        // 2. Appel au service IA, hors transaction.
        ChatIaReponse reponse = client.envoyerChat(new ChatIaRequete(preparation.messages(), u.id().toString(),
                requete.conversationId() == null ? null : requete.conversationId().toString(), null));

        // 3. Écriture : question, réponse et consommation.
        return ecriture.execute(statut -> enregistrer(u, requete.conversationId(), question, reponse));
    }

    private record Preparation(List<MessageIa> messages) {
    }

    private Preparation preparer(UtilisateurConnecte u, UUID conversationId, String question) {
        List<MessageIa> historique = new ArrayList<>();
        if (conversationId != null) {
            chargerSienne(u, conversationId);
            List<MessageConversationIa> derniers = new ArrayList<>(messages.findByConversationIdOrderByDateCreationDesc(
                    conversationId, PageRequest.of(0, HISTORIQUE_MAX)));
            Collections.reverse(derniers);
            derniers.forEach(m -> historique.add(new MessageIa(m.getRole(), m.getContenu())));
        }
        LocalDate aujourdHui = LocalDate.now(horloge);
        List<Projet> projetsVisibles = projets.findAll(SpecificationsProjet.visiblesPar(u));
        List<Activite> activitesVisibles = activites.findAll(SpecificationsActivite.visiblesPar(u));
        String contexte = ContexteIa.construire(u.role().json(), projetsVisibles,
                avancement.parProjet(projetsVisibles.stream().map(Projet::getId).toList()), activitesVisibles,
                aujourdHui);
        historique.add(MessageIa.utilisateur(ContexteIa.enrichir(contexte, question)));
        return new Preparation(historique);
    }

    private MessageChatResponse enregistrer(UtilisateurConnecte u, UUID conversationId, String question,
                                            ChatIaReponse reponse) {
        ConversationIa conversation;
        if (conversationId == null) {
            conversation = new ConversationIa();
            conversation.setProprietaire(utilisateurs.getReferenceById(u.id()));
            conversation.setTitre(question.length() <= LONGUEUR_TITRE ? question : question.substring(0, LONGUEUR_TITRE));
            conversation = conversations.save(conversation);
        } else {
            conversation = chargerSienne(u, conversationId);
            conversation.setDateModification(horloge.instant());
        }
        messages.save(message(conversation, RoleMessageIa.USER, question));
        MessageConversationIa reponseEnregistree = messages.save(
                message(conversation, RoleMessageIa.ASSISTANT, reponse.message().contenu()));
        tracerUsage(u, conversation.getId(), "chat", reponse.modele(), reponse.usage());
        return new MessageChatResponse(conversation.getId(), mapper.versDto(reponseEnregistree));
    }

    // ---------------------------------------------------------------- conversations

    public PageResponse<ConversationResponse> listerConversations(Pageable pageable) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        return lecture.execute(statut -> PageResponse.de(conversations.findByProprietaireId(u.id(), pageable),
                c -> mapper.versDto(c,
                        messages.findFirstByConversationIdAndRoleOrderByDateCreationAsc(c.getId(), RoleMessageIa.USER)
                                .map(MessageConversationIa::getContenu).orElse(null),
                        messages.countByConversationId(c.getId()))));
    }

    public PageResponse<MessageAssistantResponse> listerMessages(UUID conversationId, Pageable pageable) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        return lecture.execute(statut -> {
            chargerSienne(u, conversationId);
            return PageResponse.de(messages.findByConversationIdOrderByDateCreationAsc(conversationId, pageable),
                    mapper::versDto);
        });
    }

    public void supprimerConversation(UUID conversationId) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        ecriture.executeWithoutResult(statut -> {
            ConversationIa conversation = chargerSienne(u, conversationId);
            messages.supprimerParConversation(conversationId);
            conversations.delete(conversation);
        });
    }

    // ---------------------------------------------------------------- analyse de risques

    public AnalyseRisquesResponse analyserRisques(UUID projetId) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        record Donnees(AnalyseRisquesIaRequete requete, IndicateurRisqueResponse indicateurs) {
        }
        Donnees donnees = lecture.execute(statut -> {
            Projet projet = projets.findById(projetId)
                    .orElseThrow(() -> new RessourceIntrouvableException("Projet", projetId));
            controle.verifierVoirProjet(u, projet);
            List<Map<String, Object>> lignes = activites.findAll(SpecificationsActivite.visiblesPar(u)
                            .and(SpecificationsActivite.filtres(null, null, null, projetId, null, null,
                                    LocalDate.now(horloge))),
                    PageRequest.of(0, ACTIVITES_MAX_ANALYSE, Sort.by("dateEcheance"))).getContent().stream()
                    .map(AssistantIaService::versLigneIa)
                    .toList();
            String description = projet.getDescription();
            if (description != null && description.length() > ServiceIaClient.LONGUEUR_MAX_DESCRIPTION_PROJET) {
                description = description.substring(0, ServiceIaClient.LONGUEUR_MAX_DESCRIPTION_PROJET);
            }
            return new Donnees(new AnalyseRisquesIaRequete(projet.getNom(), description, lignes, LANGUE),
                    suivi.indicateursRisque(projetId).getFirst());
        });

        AnalyseRisquesIaReponse reponse = client.analyserRisques(donnees.requete());
        ecriture.executeWithoutResult(statut -> tracerUsage(u, null, "analyze_risks", reponse.modele(), reponse.usage()));
        List<AnalyseRisquesResponse.Risque> risques = reponse.risks() == null ? List.of()
                : reponse.risks().stream()
                .map(r -> new AnalyseRisquesResponse.Risque(r.level(), r.title(), r.description(), r.recommendation()))
                .toList();
        return new AnalyseRisquesResponse(projetId, reponse.risqueGlobal(), risques, reponse.summary(),
                donnees.indicateurs(), reponse.modele());
    }

    // ---------------------------------------------------------------- rapport

    public RapportIaResponse genererRapport(RapportIaRequest requete) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        BilanResponse bilan = switch (requete.reportType()) {
            case "weekly" -> bilans.global("week", null, null);
            case "monthly" -> bilans.global("month", null, null);
            case "productivity" -> bilans.global("30d", null, null);
            case "project" -> {
                if (requete.projectId() == null) {
                    throw new RequeteInvalideException("projectId", "Le projet est obligatoire pour un rapport de projet.");
                }
                yield bilans.projet(requete.projectId(), "12m", null, null);
            }
            default -> throw new RequeteInvalideException("reportType", "Type de rapport non pris en charge.");
        };
        Map<String, Object> donnees = new LinkedHashMap<>(json.convertValue(bilan, new TypeReference<Map<String, Object>>() {
        }));
        RapportIaReponse reponse = client.genererRapport(new RapportIaRequete(requete.reportType(), donnees, LANGUE));
        ecriture.executeWithoutResult(statut -> tracerUsage(u, null, "generate_report", reponse.modele(), reponse.usage()));
        return new RapportIaResponse(requete.reportType(), reponse.title(), reponse.content(),
                reponse.sections() == null ? Map.of() : reponse.sections(), reponse.modele());
    }

    // ---------------------------------------------------------------- santé

    public SanteServiceIa sante() {
        controle.verifierAdmin(utilisateurCourant.obtenir());
        return client.verifierSante();
    }

    // ---------------------------------------------------------------- utilitaires

    /** Une conversation d'un autre utilisateur est traitée comme introuvable. */
    private ConversationIa chargerSienne(UtilisateurConnecte u, UUID conversationId) {
        return conversations.findByIdAndProprietaireId(conversationId, u.id())
                .orElseThrow(() -> new RessourceIntrouvableException("Conversation introuvable."));
    }

    private static MessageConversationIa message(ConversationIa conversation, RoleMessageIa role, String contenu) {
        MessageConversationIa message = new MessageConversationIa();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContenu(contenu);
        return message;
    }

    private void tracerUsage(UtilisateurConnecte u, UUID conversationId, String operation, String modele, UsageIa usage) {
        EvenementUsageIa evenement = new EvenementUsageIa();
        evenement.setUtilisateurId(u.id());
        evenement.setConversationId(conversationId);
        evenement.setOperation(operation);
        evenement.setModele(modele == null || modele.length() <= 100 ? modele : modele.substring(0, 100));
        if (usage != null) {
            evenement.setJetonsPrompt(usage.jetonsPrompt());
            evenement.setJetonsReponse(usage.jetonsReponse());
            evenement.setJetonsTotal(usage.jetonsTotal());
        }
        usages.save(evenement);
    }

    /** Format lu par {@code build_analyze_risks_prompt} : {@code title}, {@code status}, {@code due_date}. */
    private static Map<String, Object> versLigneIa(Activite activite) {
        Map<String, Object> ligne = new LinkedHashMap<>();
        ligne.put("title", activite.getTitre());
        ligne.put("status", activite.getStatut().json());
        ligne.put("due_date", activite.getDateEcheance() == null ? "non définie" : activite.getDateEcheance().toString());
        ligne.put("priority", activite.getPriorite().json());
        ligne.put("progress", activite.getAvancement());
        return ligne;
    }
}
