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
import java.util.stream.Collectors;

import static com.github.quarkus.criteria.runtime.model.ComparisonOperation.IS_NULL;
import static com.github.quarkus.criteria.runtime.model.ComparisonOperation.NOT_NULL;
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
    private List criteriaRestrictions = new ArrayList<>();
    private boolean orEnabled;//or restriction activated
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
        return with(ComparisonOperation.EQ, exampleAttributes);
    }


    /**
     * A criteria will be created using the example entity and provided attributes to compare the attribute's values.
     * It will use comparisonOperation for comparing non association attributes.
     *
     * @param comparisonOperation the operation to be used while comparing the attributes.
     * @param exampleAttributes   attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
     */
    public ExampleDsl<T> with(ComparisonOperation comparisonOperation, Attribute<T, ?>... exampleAttributes) {
        resetRestrictions();
        if (comparisonOperation == null) {
            comparisonOperation = ComparisonOperation.EQ;
        }
        return addRestrictions(comparisonOperation, exampleAttributes);
    }

    /**
     * A criteria with <b>or</b> clause will be created between <code>leftSide</code> and <code>rightSide</code> attributes from the example entity using <b>EQ</b> operation.
     *
     * @param leftSide             first attribute to be considered in the or expression
     * @param rightSide            second attribute to be considered in the or expression
     * @param additionalAttributes additional right hand side attributes from example entity to consider.
     */
    public ExampleDsl<T> or(final Attribute<T, ?> leftSide, final Attribute<T, ?> rightSide, final Attribute<T, ?>... additionalAttributes) {
        return or(ComparisonOperation.EQ, leftSide, rightSide, additionalAttributes);
    }


    /**
     * A criteria with <b>or</b> clause will be created between <code>leftSide</code> and <code>rightSide</code> attributes from example entity
     * using <code>comparisonOperation</code> to compare the attribute's values.
     * It will use comparisonOperation for comparing {@code exampleAttributes}.
     *
     * @param comparisonOperation  the operation to be used while comparing example attributes.
     * @param leftSide             first attribute to be considered in the or expression
     * @param rightSide            second attribute to be considered in the or expression
     * @param additionalAttributes additional right hand side attributes from example entity to consider.
     */
    public ExampleDsl<T> or(ComparisonOperation comparisonOperation, final Attribute<T, ?> leftSide, final Attribute<T, ?> rightSide, final Attribute<T, ?>... additionalAttributes) {
        resetRestrictions();
        activateOrClause();
        if (comparisonOperation == null) {
            comparisonOperation = ComparisonOperation.EQ;
        }
        if (leftSide == null || rightSide == null) {
            throw new IllegalArgumentException("Both right and left hand side attributes are required in the 'or' clause.");
        }
        List<Attribute<T, ?>> attributes = Arrays.asList(leftSide, rightSide);
        if (additionalAttributes != null && additionalAttributes.length > 0) {
            attributes.addAll(Arrays.stream(additionalAttributes).collect(Collectors.toList()));
        }
        return addRestrictions(comparisonOperation, attributes.toArray(new Attribute[attributes.size()]));
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

    ExampleDsl addRestrictions(ComparisonOperation comparisonOperation, Attribute<T, ?>[] usingAttributes) {
        if (usingAttributes == null || usingAttributes.length == 0) {
            usingAttributes = exampleAttributes.toArray(new Attribute[0]);
        }
        for (Attribute usingAttribute : usingAttributes) {
            Criteria restriction = createRestriction(comparisonOperation, usingAttribute);
            if (orEnabled && restriction != null) {
                criteriaRestrictions.add(restriction);
            }
        }
        if (orEnabled && !criteriaRestrictions.isEmpty()) {
            createOrRestriction();
        }
        return this;
    }

    private void createOrRestriction() {
        if (criteriaRestrictions.size() == 1) {
            LOG.warning("For creating OR restrictions more than one non null field must be provided in the 'or' expression.\n Example:  exampleBuilder.of(example)" +
                    ".or(Entity_.field1, Entity_.field1).");
        }
        criteria.or(criteriaRestrictions);
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
                    //TODO return criteria
                    addAssociationRestriction(usingAttribute, comparisonOperation);
                } else {
                    final Object value = field.get(example);
                    if (!(value instanceof Collection)) {
                        LOG.warning(format("Ignoring example attribute %s for entity %s because it's value %s is not a Collection.", usingAttribute.getName(), example.getClass().getName(), value));
                        return null;
                    }
                    return createPluralRestriction(usingAttribute, (Collection) value);
                }
            }
        } catch (IllegalAccessException e) {
            LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
        }
        return criteria;
    }

    void addAssociationRestriction(Attribute attribute, ComparisonOperation operation) {
        //TODO return criteria
        try {
            visitedEntities.clear();
            addAssociationRestrictionRecursion(example, criteria, exampleAttributes, attribute, operation, new HashSet<>());
        } catch (Exception e) {
            LOG.warning(format("Attribute %s or attribute type %s not found in example entity %s.",
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
                    createPluralRestriction(attribute, (Collection) attrValue);
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

    private Criteria createPluralRestriction(final Attribute<T, ?> attribute, final Collection values) {
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
        Collection<PersistenceEntity> association = (Collection<PersistenceEntity>) values;
        SingularAttribute id = entityManager.getMetamodel().entity(listAttribute.getElementType().getJavaType()).getId(association.iterator().next().getId().getClass());
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
        Criteria restrictionCriteria;
        if (isOrActivated()) {
            Class attrType = attribute.isAssociation() ? attribute.getJavaType() : attribute.getDeclaringType().getJavaType();
            restrictionCriteria = new QueryCriteria(attrType, attrType, entityManager);
        } else {
            restrictionCriteria = criteria;
        }
        switch (operation) {
            case EQ:
                restrictionCriteria.eq(attribute, value);
                break;
            case EQ_IGNORE_CASE:
                restrictionCriteria.eqIgnoreCase(attribute, value.toString());
                break;
            case NOT_EQ:
                restrictionCriteria.notEq(attribute, value);
                break;
            case NOT_EQ_IGNORE_CASE:
                restrictionCriteria.notEqIgnoreCase(attribute, value.toString());
                break;
            case GT:
                restrictionCriteria.gt(attribute, (Comparable) value);
                break;
            case GT_OR_EQ:
                restrictionCriteria.gtOrEq(attribute, (Comparable) value);
                break;
            case LT:
                restrictionCriteria.lt(attribute, (Comparable) value);
                break;
            case LT_OR_EQ:
                restrictionCriteria.ltOrEq(attribute, (Comparable) value);
                break;
            case IS_NULL:
                restrictionCriteria.isNull(attribute);
                break;
            case NOT_NULL:
                restrictionCriteria.notNull(attribute);
                break;
            case LIKE:
                restrictionCriteria.like(attribute, value.toString());
                break;
            case LIKE_IGNORE_CASE:
                restrictionCriteria.likeIgnoreCase(attribute, value.toString());
                break;
            case NOT_LIKE:
                restrictionCriteria.notLike(attribute, value.toString());
                break;
            case NOT_LIKE_IGNORE_CASE:
                restrictionCriteria.notLikeIgnoreCase(attribute, value.toString());
                break;
            case IS_EMPTY:
                restrictionCriteria.empty(attribute);
                break;
            case NOT_EMPTY:
                restrictionCriteria.notEmpty(attribute);
                break;
        }
        return restrictionCriteria;
    }

    private boolean isOrActivated() {
        return orEnabled;
    }

    private void resetRestrictions() {
        orEnabled = false;
        criteriaRestrictions.clear();
    }

    private void activateOrClause() {
        orEnabled = true;
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
