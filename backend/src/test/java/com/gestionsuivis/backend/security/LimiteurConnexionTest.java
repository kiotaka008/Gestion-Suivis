package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.exception.TropDeTentativesException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteurConnexionTest {

    private static final String IP = "203.0.113.10";

    /** Horloge que le test fait avancer. */
    static final class HorlogeReglable extends Clock {
        private Instant maintenant = Instant.parse("2026-11-12T08:00:00Z");

        void avancer(Duration duree) {
            maintenant = maintenant.plus(duree);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    private HorlogeReglable horloge;
    private LimiteurConnexion limiteur;

    @BeforeEach
    void initialiser() {
        horloge = new HorlogeReglable();
        limiteur = new LimiteurConnexion(horloge);
    }

    private void echouer(String email, String ip, int fois) {
        for (int i = 0; i < fois; i++) {
            limiteur.verifier(email, ip);
            limiteur.enregistrerEchec(email, ip);
        }
    }

    @Test
    void cinqEchecsParEmail_bloquentLaSixiemeTentative_avecLeDelai() {
        echouer("alice@exemple.com", IP, 5);

        assertThatThrownBy(() -> limiteur.verifier("alice@exemple.com", IP))
                .isInstanceOfSatisfying(TropDeTentativesException.class, e ->
                        assertThat(e.getDelaiAvantNouvelEssai()).isEqualTo(Duration.ofMinutes(15)))
                .hasMessageContaining("Réessayez dans 15 minutes");
    }

    @Test
    void quatreEchecs_neBloquentPas() {
        echouer("alice@exemple.com", IP, 4);
        assertThatCode(() -> limiteur.verifier("alice@exemple.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void fenetreGlissante_leBlocageSeLeveQuandLePremierEchecSortDeLaFenetre() {
        echouer("alice@exemple.com", IP, 1);
        horloge.avancer(Duration.ofMinutes(5));
        echouer("alice@exemple.com", IP, 4);
        assertThatThrownBy(() -> limiteur.verifier("alice@exemple.com", IP))
                .isInstanceOfSatisfying(TropDeTentativesException.class, e ->
                        assertThat(e.getDelaiAvantNouvelEssai()).isEqualTo(Duration.ofMinutes(10)));

        horloge.avancer(Duration.ofMinutes(10));
        assertThatCode(() -> limiteur.verifier("alice@exemple.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void emailNormalise_casseEspacesEtUnicode_partagentLeMemeCompteur() {
        echouer("Alice@Exemple.com", IP, 2);
        echouer("  alice@exemple.COM ", IP, 2);
        // « ａ » pleine chasse (U+FF41) devient « a » après normalisation NFKC.
        echouer("ａlice@exemple.com", IP, 1);

        assertThatThrownBy(() -> limiteur.verifier("alice@exemple.com", IP))
                .isInstanceOf(TropDeTentativesException.class);
    }

    @Test
    void unEmailBloque_nAffectePasLesAutresComptes() {
        echouer("alice@exemple.com", IP, 5);
        assertThatCode(() -> limiteur.verifier("bob@exemple.com", "198.51.100.7")).doesNotThrowAnyException();
    }

    @Test
    void vingtEchecsParAdresseIp_bloquentTousLesEmails() {
        for (int i = 0; i < 20; i++) {
            echouer("compte" + i + "@exemple.com", IP, 1);
        }
        assertThatThrownBy(() -> limiteur.verifier("nouveau@exemple.com", IP))
                .isInstanceOf(TropDeTentativesException.class);
        assertThatCode(() -> limiteur.verifier("nouveau@exemple.com", "198.51.100.7")).doesNotThrowAnyException();
    }

    @Test
    void connexionReussie_remetLeCompteurEmailAZero_maisPasCeluiDeLAdresseIp() {
        echouer("alice@exemple.com", IP, 4);
        for (int i = 0; i < 15; i++) {
            echouer("autre" + i + "@exemple.com", IP, 1);
        }
        limiteur.enregistrerSucces("alice@exemple.com", IP);

        // Compteur email remis à zéro : 4 nouveaux échecs ne bloquent pas l'email...
        echouer("alice@exemple.com", "198.51.100.7", 4);
        assertThatCode(() -> limiteur.verifier("alice@exemple.com", "198.51.100.7")).doesNotThrowAnyException();
        // ... mais l'adresse IP garde ses 19 échecs : un de plus et elle est bloquée.
        limiteur.enregistrerEchec("x@exemple.com", IP);
        assertThatThrownBy(() -> limiteur.verifier("y@exemple.com", IP)).isInstanceOf(TropDeTentativesException.class);
    }

    @Test
    void blocageAbusifParUnTiers_leTitulaireResteAutoriseDepuisUneAdresseDejaConnue() {
        String adresseDuTitulaire = "198.51.100.7";
        limiteur.enregistrerSucces("alice@exemple.com", adresseDuTitulaire);

        // Un tiers épuise les 5 essais de l'email depuis son adresse.
        echouer("alice@exemple.com", "192.0.2.99", 5);

        assertThatThrownBy(() -> limiteur.verifier("alice@exemple.com", "192.0.2.99"))
                .isInstanceOf(TropDeTentativesException.class);
        // Le titulaire, depuis son adresse habituelle, n'est pas bloqué.
        assertThatCode(() -> limiteur.verifier("alice@exemple.com", adresseDuTitulaire)).doesNotThrowAnyException();
        // Une nouvelle adresse, elle, reste soumise au blocage de l'email.
        assertThatThrownBy(() -> limiteur.verifier("alice@exemple.com", "203.0.113.200"))
                .isInstanceOf(TropDeTentativesException.class);
    }

    @Test
    void adresseConnue_expireApresTrenteJours() {
        limiteur.enregistrerSucces("alice@exemple.com", IP);
        horloge.avancer(LimiteurConnexion.DUREE_CONFIANCE.plusSeconds(1));
        echouer("alice@exemple.com", "192.0.2.99", 5);
        assertThatThrownBy(() -> limiteur.verifier("alice@exemple.com", IP)).isInstanceOf(TropDeTentativesException.class);
    }

    @Test
    void nettoyage_supprimeLesCompteursExpires() {
        echouer("alice@exemple.com", IP, 3);
        assertThat(limiteur.nombreDeCompteurs()).isEqualTo(2);

        horloge.avancer(LimiteurConnexion.FENETRE.plusSeconds(1));
        limiteur.nettoyer();

        assertThat(limiteur.nombreDeCompteurs()).isZero();
    }

    @Test
    void emailAbsent_seuleLaLimiteParAdresseIpSApplique() {
        for (int i = 0; i < 20; i++) {
            limiteur.verifier(null, IP);
            limiteur.enregistrerEchec(null, IP);
        }
        assertThat(limiteur.nombreDeCompteurs()).isEqualTo(1);
        assertThatThrownBy(() -> limiteur.verifier(null, IP)).isInstanceOf(TropDeTentativesException.class);
    }

    @Test
    void accesConcurrents_aucunEchecPerdu() throws Exception {
        ExecutorService taches = Executors.newFixedThreadPool(8);
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<?>> resultats = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            resultats.add(taches.submit(() -> {
                depart.await();
                for (int j = 0; j < 100; j++) {
                    limiteur.enregistrerEchec("cible@exemple.com", "10.0.0." + (j % 50));
                }
                return null;
            }));
        }
        depart.countDown();
        for (Future<?> resultat : resultats) {
            resultat.get(10, TimeUnit.SECONDS);
        }
        taches.shutdown();

        // 800 échecs enregistrés en parallèle : l'email est bloqué, et chacune des 50 adresses
        // a reçu 16 échecs (sous la limite de 20) sans qu'aucun ne soit perdu.
        assertThatThrownBy(() -> limiteur.verifier("cible@exemple.com", "10.0.0.1"))
                .isInstanceOf(TropDeTentativesException.class);
        assertThatCode(() -> limiteur.verifier(null, "10.0.0.1")).doesNotThrowAnyException();
        for (int j = 0; j < 4; j++) {
            limiteur.enregistrerEchec(null, "10.0.0.1");
        }
        assertThatThrownBy(() -> limiteur.verifier(null, "10.0.0.1")).isInstanceOf(TropDeTentativesException.class);
    }

    @Test
    void adresseConnuePartagee_unAttaquantSurLaMemeAdresseNObtientAucunEssaiDePlus() {
        // Le titulaire s'est déjà connecté depuis l'adresse partagée (Wi-Fi d'établissement, proxy...).
        String adressePartagee = "198.51.100.7";
        limiteur.enregistrerSucces("alice@exemple.com", adressePartagee);

        // Un attaquant sur la même adresse : 5 échecs suffisent à bloquer l'email depuis cette adresse.
        echouer("alice@exemple.com", adressePartagee, LimiteurConnexion.MAX_ECHECS_EMAIL);

        assertThatThrownBy(() -> limiteur.verifier("alice@exemple.com", adressePartagee))
                .isInstanceOf(TropDeTentativesException.class);
    }

    @Test
    void adresseConnue_compteurDuCoupleRemisAZeroApresUneConnexionReussie() {
        limiteur.enregistrerSucces("alice@exemple.com", IP);
        echouer("alice@exemple.com", IP, LimiteurConnexion.MAX_ECHECS_EMAIL - 1);
        limiteur.enregistrerSucces("alice@exemple.com", IP);

        echouer("alice@exemple.com", IP, LimiteurConnexion.MAX_ECHECS_EMAIL - 1);
        assertThatCode(() -> limiteur.verifier("alice@exemple.com", IP)).doesNotThrowAnyException();
    }
}
