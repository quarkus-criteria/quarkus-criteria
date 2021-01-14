package com.github.quarkus.criteria.runtime.criteria.example;

import com.github.quarkus.criteria.runtime.model.ComparisonOperation;
import com.github.quarkus.criteria.runtime.model.PersistenceEntity;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static com.github.quarkus.criteria.runtime.model.ComparisonOperation.IS_NULL;
import static com.github.quarkus.criteria.runtime.model.ComparisonOperation.NOT_NULL;

/**
 * @param <T> the example entity type
 * @author rmpestano
 */
public class ExampleBuilder<T extends PersistenceEntity> {
    static final Logger LOG = Logger.getLogger(ExampleBuilder.class.getName());
    static final List<ComparisonOperation> NULL_OPERATIONS = Arrays.asList(IS_NULL, NOT_NULL);

    EntityManager entityManager;

    public ExampleBuilder(EntityManager entityManager) {
        this.entityManager = entityManager;
        if (entityManager == null) {
            throw new IllegalArgumentException("Entity manager should be provided");
        }
    }

    /**
     * @param example the example entity which values will be used to create the query
     */
    public ExampleBuilderDsl of(T example) {
        return new ExampleBuilderDsl(example, this);
    }

}
