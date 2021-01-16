package com.github.quarkus.criteria.runtime.criteria.example;

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

import static com.github.quarkus.criteria.runtime.model.ComparisonOperation.*;
import static java.lang.String.format;

/**
 * This class is responsible for creating the criteria restrictions
 *
 * @param <T> Example entity type
 */
public class ExampleDsl<T extends PersistenceEntity> {
    private static final Logger LOG = Logger.getLogger(ExampleBuilder.class.getName());
    private static final List<ComparisonOperation> NULL_OPERATIONS = Arrays.asList(IS_NULL, NOT_NULL);
    private final List<Class<?>> visitedEntities = new ArrayList<>();
    private final Set<Attribute<?, ?>> exampleAttributes;
    private T example;
    private EntityManager entityManager;
    private boolean hasRestrictions;
    Criteria<T, ?> criteria;

    public ExampleDsl(T example, EntityManager entityManager) {
        if (example == null) {
            throw new IllegalArgumentException("Example instance should not be null");
        }
        this.example = example;
        this.criteria = new QueryCriteria(example.getClass(), example.getClass(), entityManager);
        this.entityManager = entityManager;
        this.exampleAttributes = resolveExampleEntityAttributes();
    }

    /**
     * A criteria will be created using the example entity and provided attributes using <code>EQ</code> operation to restrict the query.
     *
     * @param exampleAttributes attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
     */
    public ExampleDsl<T> with(final Attribute<T, ?>... exampleAttributes) {
        return with(EQ, exampleAttributes);
    }


    /**
     * A criteria will be created using the example entity and provided attributes to compare the attribute's values.
     * It will use comparisonOperation for comparing non association attributes.
     *
     * @param comparisonOperation the operation to be used while comparing the attributes.
     * @param exampleAttributes   attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
     */
    public ExampleDsl<T> with(ComparisonOperation comparisonOperation, Attribute<T, ?>... exampleAttributes) {
        if (comparisonOperation == null) {
            comparisonOperation = EQ;
        }
        return addRestrictions(comparisonOperation, exampleAttributes);
    }

    public ExampleDsl<T> or(Attribute<T, ?>... exampleAttributes) {
        return or(EQ, exampleAttributes);
    }

    public ExampleDsl<T> or(ComparisonOperation comparisonOperation, Attribute<T, ?>... exampleAttributes) {
        Criteria previousCriteria = this.criteria;
        List<Criteria> orCriterias = new LinkedList<>();
        orCriterias.add(previousCriteria);
        for (Attribute<T, ?> exampleAttribute : exampleAttributes) {
            this.criteria = new QueryCriteria(example.getClass(), example.getClass(), entityManager);
            orCriterias.add(with(comparisonOperation, exampleAttribute).criteria);
        }
        this.criteria = new QueryCriteria(example.getClass(), example.getClass(), entityManager)
                .or(orCriterias);
        return this;
    }

    /**
     * @return A criteria populated with restrictions based on example entity
     */
    public Criteria build() {
        if (!hasRestrictions) {
            criteria = with().build();
        }
        return criteria;
    }

    private ExampleDsl addRestrictions(ComparisonOperation comparisonOperation, Attribute<T, ?>[] usingAttributes) {
        if (usingAttributes == null || usingAttributes.length == 0) {
            usingAttributes = exampleAttributes.toArray(new Attribute[0]);
        }
        for (Attribute usingAttribute : usingAttributes) {
            createRestriction(comparisonOperation, usingAttribute);
        }
        return this;
    }

