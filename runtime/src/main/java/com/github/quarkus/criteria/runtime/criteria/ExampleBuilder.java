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
        private final List<Class<?>> visitedEntities = new ArrayList<>();

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
         * A criteria will be created using the example entity and provided attributes using <code>EQ</code> operation to restrict the query.
         *
         * @param usingAttributes attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
         */
        public ExampleBuilderDsl usingAttributes(final Attribute<T, ?>... usingAttributes) {
            return usingAttributes(ComparisonOperation.EQ, usingAttributes);
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
            return addExampleRestrictions(comparisonOperation, usingAttributes);
        }


        private ExampleBuilderDsl addExampleRestrictions(ComparisonOperation comparisonOperation, Attribute<T, ?>[] usingAttributes) {
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
                        addSingularRestriction(usingAttribute, comparisonOperation);
                    } else if (usingAttribute instanceof PluralAttribute) {
                        if (!exampleAttributes.contains(usingAttribute)) {
                            addAssociationRestriction(usingAttribute, comparisonOperation);
                        } else if (usingAttribute.getJavaMember() instanceof Field) {
                            final Object value = field.get(example);
                            if (value == null || !(value instanceof Collection)) {
                                LOG.warning(format("Ignoring example attribute %s for entity %s because it's value %s is not a Collection.", usingAttribute.getName(), example.getClass().getName(), value));
                                continue;
                            }
                            addPluralRestriction(usingAttribute, (Collection) value);
                        }
                    }
                } catch (IllegalAccessException e) {
                    LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
                }
            }
            return this;
        }

        private void addSingularRestriction(final Attribute<T, ?> attribute, ComparisonOperation operation) {
            if (attribute.getJavaMember() instanceof Field) {
                if (operation == null) {
                    operation = ComparisonOperation.EQ;
                }
                if (!exampleAttributes.contains(attribute)) {
                    addAssociationRestriction(attribute, operation);
                    return;
                }
                final Field field = (Field) attribute.getJavaMember();
                field.setAccessible(true);
                try {
                    final Object value = field.get(example);
                    if (value != null || NULL_OPERATIONS.contains(operation)) {
                        LOG.log(Level.DEBUG, format("Adding an %s restriction on attribute %s using value %s.", operation.name(), attribute.getName(), value));
                        createRestriction(criteria, (SingularAttribute) attribute, operation, value);
                    }
                } catch (IllegalAccessException e) {
                    LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
                }
            }
        }

        private void createRestriction(Criteria criteria, SingularAttribute attribute, ComparisonOperation operation, Object value) {
            hasRestrictions = true;
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

        private void addAssociationRestriction(Attribute attribute, ComparisonOperation operation) {
            try {
                visitedEntities.clear();
                addAssociationRestrictionRecursion(example, criteria, exampleAttributes, attribute, operation, new HashSet<>());
            } catch (Exception e) {
                LOG.warning(format("Attribute %s or attribute type %s not found in example entity %s.",
                        attribute.getName(), attribute.getJavaMember().getDeclaringClass(), example.getClass().getName()));
            }

        }

        private void addAssociationRestrictionRecursion(Object exampleValue, Criteria<?, ?> criteria, Set<Attribute<?, ?>> exampleAttributes, Attribute attribute, ComparisonOperation operation, Set<JoinInfo> joinInfoList) throws IllegalAccessException {
            Optional<Attribute<?, ?>> exampleAttributeOptional = exampleAttributes.stream()
                    .filter(exampleAttribute -> exampleAttribute.equals(attribute))
                    .findFirst();
            if (!exampleAttributeOptional.isPresent()) { //attr not found, search into entity associations
                exampleAttributes.stream()
                        .filter(Attribute::isAssociation)
                        .forEach(attr -> {
                            try {
                                final boolean isCollection = attr.isCollection();
                                Class<?> attrType = isCollection ? ((PluralAttribute) attr).getElementType().getJavaType() : attr.getJavaType();
                                if (!visitedEntities.contains(attrType)) {
                                    Set<Attribute<?, ?>> attrEntityAttributes = (Set<Attribute<?, ?>>) exampleBuilder.entityManager.getMetamodel().entity(attrType).getAttributes();
                                    visitedEntities.add(attrType);
                                    Field field = (Field) attr.getJavaMember();
                                    field.setAccessible(true);
                                    Object associationValue = null;
                                    if (isCollection) {
                                        Collection collection = (Collection) field.get(exampleValue);
                                        if (!collection.isEmpty()) {
                                            associationValue = collection.iterator().next();
                                        }
                                    } else {
                                        associationValue = field.get(exampleValue);
                                    }
                                    if (associationValue != null) {
                                        final Criteria associationJoin = new QueryCriteria(attrType, attrType, exampleBuilder.entityManager);
                                        joinInfoList.add(new JoinInfo(attr, criteria, associationJoin));
                                        addAssociationRestrictionRecursion(associationValue, associationJoin, attrEntityAttributes, attribute, operation, joinInfoList);
                                    }
                                }
                            } catch (IllegalAccessException e) {
                            }
                        });
            } else {
                if (exampleValue != null || NULL_OPERATIONS.contains(operation)) {
                    joinInfoList.stream()
                            .forEach(joinInfo -> addJoin(joinInfo.attribute, joinInfo.criteria, joinInfo.joinCriteria));
                    Field attrField = (Field) attribute.getJavaMember();
                    attrField.setAccessible(true);
                    Object attrValue = attrField.get(exampleValue);
                    createRestriction(criteria, (SingularAttribute) attribute, operation, attrValue);
                    return;
                }
            }

        }

        private void addJoin(Attribute exampleAttribute, Criteria criteria, Criteria joinCriteria) {
            final boolean isCollection = exampleAttribute instanceof PluralAttribute;
            if (isCollection) {
                addPluralJoin(criteria, (PluralAttribute<T, ?, ?>) exampleAttribute, joinCriteria);
            } else {
                criteria.join((SingularAttribute) exampleAttribute, joinCriteria);
            }
        }

        private void addPluralRestriction(final Attribute<T, ?> attribute, final Collection values) {
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
                addPluralJoin(criteria, listAttribute, joinCriteria);
                joinCriteria.in(id, ids);
            }
        }

        private void addPluralJoin(Criteria criteria, PluralAttribute<T, ?, ?> listAttribute, Criteria joinCriteria) {
            if (listAttribute instanceof ListAttribute) {
                criteria.join((ListAttribute) listAttribute, joinCriteria);
            } else if (listAttribute instanceof SetAttribute) {
                criteria.join((SetAttribute) listAttribute, joinCriteria);
            } else if (listAttribute instanceof MapAttribute) {
                criteria.join((MapAttribute) listAttribute, joinCriteria);
            } else if (listAttribute instanceof CollectionAttribute) {
                criteria.join((CollectionAttribute) listAttribute, joinCriteria);
            }
        }

        private Set<Attribute<?, ?>> resolveExampleEntityAttributes() {
            Set<Attribute<?, ?>> attributes = (Set<Attribute<?, ?>>) exampleBuilder.entityManager.getMetamodel().entity(example.getClass()).getAttributes();
            if (attributes == null) {
                LOG.warning(format("No attributes found on entity %s", example.getClass().getName()));
                attributes = Collections.emptySet();
            }
            return attributes;
        }

        private class JoinInfo {
            private Attribute attribute;
            private Criteria criteria;
            private Criteria joinCriteria;

            public JoinInfo(Attribute attribute, Criteria criteria, Criteria joinCriteria) {
                this.attribute = attribute;
                this.criteria = criteria;
                this.joinCriteria = joinCriteria;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                JoinInfo joinInfo = (JoinInfo) o;
                return Objects.equals(attribute, joinInfo.attribute) &&
                        Objects.equals(criteria, joinInfo.criteria) &&
                        Objects.equals(joinCriteria, joinInfo.joinCriteria);
            }

            @Override
            public int hashCode() {
                return Objects.hash(attribute, criteria, joinCriteria);
            }
        }
    }

}
