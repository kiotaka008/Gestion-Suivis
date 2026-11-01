package com.gestionsuivis.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les tâches planifiées (traitement quotidien des échéances).
 * Placé ici pour ne pas modifier {@code BackendApplication}.
 */
@Configuration
@EnableScheduling
public class PlanificationConfig {
}