    private Criteria createRestriction(ComparisonOperation comparisonOperation, Attribute usingAttribute) {
        if (!(usingAttribute.getJavaMember() instanceof Field)) {
            return null;
        }
        final Field field = (Field) usingAttribute.getJavaMember();
        try {
            if (usingAttribute instanceof SingularAttribute) {
                return addSingularRestriction(usingAttribute, comparisonOperation);
            } else if (usingAttribute instanceof PluralAttribute) {
                if (!exampleAttributes.contains(usingAttribute)) {
                    addAssociationRestriction(usingAttribute, comparisonOperation);
                } else {
                    final Object value = field.get(example);
                    if (!(value instanceof Collection)) {
                        LOG.warning(format("Ignoring example attribute %s for entity %s because it's value %s is not a Collection.", usingAttribute.getName(), example.getClass().getName(), value));
                        return null;
                    }
                    return createPluralRestriction(criteria, usingAttribute, (Collection) value);
                }
            }
        } catch (IllegalAccessException e) {
            LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
        }
        return criteria;
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
                                Set<Attribute<?, ?>> attrEntityAttributes = (Set<Attribute<?, ?>>) entityManager.getMetamodel().entity(attrType).getAttributes();
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
                                    final Criteria associationJoin = new QueryCriteria(attrType, attrType, entityManager);
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
                if (attrValue instanceof Collection) {
                    createPluralRestriction(criteria, attribute, (Collection) attrValue);
                } else {
                    createSingularRestriction(criteria, (SingularAttribute) attribute, operation, attrValue);
                }
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

    private Criteria createPluralRestriction(final Criteria criteria, final Attribute<T, ?> attribute, final Collection values) {
        hasRestrictions = true;
        if (values == null || values.isEmpty()) {
            LOG.warning(format("Ignoring example attribute %s for entity %s because it's value is null", attribute.getName(), example.getClass().getName()));
            return null;
        }
        final PluralAttribute<T, ?, ?> listAttribute = (PluralAttribute<T, ?, ?>) attribute;
        final Class joinClass = listAttribute.getElementType().getJavaType();
        final Criteria joinCriteria = new QueryCriteria(joinClass, joinClass, entityManager, JoinType.LEFT);
        final Field field = (Field) attribute.getJavaMember();
        field.setAccessible(true);
        if (LOG.isLoggable(Level.DEBUG)) {
            LOG.log(Level.DEBUG, format("Adding an 'in'restriction on attribute %s using values %s.", attribute.getName(), values));
        }
        final Collection<PersistenceEntity> association = (Collection<PersistenceEntity>) values;
        final SingularAttribute id = entityManager.getMetamodel().entity(listAttribute.getElementType().getJavaType()).getId(association.iterator().next().getId().getClass());
        List<Serializable> ids = new ArrayList<>();
        for (PersistenceEntity persistenceEntity : association) {
            ids.add(persistenceEntity.getId());
        }
        addPluralJoin(criteria, listAttribute, joinCriteria);
        return joinCriteria.in(id, ids);
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

    private Criteria addSingularRestriction(final Attribute<T, ?> attribute, ComparisonOperation operation) {
        if (!exampleAttributes.contains(attribute)) {
            addAssociationRestriction(attribute, operation);
            return criteria;
        }
        final Field field = (Field) attribute.getJavaMember();
        field.setAccessible(true);
        try {
            final Object value = field.get(example);
            if (value != null || NULL_OPERATIONS.contains(operation)) {
                if (LOG.isLoggable(Level.DEBUG)) {
                    LOG.log(Level.DEBUG, format("Adding an %s restriction on attribute %s using value %s.", operation.name(), attribute.getName(), value));
                }
                return createSingularRestriction(criteria, (SingularAttribute) attribute, operation, value);
            }
        } catch (IllegalAccessException e) {
            LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
        }
        return null;
    }

    private Criteria createSingularRestriction(Criteria criteria, SingularAttribute attribute, ComparisonOperation operation, Object value) {
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
        return criteria;
    }

    private Set<Attribute<?, ?>> resolveExampleEntityAttributes() {
        Set<Attribute<?, ?>> attributes = (Set<Attribute<?, ?>>) entityManager.getMetamodel().entity(example.getClass()).getAttributes();
        if (attributes == null) {
            LOG.warning(format("No attributes found on entity %s", example.getClass().getName()));
            attributes = Collections.emptySet();
        }
        return attributes;
    }
}
