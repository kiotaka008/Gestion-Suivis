package com.gestionsuivis.backend.exception;

import com.gestionsuivis.backend.entity.StatutProjet;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.json.JsonMapper;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du gestionnaire central : instanciation directe, sans contexte
 * Spring, sans serveur et sans base de données. Vérifie le format commun avec le
 * frontend ({@code timestamp, status, code, message, path, errors[{field, message}]}).
 */
class GlobalExceptionHandlerTest {

    private static final String CHEMIN = "/api/test";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest requete;

    @BeforeEach
    void initialiser() {
        handler = new GlobalExceptionHandler();
        requete = new MockHttpServletRequest("POST", CHEMIN);
    }

    // ---------------------------------------------------------------- 422

    @Test
    void corpsInvalide_renvoie422AvecErreursParChamp() throws Exception {
        BeanPropertyBindingResult resultat = new BeanPropertyBindingResult(new Object(), "utilisateurDto");
        resultat.addError(new FieldError("utilisateurDto", "email", "L'email est obligatoire."));
        resultat.addError(new FieldError("utilisateurDto", "password", "S3cr3t!", false,
                null, null, "Le mot de passe est trop court."));
        resultat.addError(new ObjectError("utilisateurDto", "Les mots de passe ne correspondent pas."));
        MethodParameter parametre = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("methodeFictive", String.class), 0);

        ResponseEntity<ErreurResponse> reponse = handler.gererCorpsInvalide(
                new MethodArgumentNotValidException(parametre, resultat), requete);

