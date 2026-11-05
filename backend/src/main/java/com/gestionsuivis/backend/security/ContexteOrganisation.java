package com.gestionsuivis.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Organisation (tenant) courante du thread.
 * <p>
 * Par défaut, c'est l'organisation de l'utilisateur authentifié ; elle peut être
 * imposée temporairement ({@link #pendant}) pour les traitements sans utilisateur
 * connecté : connexion, lecture du compte associé au JWT, tâches planifiées,
 * initialisation. Elle n'est jamais lue dans une donnée fournie par le client.
 */
public final class ContexteOrganisation {

    /** Organisation inexistante : sans contexte, aucune ligne n'est visible et aucune écriture n'est possible. */
    public static final UUID AUCUNE = new UUID(0L, 0L);

    private static final ThreadLocal<UUID> IMPOSEE = new ThreadLocal<>();

    private ContexteOrganisation() {
    }

    public static Optional<UUID> courante() {
        UUID imposee = IMPOSEE.get();
        if (imposee != null) {
            return Optional.of(imposee);
        }
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null && authentification.getPrincipal() instanceof UtilisateurConnecte u) {
            return Optional.ofNullable(u.organisationId());
        }
        return Optional.empty();
    }

    public static UUID couranteOuAucune() {
        return courante().orElse(AUCUNE);
    }

    /** Exécute {@code traitement} dans l'organisation donnée, puis restaure le contexte précédent. */
    public static <T> T pendant(UUID organisation, Supplier<T> traitement) {
        UUID precedente = IMPOSEE.get();
        IMPOSEE.set(organisation);
        try {
            return traitement.get();
        } finally {
            if (precedente == null) {
                IMPOSEE.remove();
            } else {
                IMPOSEE.set(precedente);
            }
        }
    }
}
