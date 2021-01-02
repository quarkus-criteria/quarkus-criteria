package com.github.adminfaces.quarkus.criteria.runtime.criteria;

import com.github.adminfaces.quarkus.criteria.runtime.model.PersistenceEntity;
import org.apache.deltaspike.data.api.criteria.Criteria;
import org.apache.deltaspike.data.impl.criteria.QueryCriteria;

import javax.persistence.EntityManager;
import javax.persistence.criteria.JoinType;
import javax.persistence.metamodel.*;
import javax.print.DocFlavor;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * @param <T> the example entity type
 * @author rmpestano
 */
public class ExampleBuilder<T extends PersistenceEntity> {
    private static final Logger LOG = Logger.getLogger(ExampleBuilder.class.getName());

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
        private Criteria<T, ?> criteria;
        private T example;
        private boolean fetch;
        private Set<Attribute<?, ?>> exampleAttributes = new HashSet<>();
        private ExampleBuilder<T> exampleBuilder;

        public ExampleBuilderDsl(T example, ExampleBuilder exampleBuilder) {
            this.exampleBuilder = exampleBuilder;
            this.example = example;
            if (example == null) {
                throw new IllegalArgumentException("Example instance should not be null");
            }
            this.criteria = new QueryCriteria(example.getClass(), example.getClass(), exampleBuilder.entityManager);
        }

        public Criteria<T, ?> build() {
            return criteria;
        }

        public ExampleBuilderDsl usingCriteria(Criteria<T, ?> criteria) {
            this.criteria = criteria;
            return this;
        }

        /**
         * @param fetch when <code>true</code> associations will be fetched in result mapping. Default is false.
         */
        public ExampleBuilderDsl usingFetch(boolean fetch) {
            this.fetch = fetch;
            return this;
        }


        /**
         * A 'criteria by example' will be created using the example entity and it's non null attributes to restrict the query.
         * It will use <code>eq</code> for comparing 'simple' attributes, for <code>oneToOne</code> associations the entity
         * PK will be compared and for oneToMany association an <code>in</code> for comparing associated entities PKs.
         */
        public ExampleBuilderDsl example() {
            return example(resolveNonNullEntityAttributes(example).toArray(new Attribute[0]));
        }

        /**
         * This example criteria will add restrictions to an existing criteria based on the example entity. It will use <code>eq</code> for comparing 'simple' attributes,
         * for <code>oneToOne</code> associations the entity PK will be compared and for oneToMany association an <code>in</code> for comparing associated entities PKs
         *
         * @param usingAttributes attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
         * @throws RuntimeException If no attribute is provided.
         */
        public ExampleBuilderDsl example(final Attribute<T, ?>... usingAttributes) {
            addEntityAttributes(usingAttributes);
            for (Attribute<T, ?> usingAttribute : usingAttributes) {
                if (usingAttribute instanceof SingularAttribute) {
                    addEqExampleRestriction(usingAttribute);
                } else if (usingAttribute instanceof PluralAttribute) {
                    addInExampleRestriction(usingAttribute);
                }
            }
            return this;
        }


        /**
         * A 'criteria by example' will be created using the example entity. ONLY <code>String</code> attributes will be considered.
         * It will use 'likeIgnoreCase' for comparing STRING attributes of the example entity.
         *
         * @param usingAttributes attributes from example entity to consider. If no attribute is provided then non null String attributes will be used.
         * @return A criteria restricted by example using <code>likeIgnoreCase</code> for comparing attributes
         */
        public ExampleBuilderDsl exampleLike(SingularAttribute<T, String>... usingAttributes) {
            if (usingAttributes == null || usingAttributes.length == 0) {
                usingAttributes = resolveEntitySingularStringAttributes();
            }
            addEntityAttributes(usingAttributes);
            exampleAttributes.stream()
                    .filter(attr -> attr instanceof SingularAttribute)
                    .forEach(attr -> {
                        SingularAttribute<T, String> attribute = (SingularAttribute<T, String>) attr;
                        if (attribute.getJavaMember() instanceof Field) {
                            Field field = (Field) attribute.getJavaMember();
                            field.setAccessible(true);
                            try {
                                Object value = field.get(example);
                                if (value != null) {
                                    LOG.fine(format("Adding restriction by example on attribute %s using value %s.", attribute.getName(), value));
                                    criteria.likeIgnoreCase(attribute, value.toString());
                                }
                            } catch (IllegalAccessException e) {
                                LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
                            }
                        }
                    });

            return this;
        }

