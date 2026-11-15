package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.exception.TropDeTentativesException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Limitation des tentatives de connexion échouées (protection contre la recherche de mot de passe
 * par essais répétés), sans dépendance externe.
 *
 * <h2>Règles</h2>
 * <ul>
 *   <li>5 échecs par email sur une fenêtre glissante de 15 minutes ;</li>
 *   <li>20 échecs par adresse IP sur 15 minutes, tous emails confondus ;</li>
 *   <li>au-delà : refus immédiat (HTTP 429 + en-tête {@code Retry-After}), avant toute vérification
 *       du mot de passe, avec la même réponse que le compte existe ou non ;</li>
 *   <li>une connexion réussie remet à zéro le compteur de l'email, mais pas celui de l'adresse IP
 *       (un attaquant qui possède un compte ne peut pas « recharger » ses essais sur les autres).</li>
 * </ul>
 *
 * <h2>Contournements évités</h2>
 * <ul>
 *   <li>Emails normalisés (Unicode NFKC, espaces, casse) : {@code " Alice@X.com"} et
 *       {@code "alice@x.com"} partagent le même compteur.</li>
 *   <li>L'adresse IP est {@code request.getRemoteAddr()} : l'en-tête {@code X-Forwarded-For} n'est
 *       jamais lu directement (il est falsifiable). Derrière un proxy inverse, il faut configurer
 *       {@code server.forward-headers-strategy=native} et la liste des proxies de confiance
 *       (voir application.properties) pour que Tomcat remplace l'adresse par celle du client.</li>
 * </ul>
 *
 * <h2>Blocage abusif d'un compte par un tiers</h2>
 * Le blocage par email est temporaire (fenêtre glissante). De plus, un couple email + adresse IP
 * qui s'est déjà connecté avec succès est mémorisé 30 jours : depuis cette adresse, le compteur
 * global de l'email (alimenté par n'importe qui, n'importe où) ne s'applique pas ; c'est un compteur
 * propre au couple, avec la même limite de 5 échecs sur 15 minutes, qui s'applique à la place.
 * Le titulaire n'est donc pas bloqué par les échecs d'un tiers situé ailleurs, et une personne qui
 * partage son adresse IP (Wi-Fi d'établissement, opérateur mobile, proxy) n'obtient aucun essai de
 * plus qu'ailleurs. La limite par adresse IP s'applique dans tous les cas.
 *
 * <h2>Limites du stockage en mémoire</h2>
 * Les compteurs sont perdus au redémarrage et ne sont pas partagés entre plusieurs instances du
 * backend (chacune applique ses propres limites). Leur taille est bornée ({@value #MAX_CLES}
 * compteurs, {@value #MAX_PAIRES_CONNUES} couples connus) et un nettoyage des entrées expirées a
 * lieu toutes les 5 minutes ; si la borne est atteinte malgré le nettoyage, les nouveaux compteurs
 * ne sont plus créés (les compteurs existants continuent de s'appliquer). Pour plusieurs instances,
 * il faudrait un stockage partagé (base ou cache).
 */
@Slf4j
@Component
public class LimiteurConnexion {

    public static final int MAX_ECHECS_EMAIL = 5;
    public static final int MAX_ECHECS_IP = 20;
    public static final Duration FENETRE = Duration.ofMinutes(15);
    public static final Duration DUREE_CONFIANCE = Duration.ofDays(30);
    static final int MAX_CLES = 50_000;
    static final int MAX_PAIRES_CONNUES = 50_000;

    private static final String PREFIXE_EMAIL = "email:";
    private static final String PREFIXE_IP = "ip:";
    private static final String PREFIXE_PAIRE = "paire:";

    /** Dates des échecs récents, par clé ; chaque file est modifiée sous le verrou de sa clé (compute). */
    private final ConcurrentHashMap<String, Deque<Instant>> echecs = new ConcurrentHashMap<>();
    /** Couples « email|ip » déjà connectés avec succès → fin de validité. */
    private final ConcurrentHashMap<String, Instant> pairesConnues = new ConcurrentHashMap<>();
    private final AtomicBoolean saturationSignalee = new AtomicBoolean();
    private final Clock horloge;

    @Autowired
    public LimiteurConnexion(Clock horloge) {
        this.horloge = horloge;
    }

    /**
     * Refuse la tentative si l'adresse IP ou l'email a dépassé sa limite.
     *
     * @param email email saisi (peut être {@code null}, ex. connexion Google avant vérification)
     * @throws TropDeTentativesException avec le délai avant le prochain essai possible
     */
    public void verifier(String email, String ip) {
        Instant maintenant = horloge.instant();
        Duration attente = delaiRestant(PREFIXE_IP + cleIp(ip), MAX_ECHECS_IP, maintenant);
        String cleEmail = normaliserEmail(email);
        if (cleEmail != null) {
            // Adresse déjà connue pour ce compte : compteur propre au couple, sinon compteur global de l'email.
            String cle = estConnue(cleEmail, cleIp(ip), maintenant)
                    ? PREFIXE_PAIRE + paire(cleEmail, ip)
                    : PREFIXE_EMAIL + cleEmail;
            Duration attenteEmail = delaiRestant(cle, MAX_ECHECS_EMAIL, maintenant);
            if (attenteEmail.compareTo(attente) > 0) {
                attente = attenteEmail;
            }
        }
        if (!attente.isZero()) {
            log.warn("Connexion refusée : trop de tentatives échouées (nouvel essai dans {} s)", attente.toSeconds());
            throw new TropDeTentativesException(attente);
        }
    }

    /** Enregistre un échec (mauvais identifiants) pour l'adresse IP et, s'il est connu, l'email. */
    public void enregistrerEchec(String email, String ip) {
        Instant maintenant = horloge.instant();
        ajouter(PREFIXE_IP + cleIp(ip), MAX_ECHECS_IP, maintenant);
        String cleEmail = normaliserEmail(email);
        if (cleEmail != null) {
            ajouter(PREFIXE_EMAIL + cleEmail, MAX_ECHECS_EMAIL, maintenant);
            if (estConnue(cleEmail, cleIp(ip), maintenant)) {
                ajouter(PREFIXE_PAIRE + paire(cleEmail, ip), MAX_ECHECS_EMAIL, maintenant);
            }
        }
    }

    /** Connexion réussie : remise à zéro du compteur de l'email et mémorisation du couple email + IP. */
    public void enregistrerSucces(String email, String ip) {
        String cleEmail = normaliserEmail(email);
        if (cleEmail == null) {
            return;
        }
        echecs.remove(PREFIXE_EMAIL + cleEmail);
        echecs.remove(PREFIXE_PAIRE + paire(cleEmail, ip));
        if (pairesConnues.size() >= MAX_PAIRES_CONNUES) {
            nettoyer();
        }
        if (pairesConnues.size() < MAX_PAIRES_CONNUES) {
            pairesConnues.put(paire(cleEmail, ip), horloge.instant().plus(DUREE_CONFIANCE));
        }
    }

    /** Supprime les compteurs et couples expirés (toutes les 5 minutes). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void nettoyer() {
        Instant maintenant = horloge.instant();
        Instant limite = maintenant.minus(FENETRE);
        echecs.keySet().forEach(cle -> echecs.computeIfPresent(cle, (k, dates) -> {
            purger(dates, limite);
            return dates.isEmpty() ? null : dates;
        }));
        pairesConnues.entrySet().removeIf(e -> !e.getValue().isAfter(maintenant));
        if (echecs.size() < MAX_CLES) {
            saturationSignalee.set(false);
        }
    }

    /** Email normalisé (NFKC, sans espaces autour, minuscules) ; {@code null} si absent. */
    public static String normaliserEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalise = Normalizer.normalize(email, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
        return normalise.isEmpty() ? null : normalise;
    }

    int nombreDeCompteurs() {
        return echecs.size();
    }

    // ---------------------------------------------------------------- interne

    private static String cleIp(String ip) {
        return ip == null || ip.isBlank() ? "inconnue" : ip.strip();
    }

    private static String paire(String cleEmail, String ip) {
        return cleEmail + "|" + cleIp(ip);
    }

    private boolean estConnue(String cleEmail, String ip, Instant maintenant) {
        Instant fin = pairesConnues.get(paire(cleEmail, ip));
        return fin != null && fin.isAfter(maintenant);
    }

    /** Temps restant avant que la clé repasse sous sa limite ({@link Duration#ZERO} si elle n'est pas atteinte). */
    private Duration delaiRestant(String cle, int maximum, Instant maintenant) {
        Instant limite = maintenant.minus(FENETRE);
        Duration[] attente = {Duration.ZERO};
        echecs.computeIfPresent(cle, (k, dates) -> {
            purger(dates, limite);
            if (dates.size() >= maximum) {
                // L'échec le plus ancien encore compté sort de la fenêtre à cette date.
                Instant liberation = dates.peekFirst().plus(FENETRE);
                attente[0] = Duration.between(maintenant, liberation);
                if (attente[0].isNegative() || attente[0].isZero()) {
                    attente[0] = Duration.ofSeconds(1);
                }
            }
            return dates.isEmpty() ? null : dates;
        });
        return attente[0];
    }

    private void ajouter(String cle, int maximum, Instant maintenant) {
        if (!echecs.containsKey(cle) && echecs.size() >= MAX_CLES) {
            nettoyer();
            if (echecs.size() >= MAX_CLES) {
                if (saturationSignalee.compareAndSet(false, true)) {
                    log.warn("Limiteur de connexion saturé ({} compteurs) : nouveaux compteurs ignorés", MAX_CLES);
                }
                return;
            }
        }
        Instant limite = maintenant.minus(FENETRE);
        echecs.compute(cle, (k, dates) -> {
            Deque<Instant> liste = dates == null ? new ArrayDeque<>() : dates;
            purger(liste, limite);
            liste.addLast(maintenant);
            // Inutile de garder plus de dates que la limite : seules les plus récentes comptent.
            while (liste.size() > maximum) {
                liste.removeFirst();
            }
            return liste;
        });
    }

    private static void purger(Deque<Instant> dates, Instant limite) {
        while (!dates.isEmpty() && !dates.peekFirst().isAfter(limite)) {
            dates.removeFirst();
        }
    }
}
