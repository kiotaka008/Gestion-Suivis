package com.gestionsuivis.backend.service.rapport;

import com.gestionsuivis.backend.dto.rapport.BilanResponse;
import com.gestionsuivis.backend.dto.rapport.GraphiqueSeries;
import com.gestionsuivis.backend.dto.rapport.ResumeProjet;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.DepartementRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.SpecificationsActivite;
import com.gestionsuivis.backend.repository.SpecificationsProjet;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import com.gestionsuivis.backend.service.CalculAvancement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bilans par employé, département, projet ou global, sur une période, calculés
 * côté backend sur les seules données visibles par l'utilisateur connecté.
 * <p>
 * Une activité appartient à la période si son échéance y tombe, ou, sans
 * échéance, si elle a été créée pendant la période.
 */
@Service
@RequiredArgsConstructor
public class BilanService {

    private static final int MOIS_MAX_GRAPHIQUE = 12;

    private final ActiviteRepository activites;
    private final ProjetRepository projets;
    private final UtilisateurRepository utilisateurs;
    private final DepartementRepository departements;
    private final CalculAvancement avancement;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;
    private final Clock horloge;

    @Transactional(readOnly = true)
    public BilanResponse global(String codePeriode, LocalDate debut, LocalDate fin) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        BilanResponse.Periode periode = Periodes.resoudre(codePeriode, debut, fin, aujourdHui());
        List<Activite> activitesScope = activites.findAll(SpecificationsActivite.visiblesPar(u));
        List<Projet> projetsScope = projets.findAll(SpecificationsProjet.visiblesPar(u));
        return construire(new BilanResponse.Perimetre("global", null, null), periode, activitesScope, projetsScope);
    }

    @Transactional(readOnly = true)
    public BilanResponse utilisateur(UUID id, String codePeriode, LocalDate debut, LocalDate fin) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Utilisateur cible = utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", id));
        controle.verifierVoirUtilisateur(u, cible);
        BilanResponse.Periode periode = Periodes.resoudre(codePeriode, debut, fin, aujourdHui());
        List<Activite> activitesScope = activites.findAll(SpecificationsActivite.visiblesPar(u)
                .and(SpecificationsActivite.filtres(null, null, null, null, id, null, aujourdHui())));
        List<Projet> projetsScope = activitesScope.stream().map(Activite::getProjet).distinct().toList();
        return construire(new BilanResponse.Perimetre("user", id, cible.getNom()), periode, activitesScope, projetsScope);
    }

    @Transactional(readOnly = true)
    public BilanResponse departement(UUID id, String codePeriode, LocalDate debut, LocalDate fin) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Departement departement = departements.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Département", id));
        if (!(u.estAdmin() || (u.estManager() && u.estDansDepartement(id)))) {
            throw new AccesInterditException("Le bilan d'un département est réservé à son responsable et aux administrateurs.");
        }
        BilanResponse.Periode periode = Periodes.resoudre(codePeriode, debut, fin, aujourdHui());
        Specification<Activite> duDepartement = (racine, requete, cb) ->
                cb.equal(racine.get("projet").get("departement").get("id"), id);
        List<Activite> activitesScope = activites.findAll(duDepartement);
        List<Projet> projetsScope = projets.findAll(SpecificationsProjet.filtres(null, null, null, id, null));
        return construire(new BilanResponse.Perimetre("department", id, departement.getNom()), periode,
                activitesScope, projetsScope);
    }

    @Transactional(readOnly = true)
    public BilanResponse projet(UUID id, String codePeriode, LocalDate debut, LocalDate fin) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Projet projet = projets.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Projet", id));
        controle.verifierVoirProjet(u, projet);
        BilanResponse.Periode periode = Periodes.resoudre(codePeriode, debut, fin, aujourdHui());
        List<Activite> activitesScope = activites.findAll(SpecificationsActivite.visiblesPar(u)
                .and(SpecificationsActivite.filtres(null, null, null, id, null, null, aujourdHui())));
        return construire(new BilanResponse.Perimetre("project", id, projet.getNom()), periode, activitesScope,
                List.of(projet));
    }

    // ---------------------------------------------------------------- calcul

    private BilanResponse construire(BilanResponse.Perimetre perimetre, BilanResponse.Periode periode,
                                     List<Activite> activitesScope, List<Projet> projetsScope) {
        LocalDate aujourdHui = aujourdHui();
        ZoneId zone = horloge.getZone();
        List<Activite> dansPeriode = activitesScope.stream()
                .filter(a -> Periodes.contient(periode, dateReference(a, zone)))
                .toList();
        Indicateurs indicateurs = Indicateurs.calculer(dansPeriode, aujourdHui);
        Map<UUID, Integer> avancements = avancement.parProjet(projetsScope.stream().map(Projet::getId).toList());

        List<ResumeProjet> resume = projetsScope.stream()
                .sorted(Comparator.comparing(Projet::getNom, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new ResumeProjet(p.getId(), p.getNom(), avancements.getOrDefault(p.getId(), 0), p.getStatut()))
                .toList();

        return new BilanResponse(perimetre, periode,
                indicateurs.total(), indicateurs.terminees(), indicateurs.enCours(), indicateurs.aFaire(),
                indicateurs.enRetard(), indicateurs.tauxRealisation(), indicateurs.tauxRetard(),
                indicateurs.heuresPrevues(), indicateurs.heuresReelles(), indicateurs.ecartHeures(),
                indicateurs.tauxEcartHeures(), indicateurs.dureeMoyenneJours(),
                projetsScope.size(), projetsScope.stream().filter(p -> p.estEnRetard(aujourdHui)).count(),
                evolutionMensuelle(activitesScope, periode, zone),
                Graphiques.statutsProjets(projetsScope, aujourdHui),
                Graphiques.prioritesActivites(dansPeriode),
                resume);
    }

    /** Activités créées et terminées par mois, sur les mois de la période (12 au plus). */
    private GraphiqueSeries evolutionMensuelle(Collection<Activite> activitesScope, BilanResponse.Periode periode,
                                               ZoneId zone) {
        long nombreMois = ChronoUnit.MONTHS.between(YearMonth.from(periode.from()), YearMonth.from(periode.to())) + 1;
        List<YearMonth> mois = Graphiques.derniersMois(periode.to(), (int) Math.min(Math.max(nombreMois, 1), MOIS_MAX_GRAPHIQUE));
        Map<YearMonth, long[]> compte = new LinkedHashMap<>();
        mois.forEach(m -> compte.put(m, new long[2]));
        for (Activite activite : activitesScope) {
            if (activite.getDateCreation() != null) {
                long[] valeurs = compte.get(YearMonth.from(activite.getDateCreation().atZone(zone)));
                if (valeurs != null) {
                    valeurs[0]++;
                }
            }
            if (activite.getDateFin() != null) {
                long[] valeurs = compte.get(YearMonth.from(activite.getDateFin().atZone(zone)));
                if (valeurs != null) {
                    valeurs[1]++;
                }
            }
        }
        List<Long> crees = new ArrayList<>();
        List<Long> terminees = new ArrayList<>();
        compte.values().forEach(v -> {
            crees.add(v[0]);
            terminees.add(v[1]);
        });
        return new GraphiqueSeries(mois.stream().map(Graphiques::libelleMois).toList(),
                List.of(new GraphiqueSeries.Serie("Créés", crees), new GraphiqueSeries.Serie("Terminés", terminees)));
    }

    static LocalDate dateReference(Activite activite, ZoneId zone) {
        if (activite.getDateEcheance() != null) {
            return activite.getDateEcheance();
        }
        return activite.getDateCreation() == null ? null : activite.getDateCreation().atZone(zone).toLocalDate();
    }

    private LocalDate aujourdHui() {
        return LocalDate.now(horloge);
    }
}
