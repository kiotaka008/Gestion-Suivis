package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.exception.ErreurResponse;
import com.gestionsuivis.backend.exception.MessagesErreur;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Réponses 401 et 403 produites par les filtres Spring Security, au même format
 * JSON que le gestionnaire central ({@link ErreurResponse}).
 */
public class ErreursSecuriteHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;

    public ErreursSecuriteHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest requete, HttpServletResponse reponse,
                         AuthenticationException exception) throws IOException {
        ecrire(reponse, ErreurResponse.de(HttpStatus.UNAUTHORIZED, MessagesErreur.NON_AUTHENTIFIE,
                requete.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest requete, HttpServletResponse reponse,
                       AccessDeniedException exception) throws IOException {
        String message = exception instanceof CsrfException
                ? MessagesErreur.JETON_CSRF_INVALIDE
                : MessagesErreur.ACCES_INTERDIT;
        ecrire(reponse, ErreurResponse.de(HttpStatus.FORBIDDEN, message, requete.getRequestURI()));
    }

    private void ecrire(HttpServletResponse reponse, ErreurResponse corps) throws IOException {
        if (reponse.isCommitted()) {
            return;
        }
        reponse.setStatus(corps.status());
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        reponse.getOutputStream().write(json.writeValueAsBytes(corps));
    }
}
