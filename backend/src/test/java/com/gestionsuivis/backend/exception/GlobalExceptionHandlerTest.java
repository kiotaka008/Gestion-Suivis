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
import org.springframework.dao.DataIntegrityViolationException;
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
            JSON.readValue("{\"status\":\"archived\"}", Corps.class);
        } catch (RuntimeException erreur) {
            ex = new HttpMessageNotReadableException("JSON parse error", erreur, new MockHttpInputMessage(new byte[0]));
        }
        assertThat(ex).isNotNull();

        ErreurResponse corps = verifierBase(handler.gererCorpsIllisible(ex, requete), 422, "VALIDATION_ERROR");

        assertThat(corps.errors()).hasSize(1);
        assertThat(corps.errors().getFirst().field()).isEqualTo("status");
        assertThat(corps.errors().getFirst().message()).contains("archived").contains("active").contains("cancelled");
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

    @Test
    void conflits_renvoient409SansMessageSql() {
        ErreurResponse metier = verifierBase(handler.gererConflitMetier(
                new ConflitMetierException("Cet email est déjà utilisé."), requete), 409, "CONFLICT");
        assertThat(metier.message()).isEqualTo("Cet email est déjà utilisé.");

        ErreurResponse sql = verifierBase(handler.gererIntegriteDonnees(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"users_email_key\" Key (email)=(jean@exemple.com)"),
                requete), 409, "CONFLICT");
        assertThat(serialiser(sql)).doesNotContain("duplicate").doesNotContain("jean@exemple.com");
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
