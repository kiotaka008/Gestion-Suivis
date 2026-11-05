package com.gestionsuivis.backend.config;

import com.gestionsuivis.backend.security.ContexteOrganisation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Exécute un traitement dans une <b>nouvelle</b> transaction rattachée à une
 * organisation donnée. L'organisation doit être fixée avant l'ouverture de la
 * session Hibernate (filtre {@code @TenantId}) et de la connexion (contexte RLS) :
 * on ne peut donc pas la changer au milieu d'une transaction existante.
 */
@Component
public class ExecuteurOrganisation {

    private final TransactionTemplate lecture;
    private final TransactionTemplate ecriture;

    public ExecuteurOrganisation(PlatformTransactionManager transactions) {
        this.lecture = new TransactionTemplate(transactions);
        this.lecture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.lecture.setReadOnly(true);
        this.ecriture = new TransactionTemplate(transactions);
        this.ecriture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T lire(UUID organisation, Supplier<T> traitement) {
        return ContexteOrganisation.pendant(organisation, () -> lecture.execute(statut -> traitement.get()));
    }

    public <T> T ecrire(UUID organisation, Supplier<T> traitement) {
        return ContexteOrganisation.pendant(organisation, () -> ecriture.execute(statut -> traitement.get()));
    }

    public void ecrire(UUID organisation, Runnable traitement) {
        ecrire(organisation, () -> {
            traitement.run();
            return null;
        });
    }
}
