package com.gestionsuivis.backend.contrat;

import com.gestionsuivis.backend.dto.activite.ActiviteResponse;
import com.gestionsuivis.backend.dto.activite.CreerActiviteRequest;
import com.gestionsuivis.backend.dto.activite.ModifierActiviteRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionRequest;
import com.gestionsuivis.backend.dto.auth.ConnexionResponse;
import com.gestionsuivis.backend.dto.notification.NotificationResponse;
import com.gestionsuivis.backend.dto.projet.CreerProjetRequest;
import com.gestionsuivis.backend.dto.projet.ProjetResponse;
import com.gestionsuivis.backend.dto.utilisateur.CreerUtilisateurRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.TypeNotification;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compatibilité JSON avec le frontend React (branche feature/frontend, commit af3e3fa) :
 * noms de propriétés de {@code src/types/models.ts}, valeurs des énumérations et
 * corps réellement envoyés par les formulaires ({@code Create*Modal}, {@code LoginPage}).
 */
class ContratFrontendJsonTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID ID = UUID.fromString("3b6e7c1a-0000-4000-8000-000000000001");
    private static final Instant CREATION = Instant.parse("2025-01-10T09:00:00Z");

    /** {@code interface User} de models.ts. */
    private static final List<String> CHAMPS_USER = List.of("id", "email", "name", "role", "avatarUrl", "isActive", "createdAt");
    /** {@code interface Project} de models.ts. */
    private static final List<String> CHAMPS_PROJECT = List.of("id", "name", "description", "status", "priority", "progress",
            "ownerId", "owner", "memberIds", "members", "startDate", "dueDate", "createdAt", "updatedAt");
    /** {@code interface Activity} de models.ts. */
    private static final List<String> CHAMPS_ACTIVITY = List.of("id", "projectId", "title", "description", "status",
            "priority", "progress", "assigneeId", "assignee", "dueDate", "createdAt", "updatedAt");
    /** {@code interface Notification} de models.ts. */
    private static final List<String> CHAMPS_NOTIFICATION = List.of("id", "type", "title", "message", "isRead", "createdAt");

    @Test
    void utilisateur_contientLesChampsDuTypeUserEtJamaisDeMotDePasse() {
        JsonNode json = arbre(utilisateur());

        CHAMPS_USER.forEach(champ -> assertThat(json.has(champ)).as(champ).isTrue());
        assertThat(json.get("role").asString()).isEqualTo("member");
        assertThat(json.get("isActive").asBoolean()).isTrue();
        assertThat(json.has("active")).isFalse();
        assertThat(json.get("createdAt").asString()).isEqualTo("2025-01-10T09:00:00Z");
        assertThat(JSON.writeValueAsString(utilisateur()).toLowerCase()).doesNotContain("password").doesNotContain("hash");
    }

    @Test
    void connexion_renvoieUniquementUser() {
        JsonNode json = arbre(new ConnexionResponse(utilisateur()));

        assertThat(json.propertyNames()).containsExactly("user");
        assertThat(json.get("user").get("email").asString()).isEqualTo("rickael@company.com");
    }

    @Test
    void projet_contientLesChampsDuTypeProject() {
        ProjetResponse projet = new ProjetResponse(ID, "Refonte du site web", "Modernisation", StatutProjet.EN_COURS,
                Priorite.HAUTE, 75, ID, utilisateur(), List.of(ID), List.of(utilisateur()),
                LocalDate.parse("2025-03-01"), LocalDate.parse("2025-10-30"), null, null, false, CREATION, CREATION);

        JsonNode json = arbre(projet);

        CHAMPS_PROJECT.forEach(champ -> assertThat(json.has(champ)).as(champ).isTrue());
        assertThat(json.get("status").asString()).isEqualTo("active");
        assertThat(json.get("priority").asString()).isEqualTo("high");
        assertThat(json.get("startDate").asString()).isEqualTo("2025-03-01");
        assertThat(json.get("memberIds").get(0).asString()).isEqualTo(ID.toString());
        assertThat(json.get("isOverdue").asBoolean()).isFalse();
    }

    @Test
    void activite_contientLesChampsDuTypeActivity() {
        ActiviteResponse activite = new ActiviteResponse(ID, ID, "Projet", "Maquettes", null, StatutActivite.EN_COURS,
                Priorite.MOYENNE, 40, ID, utilisateur(), null, LocalDate.parse("2025-10-15"),
                new BigDecimal("8.00"), new BigDecimal("10.50"), true, null, CREATION, CREATION);

        JsonNode json = arbre(activite);

        CHAMPS_ACTIVITY.forEach(champ -> assertThat(json.has(champ)).as(champ).isTrue());
        assertThat(json.get("status").asString()).isEqualTo("in_progress");
        assertThat(json.get("priority").asString()).isEqualTo("medium");
        assertThat(json.get("plannedHours").asDouble()).isEqualTo(8.0);
        assertThat(json.get("isOverdue").asBoolean()).isTrue();
    }

    @Test
    void notification_contientLesChampsDuTypeNotification() {
        JsonNode json = arbre(new NotificationResponse(ID, TypeNotification.ECHEANCE, "Échéance proche", "Message",
                false, CREATION, null, ID));

        CHAMPS_NOTIFICATION.forEach(champ -> assertThat(json.has(champ)).as(champ).isTrue());
        assertThat(json.get("type").asString()).isEqualTo("deadline");
        assertThat(json.get("isRead").asBoolean()).isFalse();
    }

    @Test
    void enumerations_correspondentAuxValeursDuFrontend() {
        assertThat(List.of(Role.values()).stream().map(Role::json)).containsExactly("admin", "manager", "member");
        assertThat(List.of(Priorite.values()).stream().map(Priorite::json)).containsExactly("low", "medium", "high", "urgent");
        assertThat(StatutProjet.depuisJson("on_hold")).isEqualTo(StatutProjet.EN_ATTENTE);
        assertThat(StatutProjet.depuisJson("completed")).isEqualTo(StatutProjet.TERMINE);
        assertThat(StatutActivite.depuisJson("todo")).isEqualTo(StatutActivite.A_FAIRE);
        assertThat(StatutActivite.depuisJson("done")).isEqualTo(StatutActivite.TERMINE);
        assertThat(Role.depuisJson("member")).isEqualTo(Role.EMPLOYE);
        assertThat(List.of(TypeNotification.values()).stream().map(TypeNotification::json))
                .containsExactly("project", "activity", "comment", "deadline", "ai", "user");
    }

    @Test
    void valeursSansEquivalent_sontRefuseesExplicitement() {
        assertThatThrownBy(() -> StatutProjet.depuisJson("archived")).hasMessageContaining("archived");
        assertThatThrownBy(() -> StatutActivite.depuisJson("blocked")).hasMessageContaining("blocked");
    }

    @Test
    void corpsDeLoginPage_estLu() {
        ConnexionRequest requete = JSON.readValue("{\"email\":\"rickael@company.com\",\"password\":\"secret123\"}",
                ConnexionRequest.class);

        assertThat(requete.email()).isEqualTo("rickael@company.com");
        assertThat(requete.toString()).doesNotContain("secret123");
    }

    @Test
    void corpsDeCreateProjectModal_estLuEtProgressIgnore() {
        CreerProjetRequest requete = JSON.readValue("""
                {"name":"Migration cloud","description":"Passage sur le cloud","priority":"medium",
                 "dueDate":"2025-12-31","status":"active","progress":0}""", CreerProjetRequest.class);

        assertThat(requete.name()).isEqualTo("Migration cloud");
        assertThat(requete.priority()).isEqualTo(Priorite.MOYENNE);
        assertThat(requete.status()).isEqualTo(StatutProjet.EN_COURS);
        assertThat(requete.dueDate()).isEqualTo(LocalDate.parse("2025-12-31"));
    }

    @Test
    void corpsDeCreateActivityModal_estLu() {
        CreerActiviteRequest requete = JSON.readValue("{\"title\":\"Maquettes\",\"projectId\":\"" + ID
                + "\",\"priority\":\"urgent\",\"dueDate\":\"2025-10-15\",\"status\":\"todo\"}", CreerActiviteRequest.class);

        assertThat(requete.projectId()).isEqualTo(ID);
        assertThat(requete.priority()).isEqualTo(Priorite.URGENTE);
        assertThat(requete.status()).isEqualTo(StatutActivite.A_FAIRE);
    }

    @Test
    void corpsDeCreateUserModal_estLuMaisLeMotDePasseRestObligatoire() {
        CreerUtilisateurRequest requete = JSON.readValue(
                "{\"name\":\"Tiavina\",\"email\":\"tiavina@company.com\",\"role\":\"member\",\"isActive\":true}",
                CreerUtilisateurRequest.class);

        assertThat(requete.role()).isEqualTo(Role.EMPLOYE);
        assertThat(requete.isActive()).isTrue();
        assertThat(requete.password()).isNull();
    }

    @Test
    void patchPartielDActivite_parLaPersonneAffectee() {
        ModifierActiviteRequest suivi = JSON.readValue("{\"status\":\"done\",\"progress\":100,\"actualHours\":6.5}",
                ModifierActiviteRequest.class);
        ModifierActiviteRequest gestion = JSON.readValue("{\"title\":\"Autre titre\"}", ModifierActiviteRequest.class);

        assertThat(suivi.limiteeAuSuivi()).isTrue();
        assertThat(gestion.limiteeAuSuivi()).isFalse();
    }

    private static UtilisateurResponse utilisateur() {
        return new UtilisateurResponse(ID, "rickael@company.com", "Rickael Brayan", Role.EMPLOYE, null, true,
                CREATION, null, null, null);
    }

    private static JsonNode arbre(Object valeur) {
        return JSON.readTree(JSON.writeValueAsString(valeur));
    }
}
