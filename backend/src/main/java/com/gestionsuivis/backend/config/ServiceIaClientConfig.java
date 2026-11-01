package com.gestionsuivis.backend.config;

import com.gestionsuivis.backend.service.ia.ServiceIaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Création du client HTTP vers le service IA (FastAPI).
 * <p>
 * Deux {@link RestClient} partagent le même {@link HttpClient} du JDK (délai de
 * connexion commun) mais ont chacun leur délai de lecture : un pour le chat, un
 * plus court pour le contrôle de santé.
 * <p>
 * Le module Spring Boot de configuration automatique de RestClient n'étant pas
 * présent dans le projet, le client est construit ici sans dépendance supplémentaire.
 */
@Slf4j
@Configuration
public class ServiceIaClientConfig {

    @Bean
    public ServiceIaProperties serviceIaProperties(Environment environnement) {
        ServiceIaProperties proprietes = ServiceIaProperties.depuis(environnement);
        if (proprietes.estConfigure()) {
            log.info("Service IA configuré : {}", proprietes);
        } else {
            log.warn("Service IA non configuré ({} et {} requis) : les appels IA seront signalés comme indisponibles.",
                    ServiceIaProperties.VAR_URL, ServiceIaProperties.VAR_CLE);
        }
        return proprietes;
    }

    @Bean
    public ServiceIaClient serviceIaClient(ServiceIaProperties proprietes) {
        return creerClient(proprietes);
    }

    /** Construit le client complet (utilisé par le bean et par les tests réseau). */
    public static ServiceIaClient creerClient(ServiceIaProperties proprietes) {
        HttpClient clientHttp = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(proprietes.delaiConnexion())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        RestClient clientChat = preparerBuilder(proprietes)
                .requestFactory(fabrique(clientHttp, proprietes.delaiLectureChat()))
                .build();
        RestClient clientSante = preparerBuilder(proprietes)
                .requestFactory(fabrique(clientHttp, proprietes.delaiSante()))
                .build();
        return new ServiceIaClient(proprietes, clientChat, clientSante);
    }

    /**
     * Builder commun : adresse de base, en-tête {@code Accept} et clé interne.
     * La clé n'est ajoutée que si elle est configurée.
     */
    public static RestClient.Builder preparerBuilder(ServiceIaProperties proprietes) {
        RestClient.Builder builder = RestClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (proprietes.urlService() != null) {
            builder.baseUrl(proprietes.urlService());
        }
        if (proprietes.cleInterne() != null) {
            builder.defaultHeader(ServiceIaClient.ENTETE_CLE_INTERNE, proprietes.cleInterne());
        }
        return builder;
    }

    private static JdkClientHttpRequestFactory fabrique(HttpClient clientHttp, Duration delaiLecture) {
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(clientHttp);
        fabrique.setReadTimeout(delaiLecture);
        return fabrique;
    }
}
