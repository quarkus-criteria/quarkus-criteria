package com.github.quarkus.criteria.runtime.criteria.example;

import com.github.quarkus.criteria.runtime.model.ComparisonOperation;
import com.github.quarkus.criteria.runtime.model.PersistenceEntity;
import org.apache.deltaspike.data.api.criteria.Criteria;

import javax.persistence.EntityManager;
import javax.persistence.metamodel.Attribute;

/**
 * This class is responsible for the <code>withCriteria</code> dsl method which should be enabled only in the first level of the dsl.
 *
 * @param <T> Example entity type
 */
public class WithCriteriaDsl<T extends PersistenceEntity> {
    private ExampleDsl<T> exampleDsl;

    public WithCriteriaDsl(T example, EntityManager entityManager) {
        exampleDsl = new ExampleDsl<>(example, entityManager);
    }

    /**
     * @return A criteria populated with restrictions based on example entity
     */
    public Criteria<T, ?> build() {
        return exampleDsl.build();
    }

    /**
     * Add example restrictions to existing criteria
     *
     * @param criteria a pre configured criteria
     */
    public ExampleDsl<T> withCriteria(Criteria<T, ?> criteria) {
        exampleDsl.criteria = criteria;
        return exampleDsl;
    }

    /**
     * A criteria will be created using the example entity, provided attributes and using <b>EQ</b> operation to compare the attribute's values.
     *
     * @param exampleAttributes attributes from example entity to consider. If no attribute is provided then <b>non null</b> attributes will be used.
     */
    public ExampleDsl<T> with(final Attribute<T, ?>... exampleAttributes) {
        return exampleDsl.with(ComparisonOperation.EQ, exampleAttributes);
    }


    /**
     * A criteria will be created using the example entity, provided attributes and using <code>comparisonOperation</code> to compare the attribute's values.
     * It will use comparisonOperation for comparing {@code exampleAttributes}.
     *
     * @param comparisonOperation the operation to be used while comparing example attributes.
     * @param exampleAttributes   attributes from example entity to consider. If no attribute is provided then <b>non null</b> attributes will be used.
     */
    public ExampleDsl<T> with(ComparisonOperation comparisonOperation, Attribute<T, ?>... exampleAttributes) {
        return exampleDsl.with(comparisonOperation, exampleAttributes);
    }

}
