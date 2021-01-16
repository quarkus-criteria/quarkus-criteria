package com.github.quarkus.criteria.runtime.criteria.example;

import com.github.quarkus.criteria.runtime.model.PersistenceEntity;

import javax.persistence.EntityManager;

/**
 * @param <T> the example entity type
 * @author rmpestano
 */
public class ExampleBuilder<T extends PersistenceEntity> {

    private EntityManager entityManager;

    public ExampleBuilder(EntityManager entityManager) {
        this.entityManager = entityManager;
        if (entityManager == null) {
            throw new IllegalArgumentException("Entity manager should be provided");
        }
    }

    /**
     * @param example the example entity which values will be used to create the query
     */
    public WithCriteriaDsl of(T example) {
        return new WithCriteriaDsl(example, entityManager);
    }

}
