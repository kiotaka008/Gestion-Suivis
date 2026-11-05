package com.gestionsuivis.backend.config;

import com.gestionsuivis.backend.security.ContexteOrganisation;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Isolation des organisations (tenants) du schéma officiel, à deux niveaux :
 * <ol>
 *   <li><b>Hibernate</b> : chaque entité porte {@code @TenantId} sur {@code organization_id}.
 *       Les requêtes sont filtrées et les insertions renseignées automatiquement avec
 *       l'organisation courante ({@link ContexteOrganisation}) ;</li>
 *   <li><b>PostgreSQL (RLS)</b> : à chaque emprunt d'une connexion, la variable
 *       {@code app.current_organization_id} lue par les politiques RLS est positionnée
 *       sur la même organisation (vide sans contexte : aucune ligne visible).</li>
 * </ol>
 * Le RLS ne s'applique que si le rôle de connexion n'est ni superutilisateur, ni
 * propriétaire des tables, ni {@code BYPASSRLS} ; le filtrage Hibernate s'applique toujours.
 */
@Configuration(proxyBeanMethods = false)
public class MultiOrganisationConfig {

    static final String SQL_CONTEXTE = "select set_config('app.current_organization_id', ?, false)";

    /** Organisation courante pour Hibernate ; l'organisation « aucune » ne correspond à aucune ligne. */
    public static class ResolveurOrganisation implements CurrentTenantIdentifierResolver<UUID> {

        @Override
        public UUID resolveCurrentTenantIdentifier() {
            return ContexteOrganisation.couranteOuAucune();
        }

        @Override
        public boolean validateExistingCurrentSessions() {
            return false;
        }
    }

    @Bean
    public HibernatePropertiesCustomizer resolveurOrganisationHibernate() {
        ResolveurOrganisation resolveur = new ResolveurOrganisation();
        return proprietes -> proprietes.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolveur);
    }

    @Bean
    public static BeanPostProcessor contexteRlsDataSource() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nom) {
                if (bean instanceof DataSource source && !(bean instanceof DataSourceContexteRls)) {
                    return new DataSourceContexteRls(source);
                }
                return bean;
            }
        };
    }

    /** Positionne {@code app.current_organization_id} sur chaque connexion empruntée au pool. */
    static class DataSourceContexteRls extends DelegatingDataSource {

        DataSourceContexteRls(DataSource cible) {
            super(cible);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return positionner(super.getConnection());
        }

        @Override
        public Connection getConnection(String utilisateur, String motDePasse) throws SQLException {
            return positionner(super.getConnection(utilisateur, motDePasse));
        }

        private static Connection positionner(Connection connexion) throws SQLException {
            UUID organisation = ContexteOrganisation.couranteOuAucune();
            try (PreparedStatement requete = connexion.prepareStatement(SQL_CONTEXTE)) {
                requete.setString(1, ContexteOrganisation.AUCUNE.equals(organisation) ? "" : organisation.toString());
                requete.execute();
                return connexion;
            } catch (SQLException | RuntimeException e) {
                connexion.close();
                throw e;
            }
        }
    }
}
