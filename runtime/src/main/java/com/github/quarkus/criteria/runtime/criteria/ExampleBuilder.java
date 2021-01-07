package com.github.quarkus.criteria.runtime.criteria;

import com.github.quarkus.criteria.runtime.model.ComparisonOperation;
import com.github.quarkus.criteria.runtime.model.PersistenceEntity;
import org.apache.deltaspike.data.api.criteria.Criteria;
import org.apache.deltaspike.data.impl.criteria.QueryCriteria;
import org.jboss.logmanager.Level;

import javax.persistence.EntityManager;
import javax.persistence.criteria.JoinType;
import javax.persistence.metamodel.*;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

import static com.github.quarkus.criteria.runtime.model.ComparisonOperation.IS_NULL;
import static com.github.quarkus.criteria.runtime.model.ComparisonOperation.NOT_NULL;
import static java.lang.String.format;

/**
 * @param <T> the example entity type
 * @author rmpestano
 */
public class ExampleBuilder<T extends PersistenceEntity> {
    private static final Logger LOG = Logger.getLogger(ExampleBuilder.class.getName());
    private static final List<ComparisonOperation> NULL_OPERATIONS = Arrays.asList(IS_NULL, NOT_NULL);

    private EntityManager entityManager;

    public ExampleBuilder(EntityManager entityManager) {
        this.entityManager = entityManager;
        if (entityManager == null) {
            throw new IllegalArgumentException("Entity manager should be provided");
        }
    }

    public ExampleBuilderDsl of(T example) {
        return new ExampleBuilderDsl(example, this);
    }

    public static class ExampleBuilderDsl<T extends PersistenceEntity> {
        private final Set<Attribute<?, ?>> exampleAttributes;
        private Criteria<T, ?> criteria;
        private T example;
        private boolean hasRestrictions;
        private ExampleBuilder<T> exampleBuilder;

        public ExampleBuilderDsl(T example, ExampleBuilder exampleBuilder) {
            this.exampleBuilder = exampleBuilder;
            this.example = example;
            if (example == null) {
                throw new IllegalArgumentException("Example instance should not be null");
            }
            this.criteria = new QueryCriteria(example.getClass(), example.getClass(), exampleBuilder.entityManager);
            this.exampleAttributes = resolveExampleEntityAttributes();
        }

        public Criteria<T, ?> build() {
            if (!hasRestrictions) {
                criteria = usingAttributes().build();
            }
            return criteria;
        }

        /**
         * Add example restrictions to existing criteria
         *
         * @param criteria a pre configured criteria
         */
        public ExampleBuilderDsl usingCriteria(Criteria<T, ?> criteria) {
            this.criteria = criteria;
            return this;
        }

        /**
         * A criteria will be created using the example entity and provided attributes to restrict the query.
         * It will use <code>eq</code> for comparing non association attributes.
         * For <code>oneToOne</code> associations the entity PK will be compared and for toMany association an <code>in</code>
         * for comparing associated entities PKs.
         *
         * @param usingAttributes attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
         */
        public ExampleBuilderDsl usingAttributes(final Attribute<T, ?>... usingAttributes) {
            return usingAttributes(ComparisonOperation.EQ, usingAttributes);
        }

        /**
         * A criteria will be created using the example entity and provided attributes to restrict the query.
         * It will use <code>eq</code> for comparing non association attributes.
         * For <code>oneToOne</code> associations the entity PK will be compared and for toMany association an <code>in</code>
         * for comparing associated entities PKs.
         * <p>
         * Associations will be fetched in result mapping.
         *
         * @param usingAttributes attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
         */
        public ExampleBuilderDsl usingAttributesAndFetch(final Attribute<T, ?>... usingAttributes) {
            return usingAttributesAndFetch(ComparisonOperation.EQ, usingAttributes);
        }

        /**
         * A criteria will be created using the example entity and provided attributes to restrict the query.
         * It will use comparisonOperation for comparing non association attributes.
         * For <code>oneToOne</code> associations the entity PK will be compared and for toMany association an <code>in</code> for comparing associated entities PKs
         *
         * @param comparisonOperation the operation to be used while comparing the attributes.
         * @param usingAttributes     attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
         */
        public ExampleBuilderDsl usingAttributes(ComparisonOperation comparisonOperation, Attribute<T, ?>... usingAttributes) {
            return addExampleRestrictions(comparisonOperation, usingAttributes, false);
        }

