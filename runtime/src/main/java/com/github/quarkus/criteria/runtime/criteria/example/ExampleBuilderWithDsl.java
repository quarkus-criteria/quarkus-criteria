package com.github.quarkus.criteria.runtime.criteria.example;

import com.github.quarkus.criteria.runtime.model.ComparisonOperation;
import com.github.quarkus.criteria.runtime.model.PersistenceEntity;
import org.apache.deltaspike.data.api.criteria.Criteria;

import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;
import java.lang.reflect.Field;
import java.util.Collection;

import static java.lang.String.format;

public class ExampleBuilderWithDsl<T extends PersistenceEntity> {

    private final ExampleBuilderDsl<T> exampleBuilderDsl;

    public ExampleBuilderWithDsl(ExampleBuilderDsl<T> exampleBuilderDsl) {
        this.exampleBuilderDsl = exampleBuilderDsl;
    }

    /**
     * A criteria will be created using the example entity and provided attributes using <code>EQ</code> operation to restrict the query.
     *
     * @param exampleAttributes attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
     */
    public ExampleBuilderWithDsl<T> with(final Attribute<T, ?>... exampleAttributes) {
        return with(ComparisonOperation.EQ, exampleAttributes);
    }


    /**
     * A criteria will be created using the example entity and provided attributes to restrict the query.
     * It will use comparisonOperation for comparing non association attributes.
     *
     * @param comparisonOperation the operation to be used while comparing the attributes.
     * @param exampleAttributes   attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
     */
    public ExampleBuilderWithDsl<T> with(ComparisonOperation comparisonOperation, Attribute<T, ?>... exampleAttributes) {
        if (comparisonOperation == null) {
            comparisonOperation = ComparisonOperation.EQ;
        }
        return addExampleRestrictions(comparisonOperation, exampleAttributes);
    }

    /**
     * @return A criteria populated with restrictions based on example entity
     */
    public Criteria<T, ?> build() {
        if (!exampleBuilderDsl.hasRestrictions) {
            return with().build();
        }
        return exampleBuilderDsl.criteria;
    }

    ExampleBuilderWithDsl addExampleRestrictions(ComparisonOperation comparisonOperation, Attribute<T, ?>[] usingAttributes) {
        if (usingAttributes == null || usingAttributes.length == 0) {
            usingAttributes = exampleBuilderDsl.exampleAttributes.toArray(new Attribute[0]);
        }
        for (Attribute usingAttribute : usingAttributes) {
            if (!(usingAttribute.getJavaMember() instanceof Field)) {
                continue;
            }
            final Field field = (Field) usingAttribute.getJavaMember();
            try {
                if (usingAttribute instanceof SingularAttribute) {
                    exampleBuilderDsl.addSingularRestriction(usingAttribute, comparisonOperation);
                } else if (usingAttribute instanceof PluralAttribute) {
                    if (!exampleBuilderDsl.exampleAttributes.contains(usingAttribute)) {
                        exampleBuilderDsl.addAssociationRestriction(usingAttribute, comparisonOperation);
                    } else {
                        final Object value = field.get(exampleBuilderDsl.example);
                        if (!(value instanceof Collection)) {
                            exampleBuilderDsl.exampleBuilder.LOG.warning(format("Ignoring example attribute %s for entity %s because it's value %s is not a Collection.", usingAttribute.getName(), exampleBuilderDsl.example.getClass().getName(), value));
                            continue;
                        }
                        exampleBuilderDsl.createPluralRestriction(usingAttribute, (Collection) value);
                    }
                }
            } catch (IllegalAccessException e) {
                exampleBuilderDsl.exampleBuilder.LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), exampleBuilderDsl.example.getClass().getName()));
            }
        }
        return this;
    }
}
