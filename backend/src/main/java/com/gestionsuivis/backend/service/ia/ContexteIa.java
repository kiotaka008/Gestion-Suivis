package com.gestionsuivis.backend.service.ia;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.service.rapport.Indicateurs;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Résumé textuel des données de suivi visibles par l'utilisateur, ajouté à sa
 * question avant l'envoi au service IA (le service IA ignore les messages
 * {@code system}). Ne contient ni email ni nom de collaborateur ; longueur bornée.
 */
public final class ContexteIa {

    public static final int LONGUEUR_MAX = 6000;
    private static final int NOMBRE_MAX_LIGNES = 10;

    private ContexteIa() {
    }

    public static String construire(String role, Collection<Projet> projets, Map<java.util.UUID, Integer> avancements,
                                    Collection<Activite> activites, LocalDate aujourdHui) {
        Indicateurs indicateurs = Indicateurs.calculer(activites, aujourdHui);
        StringBuilder texte = new StringBuilder();
        texte.append("Contexte de suivi (données accessibles à l'utilisateur, rôle ").append(role)
                .append(", au ").append(aujourdHui).append(") :\n");
        texte.append("- Projets visibles : ").append(projets.size())
                .append(" (en retard : ").append(projets.stream().filter(p -> p.estEnRetard(aujourdHui)).count())
                .append(")\n");
        texte.append("- Activités visibles : ").append(indicateurs.total())
                .append(" (terminées ").append(indicateurs.terminees())
                .append(", en cours ").append(indicateurs.enCours())
                .append(", à faire ").append(indicateurs.aFaire())
                .append(", en retard ").append(indicateurs.enRetard())
                .append(" ; taux de réalisation ").append(indicateurs.tauxRealisation()).append(" %)\n");

        List<Projet> projetsEnRetard = projets.stream().filter(p -> p.estEnRetard(aujourdHui))
                .sorted(Comparator.comparing(Projet::getDateEcheance)).limit(NOMBRE_MAX_LIGNES).toList();
        if (!projetsEnRetard.isEmpty()) {
            texte.append("Projets en retard :\n");
            projetsEnRetard.forEach(p -> texte.append("  • ").append(p.getNom()).append(" — échéance ")
                    .append(p.getDateEcheance()).append(", avancement ")
                    .append(avancements.getOrDefault(p.getId(), 0)).append(" %\n"));
        }
        List<Activite> activitesEnRetard = activites.stream().filter(a -> a.estEnRetard(aujourdHui))
                .sorted(Comparator.comparing(Activite::getDateEcheance, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(NOMBRE_MAX_LIGNES).toList();
        if (!activitesEnRetard.isEmpty()) {
            texte.append("Activités en retard :\n");
            activitesEnRetard.forEach(a -> texte.append("  • ").append(a.getTitre()).append(" (projet ")
                    .append(a.getProjet().getNom()).append(") — échéance ").append(a.getDateEcheance())
                    .append(", priorité ").append(a.getPriorite().json()).append(", avancement ")
                    .append(a.getAvancement()).append(" %\n"));
        }
        return texte.length() <= LONGUEUR_MAX ? texte.toString() : texte.substring(0, LONGUEUR_MAX) + "…\n";
    }

    /** Message envoyé au service IA : contexte puis question (seule la question est enregistrée). */
    public static String enrichir(String contexte, String question) {
        return contexte + "\nQuestion de l'utilisateur : " + question;
    }
}