        /**
         * A criteria will be created using the example entity and provided attributes to restrict the query.
         * It will use comparisonOperation for comparing non association attributes.
         * For <code>oneToOne</code> associations the entity PK will be compared and for toMany association an <code>in</code> for comparing associated entities PKs
         * <p>
         * Associations will be fetched in result mapping.
         *
         * @param comparisonOperation the operation to be used while comparing the attributes.
         * @param usingAttributes     attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
         */
        public ExampleBuilderDsl usingAttributesAndFetch(ComparisonOperation comparisonOperation, Attribute<T, ?>... usingAttributes) {
            return addExampleRestrictions(comparisonOperation, usingAttributes, true);
        }

        private ExampleBuilderDsl addExampleRestrictions(ComparisonOperation comparisonOperation, Attribute<T, ?>[] usingAttributes, boolean fetch) {
            hasRestrictions = true;
            if (usingAttributes == null || usingAttributes.length == 0) {
                usingAttributes = exampleAttributes.toArray(new Attribute[0]);
            }
            for (Attribute<T, ?> usingAttribute : usingAttributes) {
                if (!(usingAttribute.getJavaMember() instanceof Field)) {
                    continue;
                }
                final Field field = (Field) usingAttribute.getJavaMember();
                try {
                    if (usingAttribute instanceof SingularAttribute) {
                        addSingularRestriction(usingAttribute, comparisonOperation, fetch);
                    } else if (usingAttribute instanceof PluralAttribute) {
                        if (!exampleAttributes.contains(usingAttribute)) {
                            addAssociationRestriction(usingAttribute, comparisonOperation, fetch);
                        } else if (usingAttribute.getJavaMember() instanceof Field) {
                            final Object value = field.get(example);
                            if (value == null || !(value instanceof Collection)) {
                                LOG.warning(format("Ignoring example attribute %s for entity %s because it's value %s is not a Collection.", usingAttribute.getName(), example.getClass().getName(), value));
                                continue;
                            }
                            addPluralRestriction(usingAttribute, (Collection) value, fetch);
                        }
                    }
                } catch (IllegalAccessException e) {
                    LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
                }
            }
            return this;
        }

        private void addSingularRestriction(final Attribute<T, ?> attribute, ComparisonOperation operation, boolean fetch) {
            if (attribute.getJavaMember() instanceof Field) {
                final Field field = (Field) attribute.getJavaMember();
                field.setAccessible(true);
                if (operation == null) {
                    operation = ComparisonOperation.EQ;
                }
                try {
                    if (!exampleAttributes.contains(attribute)) {
                        addAssociationRestriction(attribute, operation, fetch);
                        return;
                    }
                    final Object value = field.get(example);
                    if (value != null || NULL_OPERATIONS.contains(operation)) {
                        LOG.log(Level.DEBUG, format("Adding an %s restriction on attribute %s using value %s.", operation.name(), attribute.getName(), value));
                        if (fetch) {
                            criteria.fetch((SingularAttribute) attribute, JoinType.INNER);
                        }
                        addRestriction(criteria, (SingularAttribute) attribute, operation, value);
                    }
                } catch (IllegalAccessException e) {
                    LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
                }
            }
        }

        private void addRestriction(Criteria criteria, SingularAttribute attribute, ComparisonOperation operation, Object value) {
            switch (operation) {
                case EQ:
                    criteria.eq(attribute, value);
                    break;
                case EQ_IGNORE_CASE:
                    criteria.eqIgnoreCase(attribute, value.toString());
                    break;
                case NOT_EQ:
                    criteria.notEq(attribute, value);
                    break;
                case NOT_EQ_IGNORE_CASE:
                    criteria.notEqIgnoreCase(attribute, value.toString());
                    break;
                case GT:
                    criteria.gt(attribute, (Comparable) value);
                    break;
                case GT_OR_EQ:
                    criteria.gtOrEq(attribute, (Comparable) value);
                    break;
                case LT:
                    criteria.lt(attribute, (Comparable) value);
                    break;
                case LT_OR_EQ:
                    criteria.ltOrEq(attribute, (Comparable) value);
                    break;
                case IS_NULL:
                    criteria.isNull(attribute);
                    break;
                case NOT_NULL:
                    criteria.notNull(attribute);
                    break;
                case LIKE:
                    criteria.like(attribute, value.toString());
                    break;
                case LIKE_IGNORE_CASE:
                    criteria.likeIgnoreCase(attribute, value.toString());
                    break;
                case NOT_LIKE:
                    criteria.notLike(attribute, value.toString());
                    break;
                case NOT_LIKE_IGNORE_CASE:
                    criteria.notLikeIgnoreCase(attribute, value.toString());
                    break;
                case IS_EMPTY:
                    criteria.empty(attribute);
                    break;
                case NOT_EMPTY:
                    criteria.notEmpty(attribute);
                    break;
            }
        }

