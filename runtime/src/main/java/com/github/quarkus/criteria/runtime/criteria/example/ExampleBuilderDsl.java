package com.github.quarkus.criteria.runtime.criteria.example;

import com.github.quarkus.criteria.runtime.model.ComparisonOperation;
import com.github.quarkus.criteria.runtime.model.PersistenceEntity;
import org.apache.deltaspike.data.api.criteria.Criteria;
import org.apache.deltaspike.data.impl.criteria.QueryCriteria;
import org.jboss.logmanager.Level;

import javax.persistence.criteria.JoinType;
import javax.persistence.metamodel.*;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;

import static java.lang.String.format;

public class ExampleBuilderDsl<T extends PersistenceEntity> {
    final Set<Attribute<?, ?>> exampleAttributes;
    Criteria<T, ?> criteria;
    T example;
    boolean hasRestrictions;
    ExampleBuilder<T> exampleBuilder;
    final List<Class<?>> visitedEntities = new ArrayList<>();
    ExampleBuilderWithDsl<T> exampleBuilderWithDsl = new ExampleBuilderWithDsl<>(this);

    public ExampleBuilderDsl(T example, ExampleBuilder exampleBuilder) {
        this.exampleBuilder = exampleBuilder;
        this.example = example;
        if (example == null) {
            throw new IllegalArgumentException("Example instance should not be null");
        }
        this.criteria = new QueryCriteria(example.getClass(), example.getClass(), exampleBuilder.entityManager);
        this.exampleAttributes = resolveExampleEntityAttributes();
    }

    /**
     * @return A criteria populated with restrictions based on example entity
     */
    public Criteria<T, ?> build() {
        if (!hasRestrictions) {
            criteria = with().build();
        }
        return criteria;
    }

    /**
     * Add example restrictions to existing criteria
     *
     * @param criteria a pre configured criteria
     */
    public ExampleBuilderWithDsl<T> withCriteria(Criteria<T, ?> criteria) {
        this.criteria = criteria;
        return exampleBuilderWithDsl;
    }

    /**
     * A criteria will be created using the example entity and provided attributes using <b>EQ</b> operation to restrict the query.
     *
     * @param exampleAttributes attributes from example entity to consider. If no attribute is provided then <b>non null</b> attributes will be used.
     */
    public ExampleBuilderWithDsl<T> with(final Attribute<T, ?>... exampleAttributes) {
        return exampleBuilderWithDsl.with(ComparisonOperation.EQ, exampleAttributes);
    }


    /**
     * A criteria will be created using the example entity and provided attributes to restrict the query.
     * It will use comparisonOperation for comparing {@code exampleAttributes}.
     *
     * @param comparisonOperation the operation to be used while comparing example attributes.
     * @param exampleAttributes   attributes from example entity to consider. If no attribute is provided then <b>non null</b> attributes will be used.
     */
    public ExampleBuilderWithDsl<T> with(ComparisonOperation comparisonOperation, Attribute<T, ?>... exampleAttributes) {
        if (comparisonOperation == null) {
            comparisonOperation = ComparisonOperation.EQ;
        }
        return exampleBuilderWithDsl.addExampleRestrictions(comparisonOperation, exampleAttributes);
    }


    void addSingularRestriction(final Attribute<T, ?> attribute, ComparisonOperation operation) {
        if (!exampleAttributes.contains(attribute)) {
            addAssociationRestriction(attribute, operation);
            return;
        }
        final Field field = (Field) attribute.getJavaMember();
        field.setAccessible(true);
        try {
            final Object value = field.get(example);
            if (value != null || exampleBuilder.NULL_OPERATIONS.contains(operation)) {
                if (exampleBuilder.LOG.isLoggable(Level.DEBUG)) {
                    exampleBuilder.LOG.log(Level.DEBUG, format("Adding an %s restriction on attribute %s using value %s.", operation.name(), attribute.getName(), value));
                }
                createSingularRestriction(criteria, (SingularAttribute) attribute, operation, value);
            }
        } catch (IllegalAccessException e) {
            exampleBuilder.LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
        }
    }

