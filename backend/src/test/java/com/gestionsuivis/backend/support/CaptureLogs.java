package com.gestionsuivis.backend.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Capture en mémoire les logs d'une classe pendant un test, au niveau TRACE,
 * afin de vérifier qu'aucune donnée sensible n'y apparaît.
 */
public final class CaptureLogs implements AutoCloseable {

    private final Logger logger;
    private final Level niveauInitial;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CaptureLogs(Class<?> classe) {
        this.logger = (Logger) LoggerFactory.getLogger(classe);
        this.niveauInitial = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
    }

    public static CaptureLogs de(Class<?> classe) {
        return new CaptureLogs(classe);
    }

    /** Messages formatés, avec les éventuels messages d'exception attachés. */
    public List<String> messages() {
        return appender.list.stream()
                .map(evenement -> evenement.getFormattedMessage()
                        + (evenement.getThrowableProxy() == null ? "" : " " + evenement.getThrowableProxy().getMessage()))
                .toList();
    }

    public String tout() {
        return String.join("\n", messages());
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(niveauInitial);
        appender.stop();
    }
}