        private void addAssociationRestriction(Attribute<T, ?> attribute, ComparisonOperation comparisonOperation, boolean fetch) throws IllegalAccessException {
            Optional<Attribute<?, ?>> exampleAttributeOptional = exampleAttributes.stream()
                    .filter(attr -> attr.getJavaType()
                            .equals(attribute.getJavaMember().getDeclaringClass())).findFirst();

            if (!exampleAttributeOptional.isPresent()) {
                throw new IllegalArgumentException(format("Attribute %s or attribute type %s not found in example entity %s.",
                        attribute.getName(), attribute.getJavaMember().getDeclaringClass(), example.getClass().getName()));
            }

            final Attribute<?, ?> exampleAttribute = exampleAttributeOptional.get();
            final Field exampleField = (Field) exampleAttribute.getJavaMember();
            exampleField.setAccessible(true);
            final Field associationField = (Field) attribute.getJavaMember();
            associationField.setAccessible(true);
            final Object value = exampleField.get(example);
            if (exampleAttribute.isCollection()) {
                if (value == null || !(value instanceof Collection)) {
                    LOG.warning(format("Ignoring example attribute %s for entity %s because it's value %s is not a Collection.", attribute.getName(), example.getClass().getName(), value));
                }
                addPluralRestriction(attribute, (Collection) value, fetch);
            } else {
                if (value != null || NULL_OPERATIONS.contains(comparisonOperation)) {
                    final Criteria joinCriteria = new QueryCriteria(exampleAttribute.getJavaType(),
                            exampleAttribute.getJavaType(), exampleBuilder.entityManager);
                    addRestriction(joinCriteria, (SingularAttribute) attribute, comparisonOperation, associationField.get(exampleField.get(example)));
                    if (fetch) {
                        criteria.fetch((SingularAttribute) exampleAttribute);
                    }
                    criteria.join((SingularAttribute) exampleAttribute, joinCriteria);
                }
            }
        }

        private void addPluralRestriction(final Attribute<T, ?> attribute, final Collection values, boolean fetch) {
            if (values == null || values.isEmpty()) {
                LOG.warning(format("Ignoring example attribute %s for entity %s because it's value is null", attribute.getName(), example.getClass().getName()));
                return;
            }
            if (attribute.getJavaMember() instanceof Field) {
                final PluralAttribute<T, ?, ?> listAttribute = (PluralAttribute<T, ?, ?>) attribute;
                final Class joinClass = listAttribute.getElementType().getJavaType();
                final Criteria joinCriteria = new QueryCriteria(joinClass, joinClass, exampleBuilder.entityManager, JoinType.LEFT);
                final Field field = (Field) attribute.getJavaMember();
                field.setAccessible(true);
                LOG.log(Level.DEBUG, format("Adding an 'in'restriction on attribute %s using values %s.", attribute.getName(), values));
                Collection<PersistenceEntity> association = (Collection<PersistenceEntity>) values;
                SingularAttribute id = exampleBuilder.entityManager.getMetamodel().entity(listAttribute.getElementType().getJavaType()).getId(association.iterator().next().getId().getClass());
                List<Serializable> ids = new ArrayList<>();
                for (PersistenceEntity persistenceEntity : association) {
                    ids.add(persistenceEntity.getId());
                }
                if (fetch) {
                    criteria.fetch(listAttribute, JoinType.LEFT);
                }
                if (listAttribute instanceof ListAttribute) {
                    criteria.join((ListAttribute) listAttribute, joinCriteria);
                } else if (listAttribute instanceof SetAttribute) {
                    criteria.join((SetAttribute) listAttribute, joinCriteria);
                } else if (listAttribute instanceof MapAttribute) {
                    criteria.join((MapAttribute) listAttribute, joinCriteria);
                } else if (listAttribute instanceof CollectionAttribute) {
                    criteria.join((CollectionAttribute) listAttribute, joinCriteria);
                }
                joinCriteria.in(id, ids);
            }
        }

        private Set<Attribute<?, ?>> resolveExampleEntityAttributes() {
            Set<Attribute<?, ?>> attributes = (Set<Attribute<?, ?>>) exampleBuilder.entityManager.getMetamodel().entity(example.getClass()).getAttributes();
            if (attributes == null) {
                attributes = Collections.emptySet();
            }
            return attributes;
        }
    }

}