    void createSingularRestriction(Criteria criteria, SingularAttribute attribute, ComparisonOperation operation, Object value) {
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

    void addAssociationRestriction(Attribute attribute, ComparisonOperation operation) {
        try {
            visitedEntities.clear();
            addAssociationRestrictionRecursion(example, criteria, exampleAttributes, attribute, operation, new HashSet<>());
        } catch (Exception e) {
            exampleBuilder.LOG.warning(format("Attribute %s or attribute type %s not found in example entity %s.",
                    attribute.getName(), attribute.getJavaMember().getDeclaringClass(), example.getClass().getName()));
        }

    }

    void addAssociationRestrictionRecursion(Object exampleValue, Criteria<?, ?> criteria, Set<Attribute<?, ?>> exampleAttributes, Attribute attribute, ComparisonOperation operation, Set<JoinInfo> joinInfoList) throws IllegalAccessException {
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
            if (exampleValue != null || exampleBuilder.NULL_OPERATIONS.contains(operation)) {
                joinInfoList.stream()
                        .forEach(joinInfo -> addJoin(joinInfo.attribute, joinInfo.criteria, joinInfo.joinCriteria));
                Field attrField = (Field) attribute.getJavaMember();
                attrField.setAccessible(true);
                Object attrValue = attrField.get(exampleValue);
                if (attrValue instanceof Collection) {
                    createPluralRestriction(attribute, (Collection) attrValue);
                } else {
                    createSingularRestriction(criteria, (SingularAttribute) attribute, operation, attrValue);
                }
            }
        }

    }

    void addJoin(Attribute exampleAttribute, Criteria criteria, Criteria joinCriteria) {
        final boolean isCollection = exampleAttribute instanceof PluralAttribute;
        if (isCollection) {
            addPluralJoin(criteria, (PluralAttribute<T, ?, ?>) exampleAttribute, joinCriteria);
        } else {
            criteria.join((SingularAttribute) exampleAttribute, joinCriteria);
        }
    }

    void createPluralRestriction(final Attribute<T, ?> attribute, final Collection values) {
        hasRestrictions = true;
        if (values == null || values.isEmpty()) {
            exampleBuilder.LOG.warning(format("Ignoring example attribute %s for entity %s because it's value is null", attribute.getName(), example.getClass().getName()));
            return;
        }
        final PluralAttribute<T, ?, ?> listAttribute = (PluralAttribute<T, ?, ?>) attribute;
        final Class joinClass = listAttribute.getElementType().getJavaType();
        final Criteria joinCriteria = new QueryCriteria(joinClass, joinClass, exampleBuilder.entityManager, JoinType.LEFT);
        final Field field = (Field) attribute.getJavaMember();
        field.setAccessible(true);
        if (exampleBuilder.LOG.isLoggable(Level.DEBUG)) {
            exampleBuilder.LOG.log(Level.DEBUG, format("Adding an 'in'restriction on attribute %s using values %s.", attribute.getName(), values));
        }
        Collection<PersistenceEntity> association = (Collection<PersistenceEntity>) values;
        SingularAttribute id = exampleBuilder.entityManager.getMetamodel().entity(listAttribute.getElementType().getJavaType()).getId(association.iterator().next().getId().getClass());
        List<Serializable> ids = new ArrayList<>();
        for (PersistenceEntity persistenceEntity : association) {
            ids.add(persistenceEntity.getId());
        }
        addPluralJoin(criteria, listAttribute, joinCriteria);
        joinCriteria.in(id, ids);
    }

    void addPluralJoin(Criteria criteria, PluralAttribute<T, ?, ?> listAttribute, Criteria joinCriteria) {
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
            exampleBuilder.LOG.warning(format("No attributes found on entity %s", example.getClass().getName()));
            attributes = Collections.emptySet();
        }
        return attributes;
    }

}
