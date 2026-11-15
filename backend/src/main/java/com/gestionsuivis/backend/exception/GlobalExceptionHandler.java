package com.gestionsuivis.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gestion centralisée des erreurs levées par les contrôleurs et les services.
 * <p>
 * Toutes les réponses d'erreur utilisent le format {@link ErreurResponse}
 * ({@code timestamp, status, code, message, path, errors}), avec un message en
 * français qui ne révèle jamais de détail interne (SQL, classes Java, pile
 * d'appels, valeurs saisies). Les détails techniques sont uniquement écrits dans
 * les logs du serveur.
 * <p>
 * Règle 400 / 422 : 400 pour une requête impossible à lire (JSON mal formé,
 * paramètre de mauvais type, tri inconnu) ; 422 pour des données lisibles mais
 * invalides (contraintes de validation, incohérences métier).
 * <p>
 * Les 401 / 403 produits par les filtres Spring Security sont traités par
 * {@code security.ErreursSecuriteHandler}, avec le même format.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** SQLState PostgreSQL : violation d'unicité. */
    static final String ETAT_UNICITE = "23505";
    /** SQLState PostgreSQL : violation de clé étrangère. */
    static final String ETAT_CLE_ETRANGERE = "23503";
    /** SQLState PostgreSQL : verrou indisponible ({@code lock_timeout}, {@code NOWAIT}). */
    static final String ETAT_VERROU_INDISPONIBLE = "55P03";
    /** SQLState PostgreSQL : interblocage détecté. */
    static final String ETAT_INTERBLOCAGE = "40P01";
    /** Délai conseillé (secondes) avant un nouvel essai après un verrou indisponible. */
    static final String RETRY_AFTER_VERROU = "2";

    /**
     * Contraintes d'unicité reconnues et message métier associé. Noms tirés de
     * {@code ProjectManager_SaaS_Database/database/migrations/20260930_001_projectmanager_initial_schema.sql} :
     * <ul>
     *   <li>{@code users_org_email_unique} : nommée explicitement, {@code UNIQUE (organization_id, email)} ;</li>
     *   <li>{@code organizations_slug_key} : {@code slug ... UNIQUE} déclaré sur la colonne, nommé par
     *       PostgreSQL selon sa règle par défaut {@code <table>_<colonne>_key}.</li>
     * </ul>
     * Les contraintes {@code *_org_id_id_unique} (clés composites servant aux clés étrangères) ne
     * correspondent à aucune saisie de l'utilisateur : elles gardent le message 409 générique.
     */
    static final Map<String, String> MESSAGES_CONTRAINTES_UNIQUES = Map.of(
            "users_org_email_unique", MessagesErreur.CONFLIT_EMAIL,
            "organizations_slug_key", MessagesErreur.CONFLIT_SLUG_ORGANISATION);

    /** Échec d'acquisition d'un verrou pessimiste (Spring, JPA, Hibernate brut) : 503. */
    private static final List<Class<? extends Throwable>> EXCEPTIONS_VERROU_PESSIMISTE = List.of(
            org.springframework.dao.PessimisticLockingFailureException.class, // dont CannotAcquireLockException
            jakarta.persistence.PessimisticLockException.class,
            jakarta.persistence.LockTimeoutException.class,
            org.hibernate.PessimisticLockException.class); // dont LockAcquisitionException, LockTimeoutException

    /** Conflit de verrou optimiste (version périmée) : 409. */
    private static final List<Class<? extends Throwable>> EXCEPTIONS_VERROU_OPTIMISTE = List.of(
            org.springframework.dao.OptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class,
            org.hibernate.StaleStateException.class);

    // ---------------------------------------------------------------- 422

    /** Corps JSON annoté avec {@code @Valid} dont un ou plusieurs champs sont invalides. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurResponse> gererCorpsInvalide(MethodArgumentNotValidException ex,
                                                             HttpServletRequest requete) {
        List<ErreurChamp> erreurs = new ArrayList<>();
        for (FieldError erreur : ex.getBindingResult().getFieldErrors()) {
            erreurs.add(new ErreurChamp(erreur.getField(), messageOuDefaut(erreur.getDefaultMessage())));
        }
        ex.getBindingResult().getGlobalErrors().forEach(erreur ->
                erreurs.add(new ErreurChamp(erreur.getObjectName(), messageOuDefaut(erreur.getDefaultMessage()))));
        return repondre(HttpStatus.UNPROCESSABLE_CONTENT, MessagesErreur.DONNEES_INVALIDES, requete, erreurs);
    }

    /** Paramètres de méthode de contrôleur invalides ({@code @RequestParam @Min(1)}, etc.). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErreurResponse> gererParametresInvalides(HandlerMethodValidationException ex,
                                                                   HttpServletRequest requete) {
        List<ErreurChamp> erreurs = new ArrayList<>();
        ex.getParameterValidationResults().forEach(resultat -> {
            String nom = resultat.getMethodParameter().getParameterName();
            resultat.getResolvableErrors().forEach(erreur ->
                    erreurs.add(new ErreurChamp(nom, messageOuDefaut(erreur.getDefaultMessage()))));
        });
        return repondre(HttpStatus.UNPROCESSABLE_CONTENT, MessagesErreur.DONNEES_INVALIDES, requete, erreurs);
    }

    /** Validation déclenchée hors contrôleur (service annoté {@code @Validated}). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErreurResponse> gererContraintesInvalides(ConstraintViolationException ex,
                                                                    HttpServletRequest requete) {
        List<ErreurChamp> erreurs = new ArrayList<>();
        if (ex.getConstraintViolations() != null) {
            for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
                erreurs.add(new ErreurChamp(dernierNoeud(violation.getPropertyPath().toString()),
                        messageOuDefaut(violation.getMessage())));
            }
        }
        return repondre(HttpStatus.UNPROCESSABLE_CONTENT, MessagesErreur.DONNEES_INVALIDES, requete, erreurs);
    }

    /** Incohérence métier détectée par un service. */
    @ExceptionHandler(RequeteInvalideException.class)
    public ResponseEntity<ErreurResponse> gererRequeteInvalide(RequeteInvalideException ex,
                                                               HttpServletRequest requete) {
        List<ErreurChamp> erreurs = ex.getChamp() == null
                ? List.of()
                : List.of(new ErreurChamp(ex.getChamp(), ex.getMessage()));
        return repondre(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), requete, erreurs);
    }

    // ---------------------------------------------------------------- 400

    /**
     * JSON absent ou mal formé (400). Une valeur d'un mauvais type ou une valeur
     * d'énumération inconnue (ex. statut {@code archived}) donne une erreur 422 sur le champ.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurResponse> gererCorpsIllisible(HttpMessageNotReadableException ex,
                                                              HttpServletRequest requete) {
        DatabindException mauvaisType = trouverCause(ex, DatabindException.class);
        if (mauvaisType != null) {
            String champ = cheminJson(mauvaisType);
            if (champ != null) {
                IllegalArgumentException valeurRefusee = trouverCause(ex, IllegalArgumentException.class);
                String message = valeurRefusee != null && valeurRefusee.getMessage() != null
                        && valeurRefusee.getMessage().startsWith("Valeur '")
                        ? valeurRefusee.getMessage() : MessagesErreur.VALEUR_INVALIDE;
                return repondre(HttpStatus.UNPROCESSABLE_CONTENT, MessagesErreur.DONNEES_INVALIDES, requete,
                        List.of(new ErreurChamp(champ, message)));
            }
        }
        log.debug("Corps de requête illisible sur {} : {}", requete.getRequestURI(), ex.getClass().getSimpleName());
        return repondre(HttpStatus.BAD_REQUEST, MessagesErreur.CORPS_ILLISIBLE, requete);
    }

    /** Paramètre d'URL d'un mauvais type (ex. {@code /api/projects/abc} au lieu d'un UUID). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurResponse> gererTypeParametre(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest requete) {
        String message = "Le paramètre '" + ex.getName() + "' a une valeur invalide.";
        return repondre(HttpStatus.BAD_REQUEST, message, requete);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErreurResponse> gererParametreManquant(MissingServletRequestParameterException ex,
                                                                 HttpServletRequest requete) {
        String message = "Le paramètre obligatoire '" + ex.getParameterName() + "' est manquant.";
        return repondre(HttpStatus.BAD_REQUEST, message, requete);
    }

    /** Critère de tri inconnu. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErreurResponse> gererTriInconnu(PropertyReferenceException ex,
                                                          HttpServletRequest requete) {
        String message = "Le critère de tri '" + ex.getPropertyName() + "' n'est pas reconnu.";
        return repondre(HttpStatus.BAD_REQUEST, message, requete);
    }

    @ExceptionHandler(ParametreInvalideException.class)
    public ResponseEntity<ErreurResponse> gererParametreInvalide(ParametreInvalideException ex,
                                                                 HttpServletRequest requete) {
        return repondre(HttpStatus.BAD_REQUEST, ex.getMessage(), requete);
    }

    // ---------------------------------------------------------------- 429

    /** Trop de tentatives de connexion échouées : 429 + Retry-After (secondes). */
    @ExceptionHandler(TropDeTentativesException.class)
    public ResponseEntity<ErreurResponse> gererTropDeTentatives(TropDeTentativesException ex,
                                                                HttpServletRequest requete) {
        HttpStatus statut = HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(statut)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.getDelaiAvantNouvelEssai().toSeconds())))
                .body(ErreurResponse.de(statut, ex.getMessage(), requete.getRequestURI()));
    }

    // ---------------------------------------------------------------- 401 / 403

    @ExceptionHandler(IdentifiantsInvalidesException.class)
    public ResponseEntity<ErreurResponse> gererIdentifiantsInvalides(IdentifiantsInvalidesException ex,
                                                                     HttpServletRequest requete) {
        return repondre(HttpStatus.UNAUTHORIZED, ex.getMessage(), requete);
    }

    /** Authentification refusée levée depuis un contrôleur ou un service. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErreurResponse> gererNonAuthentifie(AuthenticationException ex,
                                                              HttpServletRequest requete) {
        return repondre(HttpStatus.UNAUTHORIZED, MessagesErreur.NON_AUTHENTIFIE, requete);
    }

    /** Accès refusé par {@code @PreAuthorize} ou levé depuis un service. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurResponse> gererAccesRefuse(AccessDeniedException ex,
                                                           HttpServletRequest requete) {
        return repondre(HttpStatus.FORBIDDEN, MessagesErreur.ACCES_INTERDIT, requete);
    }

    @ExceptionHandler(AccesInterditException.class)
    public ResponseEntity<ErreurResponse> gererAccesInterdit(AccesInterditException ex,
                                                             HttpServletRequest requete) {
        return repondre(HttpStatus.FORBIDDEN, ex.getMessage(), requete);
    }

    // ---------------------------------------------------------------- 404

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<ErreurResponse> gererRessourceIntrouvable(RessourceIntrouvableException ex,
                                                                    HttpServletRequest requete) {
        return repondre(HttpStatus.NOT_FOUND, ex.getMessage(), requete);
    }

    /** URL ne correspondant à aucun endpoint. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErreurResponse> gererUrlInconnue(NoResourceFoundException ex,
                                                           HttpServletRequest requete) {
        return repondre(HttpStatus.NOT_FOUND, MessagesErreur.RESSOURCE_INEXISTANTE, requete);
    }

    // ---------------------------------------------------------------- 405 / 415

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErreurResponse> gererMethodeNonAutorisee(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest requete) {
        String message = "La méthode HTTP '" + ex.getMethod() + "' n'est pas autorisée pour cette URL.";
        HttpHeaders entetes = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            entetes.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(entetes)
                .body(ErreurResponse.de(HttpStatus.METHOD_NOT_ALLOWED, message, requete.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErreurResponse> gererTypeContenu(HttpMediaTypeNotSupportedException ex,
                                                           HttpServletRequest requete) {
        return repondre(HttpStatus.UNSUPPORTED_MEDIA_TYPE, MessagesErreur.TYPE_CONTENU_NON_SUPPORTE, requete);
    }

    // ---------------------------------------------------------------- 409

    @ExceptionHandler(ConflitMetierException.class)
    public ResponseEntity<ErreurResponse> gererConflitMetier(ConflitMetierException ex,
                                                             HttpServletRequest requete) {
        return repondre(HttpStatus.CONFLICT, ex.getMessage(), requete);
    }

    /**
     * Violation d'une contrainte PostgreSQL, classée selon le SQLState réel :
     * <ul>
     *   <li>{@code 23505} (unicité) : 409, avec un message précis si la contrainte est connue
     *       ({@link #MESSAGES_CONTRAINTES_UNIQUES}), sinon le message 409 générique ;</li>
     *   <li>{@code 23503} (clé étrangère) : 409 générique ;</li>
     *   <li>{@code 23514}, {@code 23502}, {@code 22xxx}, autre état ou état absent : 500 générique
     *       (donnée que l'application aurait dû refuser avant la base).</li>
     * </ul>
     * Le message SQL, le nom de la contrainte et les valeurs ne sont jamais renvoyés au client ;
     * le SQLState et le nom de la contrainte sont seulement journalisés.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErreurResponse> gererIntegriteDonnees(DataIntegrityViolationException ex,
                                                                HttpServletRequest requete) {
        HttpStatus statutVerrou = statutConflitVerrou(ex);
        if (statutVerrou != null) {
            return repondreConflitVerrou(statutVerrou, ex, requete);
        }

        if (contientEtatSql(ex, ETAT_UNICITE)) {
            String contrainte = contrainteUniqueReconnue(ex);
            log.warn("Violation d'unicité (SQLState {}) sur {} {} : contrainte {}", ETAT_UNICITE,
                    requete.getMethod(), requete.getRequestURI(), contrainte == null ? "non reconnue" : contrainte);
            String message = contrainte == null
                    ? MessagesErreur.CONFLIT_DONNEES
                    : MESSAGES_CONTRAINTES_UNIQUES.get(contrainte);
            return repondre(HttpStatus.CONFLICT, message, requete);
        }
        if (contientEtatSql(ex, ETAT_CLE_ETRANGERE)) {
            log.warn("Violation de clé étrangère (SQLState {}) sur {} {}", ETAT_CLE_ETRANGERE,
                    requete.getMethod(), requete.getRequestURI());
            return repondre(HttpStatus.CONFLICT, MessagesErreur.CONFLIT_DONNEES, requete);
        }

        log.error("Violation d'intégrité non prévue (SQLState {}) sur {} {}", premierEtatSql(ex),
                requete.getMethod(), requete.getRequestURI(), ex);
        return repondre(HttpStatus.INTERNAL_SERVER_ERROR, MessagesErreur.ERREUR_INTERNE, requete);
    }

    // ---------------------------------------------------------------- service IA

    /** Erreur du client FastAPI : code suggéré (422, 429, 502, 503) et message générique. */
    @ExceptionHandler(ServiceIaException.class)
    public ResponseEntity<ErreurResponse> gererServiceIa(ServiceIaException ex, HttpServletRequest requete) {
        HttpStatusCode statut = ex.getStatutHttpSuggere();
        ResponseEntity.BodyBuilder reponse = ResponseEntity.status(statut);
        ex.getDelaiAvantNouvelEssai().ifPresent(delai ->
                reponse.header(HttpHeaders.RETRY_AFTER, String.valueOf(delai.toSeconds())));
        return reponse.body(ErreurResponse.de(statut, ex.getMessage(), requete.getRequestURI()));
    }

    // ---------------------------------------------------------------- 500

    /**
     * Toute autre erreur. Les exceptions Spring qui portent déjà un code HTTP
     * (ex. 406, 413) conservent ce code ; les autres deviennent une erreur 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurResponse> gererErreurInattendue(Exception ex, HttpServletRequest requete) {
        HttpStatus statutVerrou = statutConflitVerrou(ex);
        if (statutVerrou != null) {
            return repondreConflitVerrou(statutVerrou, ex, requete);
        }
        DataIntegrityViolationException violation =
                trouverCauseImbriquee(ex, DataIntegrityViolationException.class);
        if (violation != null) {
            return gererIntegriteDonnees(violation, requete);
        }
        if (ex instanceof ErrorResponse reponseSpring && reponseSpring.getStatusCode().is4xxClientError()) {
            HttpStatusCode statut = reponseSpring.getStatusCode();
            return repondre(statut, MessagesErreur.generique(statut), requete);
        }
        HttpStatusCode statut = ex instanceof ErrorResponse reponseSpring
                ? reponseSpring.getStatusCode()
                : HttpStatus.INTERNAL_SERVER_ERROR;
        log.error("Erreur inattendue sur {} {}", requete.getMethod(), requete.getRequestURI(), ex);
        return repondre(statut, MessagesErreur.ERREUR_INTERNE, requete);
    }

    // ---------------------------------------------------------------- conflits de verrou

    /**
     * 409 (verrou optimiste : la ressource a changé depuis sa lecture) ou 503 + {@code Retry-After}
     * (verrou pessimiste indisponible : délai dépassé ou interblocage, un nouvel essai peut réussir).
     * Le type d'exception et le SQLState sont journalisés, jamais renvoyés au client.
     */
    private ResponseEntity<ErreurResponse> repondreConflitVerrou(HttpStatus statut, Throwable ex,
                                                                HttpServletRequest requete) {
        log.warn("Conflit de verrou ({}) sur {} {} : {} (SQLState {})", statut.value(), requete.getMethod(),
                requete.getRequestURI(), ex.getClass().getName(), premierEtatSql(ex));
        ResponseEntity.BodyBuilder reponse = ResponseEntity.status(statut);
        String message = MessagesErreur.RESSOURCE_MODIFIEE;
        if (statut == HttpStatus.SERVICE_UNAVAILABLE) {
            reponse.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_VERROU);
            message = MessagesErreur.SERVICE_OCCUPE;
        }
        return reponse.body(ErreurResponse.de(statut, message, requete.getRequestURI()));
    }

    /**
     * Statut d'un conflit de verrou trouvé n'importe où dans la chaîne des causes, ou {@code null}.
     * Le pessimiste est testé en premier : un échec d'acquisition de verrou n'est jamais un 409.
     */
    private static HttpStatus statutConflitVerrou(Throwable ex) {
        if (contientUneCause(ex, EXCEPTIONS_VERROU_PESSIMISTE)
                || contientEtatSql(ex, ETAT_VERROU_INDISPONIBLE, ETAT_INTERBLOCAGE)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (contientUneCause(ex, EXCEPTIONS_VERROU_OPTIMISTE)) {
            return HttpStatus.CONFLICT;
        }
        return null;
    }

    private static boolean contientUneCause(Throwable ex, List<Class<? extends Throwable>> types) {
        return exceptionsImbriquees(ex).stream().anyMatch(cause -> types.stream().anyMatch(t -> t.isInstance(cause)));
    }

    private static <T extends Throwable> T trouverCauseImbriquee(Throwable ex, Class<T> type) {
        return exceptionsImbriquees(ex).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }

    private static boolean contientEtatSql(Throwable ex, String... etats) {
        Set<String> etatsRecherches = Set.of(etats);
        return exceptionsImbriquees(ex).stream()
                .filter(SQLException.class::isInstance)
                .map(SQLException.class::cast)
                .anyMatch(sql -> sql.getSQLState() != null && etatsRecherches.contains(sql.getSQLState()));
    }

    /** Premier SQLState non vide de la chaîne (journalisation uniquement), ou {@code "absent"}. */
    private static String premierEtatSql(Throwable ex) {
        return exceptionsImbriquees(ex).stream()
                .filter(SQLException.class::isInstance)
                .map(cause -> ((SQLException) cause).getSQLState())
                .filter(etat -> etat != null && !etat.isBlank())
                .findFirst()
                .orElse("absent");
    }

    /**
     * Nom de la contrainte d'unicité reconnue ({@link #MESSAGES_CONTRAINTES_UNIQUES}) violée par
     * une erreur {@code 23505} de la chaîne, ou {@code null}. Le nom n'est retenu que s'il est porté
     * par une exception d'état {@code 23505} : un nom présent dans une autre erreur SQL est ignoré.
     * Sources, de la plus fiable à la moins fiable : nom extrait par Hibernate, champ
     * {@code constraint} du message serveur PostgreSQL (lu par réflexion, le pilote n'étant présent
     * qu'à l'exécution, et indépendant de la langue du serveur), texte du message.
     */
    private static String contrainteUniqueReconnue(Throwable ex) {
        for (Throwable cause : exceptionsImbriquees(ex)) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null // Map.of refuse containsKey(null)
                    && MESSAGES_CONTRAINTES_UNIQUES.containsKey(violation.getConstraintName())
                    && (ETAT_UNICITE.equals(violation.getSQLState()) || contientEtatSql(violation, ETAT_UNICITE))) {
                return violation.getConstraintName();
            }
            if (cause instanceof SQLException sql && ETAT_UNICITE.equals(sql.getSQLState())) {
                String duPilote = contrainteDuPilote(sql);
                if (duPilote != null && MESSAGES_CONTRAINTES_UNIQUES.containsKey(duPilote)) {
                    return duPilote;
                }
                String message = sql.getMessage();
                if (message != null) {
                    for (String nom : MESSAGES_CONTRAINTES_UNIQUES.keySet()) {
                        // Guillemets anglais (lc_messages = en) ou chevrons français (lc_messages = fr).
                        if (message.contains("\"" + nom + "\"") || message.contains("« " + nom + " »")) {
                            return nom;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** {@code PSQLException.getServerErrorMessage().getConstraint()}, sans dépendance de compilation. */
    private static String contrainteDuPilote(SQLException sql) {
        try {
            Object detail = sql.getClass().getMethod("getServerErrorMessage").invoke(sql);
            if (detail == null) {
                return null;
            }
            Object nom = detail.getClass().getMethod("getConstraint").invoke(detail);
            return nom instanceof String texte ? texte : null;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static List<Throwable> exceptionsImbriquees(Throwable racine) {
        List<Throwable> resultat = new ArrayList<>();
        if (racine == null) {
            return resultat;
        }

        ArrayDeque<Throwable> aVisiter = new ArrayDeque<>();
        Set<Throwable> visitees = Collections.newSetFromMap(new IdentityHashMap<>());
        aVisiter.add(racine);
        while (!aVisiter.isEmpty()) {
            Throwable courante = aVisiter.removeFirst();
            if (!visitees.add(courante)) {
                continue;
            }
            resultat.add(courante);
            if (courante.getCause() != null) {
                aVisiter.addLast(courante.getCause());
            }
            if (courante instanceof SQLException sql && sql.getNextException() != null) {
                aVisiter.addLast(sql.getNextException());
            }
        }
        return resultat;
    }

    // ---------------------------------------------------------------- utilitaires

    private ResponseEntity<ErreurResponse> repondre(HttpStatusCode statut, String message,
                                                    HttpServletRequest requete) {
        return repondre(statut, message, requete, List.of());
    }

    private ResponseEntity<ErreurResponse> repondre(HttpStatusCode statut, String message,
                                                    HttpServletRequest requete, List<ErreurChamp> erreurs) {
        return ResponseEntity.status(statut)
                .body(ErreurResponse.de(statut, message, requete.getRequestURI(), erreurs));
    }

    private static String messageOuDefaut(String message) {
        return message == null || message.isBlank() ? MessagesErreur.VALEUR_INVALIDE : message;
    }

    /** {@code creer.dto.email} devient {@code email}. */
    private static String dernierNoeud(String chemin) {
        int position = chemin.lastIndexOf('.');
        return position >= 0 ? chemin.substring(position + 1) : chemin;
    }

    /** Nom du dernier champ nommé du chemin JSON en erreur (ex. {@code status}). */
    private static String cheminJson(JacksonException ex) {
        String champ = null;
        for (JacksonException.Reference reference : ex.getPath()) {
            if (reference.getPropertyName() != null) {
                champ = reference.getPropertyName();
            }
        }
        return champ;
    }

    private static <T extends Throwable> T trouverCause(Throwable erreur, Class<T> type) {
        Throwable courante = erreur;
        int profondeur = 0;
        while (courante != null && profondeur++ < 10) {
            if (type.isInstance(courante)) {
                return type.cast(courante);
            }
            courante = courante.getCause();
        }
        return null;
    }
}
