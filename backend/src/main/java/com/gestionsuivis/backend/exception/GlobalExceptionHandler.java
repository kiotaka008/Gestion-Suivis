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

import java.util.ArrayList;
import java.util.List;

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
     * Violation d'une contrainte PostgreSQL (unicité, clé étrangère, NOT NULL).
     * Le message SQL d'origine n'est jamais renvoyé au client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErreurResponse> gererIntegriteDonnees(DataIntegrityViolationException ex,
                                                                HttpServletRequest requete) {
        log.warn("Violation d'intégrité des données sur {} : {}", requete.getRequestURI(),
                ex.getMostSpecificCause().getClass().getSimpleName());
        return repondre(HttpStatus.CONFLICT, MessagesErreur.CONFLIT_DONNEES, requete);
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
