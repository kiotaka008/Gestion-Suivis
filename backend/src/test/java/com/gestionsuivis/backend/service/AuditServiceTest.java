package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.entity.ConvertisseurMetadonneesAudit;
import com.gestionsuivis.backend.entity.JournalAudit;
import com.gestionsuivis.backend.repository.JournalAuditRepository;
import com.gestionsuivis.backend.security.ContexteOrganisation;
import com.gestionsuivis.backend.support.Donnees;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Journal d'audit : détails filtrés (liste blanche) et conversion JSONB. */
class AuditServiceTest {

    private final JournalAuditRepository journal = mock(JournalAuditRepository.class);
    private final AuditService service = new AuditService(journal);

    @Test
    void detailsFiltres_clesInconnuesValeursNullesEtListesVidesIgnorees() {
        UUID membre = UUID.randomUUID();
        Map<String, Object> details = new java.util.HashMap<>();
        details.put(AuditService.CLE_CHAMPS, List.of("status"));
        details.put(AuditService.CLE_STATUT, "done");
        details.put(AuditService.CLE_MEMBRE, membre);
        details.put("email", "secret@exemple.com");
        details.put("password", "x");
        details.put(AuditService.CLE_TITRE_ACTIVITE, null);

        Map<String, Object> filtres = AuditService.filtrer(details);

        assertThat(filtres).containsOnlyKeys(AuditService.CLE_CHAMPS, AuditService.CLE_STATUT, AuditService.CLE_MEMBRE);
        assertThat(filtres.get(AuditService.CLE_MEMBRE)).isEqualTo(membre.toString());
        assertThat(AuditService.filtrer(Map.of(AuditService.CLE_CHAMPS, List.of()))).isNull();
    }

    @Test
    void journaliser_enregistreLesDetailsDansLOrganisationCourante() {
        UUID auteur = UUID.randomUUID();
        UUID projet = UUID.randomUUID();
        ContexteOrganisation.pendant(Donnees.ORGANISATION, () -> {
            service.journaliser(auteur, "updated", AuditService.PROJET, projet,
                    Map.of(AuditService.CLE_CHAMPS, List.of("name"), "email", "x@y.z"));
            return null;
        });

        ArgumentCaptor<JournalAudit> entree = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journal).save(entree.capture());
        assertThat(entree.getValue().getAction()).isEqualTo("project.updated");
        assertThat(entree.getValue().getMetadonnees()).isEqualTo(Map.of(AuditService.CLE_CHAMPS, List.of("name")));
    }

    @Test
    void horsOrganisation_rienNEstEnregistre() {
        service.journaliser(UUID.randomUUID(), "updated", AuditService.PROJET, UUID.randomUUID());
        verify(journal, never()).save(any());
    }

    @Test
    void siModifie_neRetientQueLesValeursFourniesEtDifferentes() {
        List<String> champs = new ArrayList<>();
        AuditService.siModifie(champs, "name", "A", "B");
        AuditService.siModifie(champs, "status", "active", "active");
        AuditService.siModifie(champs, "dueDate", "2026-01-01", null);
        AuditService.siModifie(champs, "progress", 10, 20);
        assertThat(champs).containsExactly("name", "progress");
    }

    @Test
    void convertisseurJsonb_allerRetour_etValeurIllisibleIgnoree() {
        ConvertisseurMetadonneesAudit convertisseur = new ConvertisseurMetadonneesAudit();
        Map<String, Object> details = Map.of("fields", List.of("status", "dueDate"), "status", "archived");

        String json = convertisseur.convertToDatabaseColumn(details);

        assertThat(convertisseur.convertToEntityAttribute(json)).isEqualTo(details);
        assertThat(convertisseur.convertToDatabaseColumn(Map.of())).isNull();
        assertThat(convertisseur.convertToDatabaseColumn(null)).isNull();
        assertThat(convertisseur.convertToEntityAttribute(null)).isEmpty();
        assertThat(convertisseur.convertToEntityAttribute("{pas du json")).isEmpty();
    }
}
