package com.github.quarkus.criteria.infra;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Collections;
import java.util.Map;

public class PostgresResource implements QuarkusTestResourceLifecycleManager {

    static PostgreSQLContainer<?> db =
            new PostgreSQLContainer<>("postgres:13")
                    .withDatabaseName("cars")
                    .withUsername("car")
                    .withPassword("car");

    @Override
    public Map<String, String> start() {
        db.start();
        return Collections.singletonMap(
                "quarkus.datasource.jdbc.url", db.getJdbcUrl()
        );
    }

    @Override
    public void stop() {
        db.stop();
    }
}