        private void addEqExampleRestriction(Attribute<T, ?> attribute) {
            if (attribute.getJavaMember() instanceof Field) {
                Field field = (Field) attribute.getJavaMember();
                field.setAccessible(true);
                try {
                    Object value = field.get(example);
                    if (value != null) {
                        LOG.fine(format("Adding an 'eq' restriction on attribute %s using value %s.", attribute.getName(), value));
                        if (fetch) {
                            criteria.fetch((SingularAttribute) attribute, JoinType.INNER);
                        }
                        criteria.eq((SingularAttribute) attribute, value);
                    }
                } catch (IllegalAccessException e) {
                    LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
                }
            }
        }

        private void addInExampleRestriction(final Attribute<T, ?> attribute) {
            final PluralAttribute<T, ?, ?> listAttribute = (PluralAttribute<T, ?, ?>) attribute;
            Class joinClass = listAttribute.getElementType().getJavaType();
            Criteria joinCriteria = new QueryCriteria(joinClass, example.getClass(), exampleBuilder.entityManager, JoinType.LEFT);
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
            if (attribute.getJavaMember() instanceof Field) {
                Field field = (Field) attribute.getJavaMember();
                field.setAccessible(true);
                try {
                    Object value = field.get(example);
                    if (value != null) {
                        LOG.fine(format("Adding an 'in'restriction on attribute %s using value %s.", attribute.getName(), value));
                        Collection<PersistenceEntity> association = (Collection<PersistenceEntity>) value;
                        SingularAttribute id = exampleBuilder.entityManager.getMetamodel().entity(listAttribute.getElementType().getJavaType()).getId(association.iterator().next().getId().getClass());
                        List<Serializable> ids = new ArrayList<>();
                        for (PersistenceEntity persistenceEntity : association) {
                            ids.add(persistenceEntity.getId());
                        }

                        joinCriteria.in(id, ids);
                    }
                } catch (IllegalAccessException e) {
                    LOG.warning(format("Could not get value from field %s of entity %s.", field.getName(), example.getClass().getName()));
                }
            }
        }

        private void addEntityAttributes(Attribute<T, ?>[] usingAttributes) {
            if (exampleAttributes == null) {
                exampleAttributes = new HashSet<>();
            }
            for (Attribute<T, ?> usingAttribute : usingAttributes) {
                exampleAttributes.add(usingAttribute);
            }
        }

        private Set<Attribute<?, ?>> resolveNonNullEntityAttributes(T example) {
            Set<Attribute<?, ?>> attributes = (Set<Attribute<?, ?>>) exampleBuilder.entityManager.getMetamodel().entity(example.getClass()).getAttributes();
            if (attributes == null) {
                attributes = Collections.emptySet();
            }
            return attributes;
        }

        private SingularAttribute<T, String>[] resolveEntitySingularStringAttributes() {
            Set<SingularAttribute<?, ?>> singularAttributes = (Set<SingularAttribute<?, ?>>) exampleBuilder.entityManager.getMetamodel().entity(example.getClass()).getSingularAttributes();
            List<SingularAttribute<T, String>> stringAttributes = new ArrayList<>();
            if (singularAttributes != null && !singularAttributes.isEmpty()) {
                singularAttributes.stream()
                        .filter(attr -> attr.getType().getJavaType().isAssignableFrom(String.class))
                        .forEach(attr -> stringAttributes.add((SingularAttribute<T, String>) attr));
            }
            return stringAttributes.toArray(new SingularAttribute[0]);
        }

    }

}
