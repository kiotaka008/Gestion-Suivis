package com.gestionsuivis.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ports d'écoute supplémentaires de l'API, en plus de {@code server.port} (3000 par défaut).
 * <p>
 * Le frontend contient deux réglages qui visent des ports différents :
 * {@code .env.example} appelle directement {@code http://localhost:3000/api}, tandis que le
 * proxy de {@code vite.config.ts} (utilisé quand {@code VITE_API_URL} n'est pas défini) envoie
 * {@code /api} vers {@code http://localhost:8080}. Le backend écoute donc aussi sur 8080, pour que
 * les deux configurations fonctionnent sans modifier le frontend.
 * <p>
 * Variable {@code SERVER_SECONDARY_PORTS} : liste de ports séparés par des virgules
 * (défaut {@code 8080}) ; une valeur vide désactive les ports supplémentaires.
 */
@Slf4j
@Configuration
public class PortsSecondairesConfig {

    static final String VARIABLE = "SERVER_SECONDARY_PORTS";
    static final String DEFAUT = "8080";

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> portsSecondaires(Environment environnement) {
        Integer principal = environnement.getProperty("server.port", Integer.class, 8080);
        List<Integer> ports = lire(environnement.getProperty(VARIABLE, DEFAUT), principal);
        return fabrique -> {
            for (Integer port : ports) {
                Connector connecteur = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                connecteur.setPort(port);
                fabrique.addAdditionalConnectors(connecteur);
            }
            if (!ports.isEmpty()) {
                log.info("API également disponible sur le(s) port(s) {} (proxy Vite du frontend)", ports);
            }
        };
    }

    /**
     * Ports valides et distincts du port principal, dans l'ordre indiqué.
     * Les valeurs non numériques ou hors de 1..65535 sont ignorées avec un avertissement.
     */
    static List<Integer> lire(String valeur, Integer portPrincipal) {
        Set<Integer> ports = new LinkedHashSet<>();
        if (valeur == null || valeur.isBlank()) {
            return List.of();
        }
        for (String morceau : valeur.split(",")) {
            String texte = morceau.trim();
            if (texte.isEmpty()) {
                continue;
            }
            try {
                int port = Integer.parseInt(texte);
                if (port < 1 || port > 65535) {
                    log.warn("{} : port {} ignoré (hors de 1..65535)", VARIABLE, port);
                } else if (portPrincipal == null || port != portPrincipal) {
                    ports.add(port);
                }
            } catch (NumberFormatException e) {
                log.warn("{} : valeur « {} » ignorée (pas un numéro de port)", VARIABLE, texte);
            }
        }
        return new ArrayList<>(ports);
    }
}
