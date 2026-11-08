package com.gestionsuivis.backend;

import com.gestionsuivis.backend.config.InitialisationAdministrateur;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Vérifie que le contexte Spring complet (sécurité, services, contrôleurs, JPA) se construit.
 *
 * Le test ne dépend d'aucune base PostgreSQL : Hibernate reçoit le dialecte sans lire les
 * métadonnées JDBC et ne valide pas le schéma (le pool Hikari n'ouvre aucune connexion au
 * démarrage, et l'initialisation du compte administrateur, qui interroge la base, est simulée).
 * La conformité au schéma officiel se vérifie en lançant le backend sur une base
 * créée avec les migrations de ProjectManager_SaaS_Database (ddl-auto=validate).
 */
@SpringBootTest(properties = {
		"spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
		"spring.jpa.hibernate.ddl-auto=none",
		"JWT_SECRET=secret-de-test-pour-le-chargement-du-contexte-0123456789"
})
class BackendApplicationTests {

	@MockitoBean
	InitialisationAdministrateur initialisationAdministrateur;

	@Test
	void contextLoads() {
	}

}