        ErreurResponse corps = verifierBase(reponse, 422, "VALIDATION_ERROR");
        assertThat(corps.message()).isEqualTo(MessagesErreur.DONNEES_INVALIDES);
        assertThat(corps.errors()).containsExactly(
                new ErreurChamp("email", "L'email est obligatoire."),
                new ErreurChamp("password", "Le mot de passe est trop court."),
                new ErreurChamp("utilisateurDto", "Les mots de passe ne correspondent pas."));
        assertThat(serialiser(corps)).doesNotContain("S3cr3t!");
    }

    @Test
    void contrainteInvalide_renvoie422AvecNomDuChamp() {
        try (ValidatorFactory fabrique = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<ObjetFictif>> violations = fabrique.getValidator().validate(new ObjetFictif());

            ErreurResponse corps = verifierBase(handler.gererContraintesInvalides(
                    new ConstraintViolationException("detail interne", violations), requete), 422, "VALIDATION_ERROR");

            assertThat(corps.errors()).containsExactly(new ErreurChamp("name", "Le nom est obligatoire."));
        }
    }

    @Test
    void incoherenceMetier_renvoie422AvecLeChampConcerne() {
        ErreurResponse corps = verifierBase(handler.gererRequeteInvalide(new RequeteInvalideException("dueDate",
                "L'échéance doit être postérieure ou égale à la date de début."), requete), 422, "VALIDATION_ERROR");

        assertThat(corps.message()).isEqualTo("L'échéance doit être postérieure ou égale à la date de début.");
        assertThat(corps.errors()).containsExactly(new ErreurChamp("dueDate",
                "L'échéance doit être postérieure ou égale à la date de début."));
    }

    @Test
    void valeurEnumerationInconnue_renvoie422SurLeChamp() {
        record Corps(StatutProjet status) {
        }
        HttpMessageNotReadableException ex = null;
        try {
            JSON.readValue("{\"status\":\"cancelled\"}", Corps.class);
        } catch (RuntimeException erreur) {
            ex = new HttpMessageNotReadableException("JSON parse error", erreur, new MockHttpInputMessage(new byte[0]));
        }
        assertThat(ex).isNotNull();

        ErreurResponse corps = verifierBase(handler.gererCorpsIllisible(ex, requete), 422, "VALIDATION_ERROR");

        assertThat(corps.errors()).hasSize(1);
        assertThat(corps.errors().getFirst().field()).isEqualTo("status");
        assertThat(corps.errors().getFirst().message()).contains("cancelled").contains("active").contains("archived");
    }

    // ---------------------------------------------------------------- 400

    @Test
    void jsonMalForme_renvoie400SansDetailTechnique() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character at line 1 column 7", new MockHttpInputMessage(new byte[0]));

        ErreurResponse corps = verifierBase(handler.gererCorpsIllisible(ex, requete), 400, "BAD_REQUEST");

        assertThat(corps.message()).isEqualTo(MessagesErreur.CORPS_ILLISIBLE);
        assertThat(corps.errors()).isEmpty();
    }

    @Test
    void parametreDeMauvaisType_renvoie400() throws Exception {
        MethodParameter parametre = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("methodeFictive", String.class), 0);

        ErreurResponse corps = verifierBase(handler.gererTypeParametre(new MethodArgumentTypeMismatchException(
                "abc", java.util.UUID.class, "id", parametre, new IllegalArgumentException("x")), requete), 400, "BAD_REQUEST");

        assertThat(corps.message()).isEqualTo("Le paramètre 'id' a une valeur invalide.");
    }

    @Test
    void parametreManquant_renvoie400() {
        ErreurResponse corps = verifierBase(handler.gererParametreManquant(
                new MissingServletRequestParameterException("from", "LocalDate"), requete), 400, "BAD_REQUEST");

        assertThat(corps.message()).isEqualTo("Le paramètre obligatoire 'from' est manquant.");
    }

    @Test
    void triInconnu_renvoie400SansNomDeClasse() {
        PropertyReferenceException ex = new PropertyReferenceException(
                "inexistant", TypeInformation.of(ClasseFictive.class), List.of());

        ErreurResponse corps = verifierBase(handler.gererTriInconnu(ex, requete), 400, "BAD_REQUEST");

        assertThat(corps.message()).isEqualTo("Le critère de tri 'inexistant' n'est pas reconnu.").doesNotContain("ClasseFictive");
    }

    @Test
    void parametreInvalide_renvoie400() {
        verifierBase(handler.gererParametreInvalide(new ParametreInvalideException("x"), requete), 400, "BAD_REQUEST");
    }

    // ---------------------------------------------------------------- 401 / 403

    @Test
    void identifiantsInvalides_renvoie401AvecMessageDeConnexion() {
        ErreurResponse corps = verifierBase(handler.gererIdentifiantsInvalides(
                new IdentifiantsInvalidesException(), requete), 401, "UNAUTHORIZED");

        assertThat(corps.message()).isEqualTo("Email ou mot de passe incorrect.");
    }

    @Test
    void authentificationRefusee_renvoie401() {
        ErreurResponse corps = verifierBase(handler.gererNonAuthentifie(
                new BadCredentialsException("Bad credentials"), requete), 401, "UNAUTHORIZED");

        assertThat(corps.message()).isEqualTo(MessagesErreur.NON_AUTHENTIFIE);
    }

    @Test
    void accesRefuse_renvoie403() {
        verifierBase(handler.gererAccesRefuse(new AccessDeniedException("Access Denied"), requete), 403, "FORBIDDEN");
        ErreurResponse corps = verifierBase(handler.gererAccesInterdit(new AccesInterditException(), requete),
                403, "FORBIDDEN");
        assertThat(corps.message()).isEqualTo(MessagesErreur.ACCES_INTERDIT);
    }

    // ---------------------------------------------------------------- 404 / 405 / 415 / 409

    @Test
    void ressourceIntrouvable_renvoie404() {
        ErreurResponse corps = verifierBase(handler.gererRessourceIntrouvable(
                new RessourceIntrouvableException("Projet", 12), requete), 404, "NOT_FOUND");
        assertThat(corps.message()).isEqualTo("Projet introuvable avec l'identifiant 12.");
        verifierBase(handler.gererUrlInconnue(new NoResourceFoundException(HttpMethod.GET, CHEMIN, "api/test"), requete),
                404, "NOT_FOUND");
    }

    @Test
    void methodeNonAutorisee_renvoie405AvecEnteteAllow() {
        ResponseEntity<ErreurResponse> reponse = handler.gererMethodeNonAutorisee(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST")), requete);

        verifierBase(reponse, 405, "METHOD_NOT_ALLOWED");
        assertThat(reponse.getHeaders().getFirst(HttpHeaders.ALLOW)).contains("GET").contains("POST");
    }

    @Test
    void typeDeContenuNonSupporte_renvoie415() {
        verifierBase(handler.gererTypeContenu(new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)), requete), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    // ---------------------------------------------------------------- 409 métier

    @Test
    void conflitMetier_renvoie409AvecLeMessageDuService() {
        ErreurResponse metier = verifierBase(handler.gererConflitMetier(
                new ConflitMetierException("Cet email est déjà utilisé."), requete), 409, "CONFLICT");
        assertThat(metier.message()).isEqualTo("Cet email est déjà utilisé.");
    }

    // ---------------------------------------------------------------- verrou optimiste : 409 (tests 1 à 3)

    @Test
    void optimisticLockingFailureException_renvoie409() {
        verifierConflitOptimiste(new ObjectOptimisticLockingFailureException("com.gestionsuivis.backend.entity.Projet",
                "8f1c0000-0000-0000-0000-000000000000"));
        verifierConflitOptimiste(new OptimisticLockingFailureException("update projects set version=? where id=?"));
    }

    @Test
    void optimisticLockExceptionJpa_renvoie409() {
        verifierConflitOptimiste(new jakarta.persistence.OptimisticLockException("update projects set version=?"));
    }

    @Test
    void staleStateExceptionHibernate_renvoie409() {
        verifierConflitOptimiste(new org.hibernate.StaleObjectStateException("com.gestionsuivis.backend.entity.Projet",
                "8f1c0000-0000-0000-0000-000000000000"));
        verifierConflitOptimiste(new org.hibernate.StaleStateException("update projects set version=?"));
    }

    // ---------------------------------------------------------------- verrou pessimiste : 503 (tests 4 à 7)

    @Test
    void pessimisticLockingFailureException_renvoie503EtRetryAfter2() {
        verifierVerrouIndisponible(new PessimisticLockingFailureException("select ... for update"));
        // Exceptions brutes JPA et Hibernate (EntityManager utilisé hors @Repository : non traduites).
        verifierVerrouIndisponible(new jakarta.persistence.PessimisticLockException("select ... for update"));
        verifierVerrouIndisponible(new jakarta.persistence.LockTimeoutException("select ... for update"));
        verifierVerrouIndisponible(new org.hibernate.PessimisticLockException("select ... for update",
                new SQLException("lock", "55P03"), "select * from projects for update"));
    }

    @Test
    void cannotAcquireLockException_renvoie503() {
        verifierVerrouIndisponible(new CannotAcquireLockException("could not obtain lock on row in relation projects"));
        verifierVerrouIndisponible(new org.hibernate.exception.LockAcquisitionException("deadlock",
                new SQLException("deadlock detected", "40P01"), "update projects set name=?"));
    }

    @Test
    void etatSql55P03_renvoie503() {
        verifierVerrouIndisponible(new IllegalStateException("enveloppe",
                new SQLException("canceling statement due to lock timeout", "55P03")));
        // Une 55P03 classée par Spring comme violation d'intégrité reste un 503.
        verifierVerrouIndisponible(new DataIntegrityViolationException("enveloppe",
                new SQLException("could not obtain lock on row in relation projects", "55P03")));
    }

    @Test
    void etatSql40P01_renvoie503() {
        verifierVerrouIndisponible(new IllegalStateException("enveloppe",
                new SQLException("deadlock detected", "40P01")));
        // État porté par une exception JDBC « suivante » (getNextException).
        SQLException premiere = new SQLException("autre erreur SQL", "99999");
        premiere.setNextException(new SQLException("deadlock detected", "40P01"));
        verifierVerrouIndisponible(new IllegalStateException("enveloppe", premiere));
    }

    // ---------------------------------------------------------------- violations d'intégrité (tests 8 à 14)

    /** Ancien {@code conflits_renvoient409SansMessageSql}, partie SQL : une contrainte reconnue → 409 précis. */
    @Test
    void etatSql23505Reconnu_renvoie409AvecMessagePrecis() {
        ErreurResponse email = verifierIntegrite(new DataIntegrityViolationException("enveloppe", new SQLException(
                "duplicate key value violates unique constraint \"users_org_email_unique\" "
                        + "Detail: Key (organization_id, email)=(0f0e, jean@exemple.com) already exists.", "23505")),
                409, "CONFLICT");
        assertThat(email.message()).isEqualTo(MessagesErreur.CONFLIT_EMAIL);

        ErreurResponse slug = verifierIntegrite(new DataIntegrityViolationException("enveloppe", new SQLException(
                "duplicate key value violates unique constraint \"organizations_slug_key\" Key (slug)=(secret)",
                "23505")), 409, "CONFLICT");
        assertThat(slug.message()).isEqualTo(MessagesErreur.CONFLIT_SLUG_ORGANISATION);

        // Serveur PostgreSQL en français (lc_messages = fr_FR).
        ErreurResponse francais = verifierIntegrite(new DataIntegrityViolationException("enveloppe", new SQLException(
                "la valeur d'une clé dupliquée rompt la contrainte unique « users_org_email_unique »", "23505")),
                409, "CONFLICT");
        assertThat(francais.message()).isEqualTo(MessagesErreur.CONFLIT_EMAIL);

        // Nom extrait par Hibernate (ConstraintViolationException.getConstraintName()).
        ErreurResponse hibernate = verifierIntegrite(new DataIntegrityViolationException("enveloppe",
                new org.hibernate.exception.ConstraintViolationException("insert into users ...",
                        new SQLException("violation", "23505"), "insert into users ...", "users_org_email_unique")),
                409, "CONFLICT");
        assertThat(hibernate.message()).isEqualTo(MessagesErreur.CONFLIT_EMAIL);

        // Nom fourni par le pilote PostgreSQL (ServerErrorMessage.getConstraint()), message muet.
        ErreurResponse pilote = verifierIntegrite(new DataIntegrityViolationException("enveloppe",
                new FausseExceptionPilote("erreur", "23505", "organizations_slug_key")), 409, "CONFLICT");
        assertThat(pilote.message()).isEqualTo(MessagesErreur.CONFLIT_SLUG_ORGANISATION);
    }

    @Test
    void etatSql23505Inconnu_renvoie409Generique() {
        ErreurResponse inconnue = verifierIntegrite(new DataIntegrityViolationException("enveloppe", new SQLException(
                "duplicate key value violates unique constraint \"users_email_key\" Key (email)=(secret)", "23505")),
                409, "CONFLICT");
        assertThat(inconnue.message()).isEqualTo(MessagesErreur.CONFLIT_DONNEES);

        // Contrainte réelle mais sans message métier (clé composite technique).
        ErreurResponse technique = verifierIntegrite(new DataIntegrityViolationException("enveloppe", new SQLException(
                "duplicate key value violates unique constraint \"projects_org_id_id_unique\"", "23505")),
                409, "CONFLICT");
        assertThat(technique.message()).isEqualTo(MessagesErreur.CONFLIT_DONNEES);

        // Nom reconnu présent seulement dans une erreur SQL d'un autre état : non retenu.
        SQLException mixte = new SQLException("duplicate key", "23505");
        mixte.setNextException(new SQLException(
                "duplicate key value violates unique constraint \"organizations_slug_key\"", "99999"));
        ErreurResponse nonAssocie = verifierIntegrite(new DataIntegrityViolationException("enveloppe", mixte),
                409, "CONFLICT");
        assertThat(nonAssocie.message()).isEqualTo(MessagesErreur.CONFLIT_DONNEES);

        // Nom Hibernate absent : pas d'erreur, message générique.
        ErreurResponse sansNom = verifierIntegrite(new DataIntegrityViolationException("enveloppe",
                new org.hibernate.exception.ConstraintViolationException("insert",
                        new SQLException("violation", "23505"), "insert", null)), 409, "CONFLICT");
        assertThat(sansNom.message()).isEqualTo(MessagesErreur.CONFLIT_DONNEES);
    }

    @Test
    void etatSql23503_renvoie409Generique() {
        ErreurResponse corps = verifierIntegrite(new DataIntegrityViolationException("enveloppe", new SQLException(
                "insert or update on table \"activities\" violates foreign key constraint \"activities_project_fk\" "
                        + "Detail: Key (project_id)=(secret) is not present", "23503")), 409, "CONFLICT");
        assertThat(corps.message()).isEqualTo(MessagesErreur.CONFLIT_DONNEES);
    }

    @Test
    void etatSql23514_renvoie500Generique() {
        verifierErreurInterne("23514", "new row for relation \"projects\" violates check constraint \"projects_progress_range\"");
    }

    @Test
    void etatSql23502_renvoie500Generique() {
        verifierErreurInterne("23502", "null value in column \"name\" of relation \"projects\" violates not-null constraint");
    }

    @Test
    void etatSql22xxx_renvoie500Generique() {
        verifierErreurInterne("22001", "value too long for type character varying(255) : secret");
        verifierErreurInterne("22P02", "invalid input syntax for type uuid: \"secret\"");
        verifierErreurInterne("99999", "état non prévu : secret");
    }

    @Test
    void etatSqlAbsent_renvoie500Generique() {
        // Ni cause SQL : seul le message de Spring, qui contient le SQL.
        ErreurResponse sansCause = verifierIntegrite(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"users_org_email_unique\" (email)=(secret)"),
                500, "SERVER_ERROR");
        assertThat(sansCause.message()).isEqualTo(MessagesErreur.ERREUR_INTERNE);

        // Cause SQL sans SQLState.
        ErreurResponse sansEtat = verifierIntegrite(new DataIntegrityViolationException("enveloppe",
                new SQLException("détail interne secret")), 500, "SERVER_ERROR");
        assertThat(sansEtat.message()).isEqualTo(MessagesErreur.ERREUR_INTERNE);
    }

    // ---------------------------------------------------------------- causes imbriquées (test 15)

    @Test
    void exceptionEnveloppeeDansPlusieursCauses_classeeCorrectement() {
        // 23505 reconnue sous trois niveaux, passée par le gestionnaire générique.
        Exception unicite = new IllegalStateException("niveau 1", new RuntimeException("niveau 2",
                new DataIntegrityViolationException("niveau 3", new RuntimeException("niveau 4", new SQLException(
                        "duplicate key value violates unique constraint \"users_org_email_unique\"", "23505")))));
        ErreurResponse corps = verifierBase(handler.gererErreurInattendue(unicite, requete), 409, "CONFLICT");
        assertThat(corps.message()).isEqualTo(MessagesErreur.CONFLIT_EMAIL);
        assertThat(serialiser(corps)).doesNotContain("users_org_email_unique");

        // 23503 sous une cause intermédiaire.
        ErreurResponse cleEtrangere = verifierIntegrite(new DataIntegrityViolationException("enveloppe",
                new IllegalStateException("intermédiaire", new SQLException("détail secret", "23503"))),
                409, "CONFLICT");
        assertThat(cleEtrangere.message()).isEqualTo(MessagesErreur.CONFLIT_DONNEES);

        // Verrou optimiste enveloppé (ex. dans une exception de transaction).
        verifierConflitOptimiste(new IllegalStateException("commit impossible", new RuntimeException("rollback",
                new org.hibernate.StaleStateException("update projects set version=?"))));

        // Verrou pessimiste et violation d'intégrité dans la même chaîne : le 503 l'emporte.
        verifierVerrouIndisponible(new DataIntegrityViolationException("enveloppe",
                new RuntimeException("intermédiaire", new CannotAcquireLockException("lock"))));

        // Chaîne cyclique : pas de boucle infinie, 500 générique.
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        verifierBase(handler.gererErreurInattendue(a, requete), 500, "SERVER_ERROR");
    }

    // ---------------------------------------------------------------- utilitaires 409 / 503

    private void verifierConflitOptimiste(Exception conflit) {
        ResponseEntity<ErreurResponse> reponse = handler.gererErreurInattendue(conflit, requete);
        ErreurResponse corps = verifierBase(reponse, 409, "CONFLICT");
        assertThat(corps.message()).isEqualTo(MessagesErreur.RESSOURCE_MODIFIEE)
                .isEqualTo("Cette ressource a été modifiée par quelqu'un d'autre. Rechargez-la avant de réessayer.");
        assertThat(reponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
        verifierSansDetailInterne(corps);
    }

    private void verifierVerrouIndisponible(Exception verrou) {
        ResponseEntity<ErreurResponse> reponse = verrou instanceof DataIntegrityViolationException violation
                ? handler.gererIntegriteDonnees(violation, requete)
                : handler.gererErreurInattendue(verrou, requete);
        ErreurResponse corps = verifierBase(reponse, 503, "SERVER_ERROR");
        assertThat(reponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        assertThat(corps.message()).isEqualTo(MessagesErreur.SERVICE_OCCUPE);
        assertThat(corps.errors()).isEmpty();
        verifierSansDetailInterne(corps);
    }

    private ErreurResponse verifierIntegrite(DataIntegrityViolationException violation, int statut, String code) {
        ResponseEntity<ErreurResponse> reponse = handler.gererIntegriteDonnees(violation, requete);
        ErreurResponse corps = verifierBase(reponse, statut, code);
        assertThat(reponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
        verifierSansDetailInterne(corps);
        return corps;
    }

    private void verifierErreurInterne(String etat, String messageSql) {
        ErreurResponse corps = verifierIntegrite(new DataIntegrityViolationException("enveloppe",
                new SQLException(messageSql, etat)), 500, "SERVER_ERROR");
        assertThat(corps.message()).isEqualTo(MessagesErreur.ERREUR_INTERNE);
        assertThat(sansHorodatage(corps)).doesNotContain(etat);
    }

    /** Ni SQL, ni nom de contrainte, ni valeur saisie, ni classe Java, ni SQLState dans la réponse. */
    private static void verifierSansDetailInterne(ErreurResponse corps) {
        assertThat(sansHorodatage(corps)).doesNotContainIgnoringCase("select").doesNotContainIgnoringCase("update ")
                .doesNotContainIgnoringCase("insert").doesNotContainIgnoringCase("constraint")
                .doesNotContain("users_org_email_unique").doesNotContain("organizations_slug_key")
                .doesNotContain("users_email_key").doesNotContain("projects_org_id_id_unique")
                .doesNotContain("jean@exemple.com").doesNotContain("secret").doesNotContain("Exception")
                .doesNotContain("org.").doesNotContain("com.gestionsuivis").doesNotContain("deadlock")
                .doesNotContain("55P03").doesNotContain("40P01").doesNotContain("23505").doesNotContain("23503");
    }

    /** JSON de la réponse sans l'horodatage, dont les chiffres pourraient former un SQLState. */
    private static String sansHorodatage(ErreurResponse corps) {
        return serialiser(corps).replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }

    /** Simule {@code PSQLException} : nom de contrainte disponible via {@code getServerErrorMessage()}. */
    public static final class FausseExceptionPilote extends SQLException {
        private final transient MessageServeur detail;

        FausseExceptionPilote(String message, String etat, String contrainte) {
            super(message, etat);
            this.detail = new MessageServeur(contrainte);
        }

        public MessageServeur getServerErrorMessage() {
            return detail;
        }
    }

    /** Simule {@code org.postgresql.util.ServerErrorMessage}. */
    public static final class MessageServeur {
        private final String contrainte;

        MessageServeur(String contrainte) {
            this.contrainte = contrainte;
        }

        public String getConstraint() {
            return contrainte;
        }
    }

    // ---------------------------------------------------------------- IA / 500

    @Test
    void serviceIa_utiliseLeCodeSuggereEtRetryAfter() {
        ResponseEntity<ErreurResponse> surcharge = handler.gererServiceIa(new ServiceIaException(
                TypeErreurServiceIa.SURCHARGE, 429, null, Duration.ofSeconds(60), null), requete);
        verifierBase(surcharge, 429, "RATE_LIMITED");
        assertThat(surcharge.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");

        ErreurResponse indisponible = verifierBase(handler.gererServiceIa(
                new ServiceIaException(TypeErreurServiceIa.INDISPONIBLE), requete), 503, "SERVER_ERROR");
        assertThat(indisponible.message()).isEqualTo(TypeErreurServiceIa.INDISPONIBLE.messageUtilisateur());
    }

    @Test
    void erreurInattendue_renvoie500SansDetailInterne() {
        ResponseEntity<ErreurResponse> reponse = handler.gererErreurInattendue(
                new IllegalStateException("Connexion refusée : password=Admin@2026"), requete);

        ErreurResponse corps = verifierBase(reponse, 500, "SERVER_ERROR");
        assertThat(serialiser(corps)).doesNotContain("Admin@2026").doesNotContain("IllegalStateException");
    }

    @Test
    void exceptionSpringAvecCode4xx_conserveSonCode() {
        verifierBase(handler.gererErreurInattendue(
                new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "détail interne"), requete), 406, "NOT_ACCEPTABLE");
    }

    // ---------------------------------------------------------------- format JSON (Jackson 3)

    @Test
    void serialisationJson_respecteLeContratFrontend() {
        String json = serialiser(ErreurResponse.de(HttpStatus.NOT_FOUND, "Introuvable.", CHEMIN));

        assertThat(json).contains("\"timestamp\":\"").contains("\"status\":404").contains("\"code\":\"NOT_FOUND\"")
                .contains("\"message\":\"Introuvable.\"").contains("\"path\":\"/api/test\"").doesNotContain("\"errors\"");

        String avecErreurs = serialiser(ErreurResponse.de(HttpStatus.UNPROCESSABLE_CONTENT, "Invalide.", CHEMIN,
                List.of(new ErreurChamp("email", "L'email est obligatoire."))));
        assertThat(avecErreurs).contains("\"errors\":[{\"field\":\"email\",\"message\":\"L'email est obligatoire.\"}]");
    }

    // ---------------------------------------------------------------- utilitaires

    private static ErreurResponse verifierBase(ResponseEntity<ErreurResponse> reponse, int statut, String code) {
        assertThat(reponse.getStatusCode().value()).isEqualTo(statut);
        ErreurResponse corps = reponse.getBody();
        assertThat(corps).isNotNull();
        assertThat(corps.status()).isEqualTo(statut);
        assertThat(corps.code()).isEqualTo(code);
        assertThat(corps.path()).isEqualTo(CHEMIN);
        assertThat(corps.timestamp()).isNotNull();
        return corps;
    }

    private static String serialiser(ErreurResponse corps) {
        return JSON.writeValueAsString(corps);
    }

    @SuppressWarnings("unused")
    private void methodeFictive(String valeur) {
    }

    private static final class ClasseFictive {
    }

    private static final class ObjetFictif {
        @NotBlank(message = "Le nom est obligatoire.")
        private String name;
    }
}
