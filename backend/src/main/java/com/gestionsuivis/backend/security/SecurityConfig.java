package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.util.List;

/**
 * Configuration Spring Security.
 * <ul>
 *   <li>sans session serveur : JWT HS256 dans un cookie HttpOnly (ou en-tête Bearer) ;</li>
 *   <li>CSRF pour application monopage : cookie {@code XSRF-TOKEN} + en-tête
 *       {@code X-XSRF-TOKEN} (noms par défaut d'Axios), exigé pour toute requête
 *       modifiante authentifiée par cookie. Non exigé sur la connexion, l'inscription et
 *       les requêtes portant un en-tête {@code Authorization: Bearer} (Postman, curl) ;</li>
 *   <li>CORS avec cookies pour {@code http://localhost:5173} et {@code http://localhost:3000} ;</li>
 *   <li>401 / 403 au format JSON commun ;</li>
 *   <li>autorisations fines vérifiées dans les services ({@code ControleAcces}).</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecuriteProperties securiteProperties(Environment environnement) {
        SecuriteProperties proprietes = SecuriteProperties.depuis(environnement);
        log.info("Sécurité : {}", proprietes);
        return proprietes;
    }

    @Bean
    public Clock horloge() {
        // Fuseau du serveur : les échéances (LocalDate) sont évaluées à la date locale.
        return Clock.systemDefaultZone();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(SecuriteProperties proprietes) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(cle(proprietes)));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecuriteProperties proprietes) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withSecretKey(cle(proprietes))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(JwtService.EMETTEUR)));
        return decodeur;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecuriteProperties proprietes) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(proprietes.originesAutorisees());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                HttpHeaders.AUTHORIZATION, "X-XSRF-TOKEN", "X-Requested-With"));
        configuration.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UtilisateurRepository utilisateurs,
                                                   JwtDecoder jwtDecoder, JsonMapper jsonMapper) throws Exception {
        ErreursSecuriteHandler erreurs = new ErreursSecuriteHandler(jsonMapper);
        ConvertisseurJwtUtilisateur convertisseur = new ConvertisseurJwtUtilisateur(utilisateurs);

        // Le « resource server » ne lit que l'en-tête Authorization (DefaultBearerTokenResolver) :
        // il dispense alors de CSRF les seules requêtes Bearer. Le cookie est lu par CookieJwtFilter,
        // placé après le filtre CSRF, pour que le navigateur reste protégé.
        http
                .cors(cors -> { })
                // Sans session serveur, chaque requête authentifiée par cookie serait vue comme une
                // nouvelle connexion et renouvellerait le jeton CSRF : des requêtes parallèles du
                // frontend seraient alors refusées. Le jeton reste donc stable pendant la session.
                .csrf(csrf -> csrf.spa()
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/register")
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(new CookieJwtFilter(jwtDecoder, convertisseur), BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(autorisations -> autorisations
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout",
                                "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(serveur -> serveur
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertisseur))
                        .authenticationEntryPoint(erreurs)
                        .accessDeniedHandler(erreurs))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(erreurs)
                        .accessDeniedHandler(erreurs));
        return http.build();
    }

    private static SecretKey cle(SecuriteProperties proprietes) {
        return new SecretKeySpec(proprietes.secretJwt(), "HmacSHA256");
    }
}
