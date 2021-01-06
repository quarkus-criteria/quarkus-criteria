package com.github.quarkus.criteria;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.sql.SQLException;
import java.util.logging.Logger;

@ApplicationScoped
public class AppLifeCycle {

    private static final Logger LOGGER = Logger.getLogger(AppLifeCycle.class.getName());

    void onStart(@Observes StartupEvent ev) throws SQLException {
        LOGGER.info("The application is starting...");

    }

    void onStop(@Observes ShutdownEvent ev) throws SQLException {
        LOGGER.info("The application is stopping...");

    }

}
