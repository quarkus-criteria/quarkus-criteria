package com.github.adminfaces.quarkus.criteria.runtime.service;

import com.github.adminfaces.quarkus.criteria.runtime.criteria.BaseCriteriaSupport;
import com.github.adminfaces.quarkus.criteria.runtime.model.Filter;
import com.github.adminfaces.quarkus.criteria.runtime.model.MultiSort;
import com.github.adminfaces.quarkus.criteria.runtime.model.PersistenceEntity;
import com.github.adminfaces.quarkus.criteria.runtime.model.Sort;
import org.apache.deltaspike.data.api.criteria.Criteria;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.criteria.JoinType;
import javax.persistence.metamodel.*;
import javax.transaction.Transactional;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * @author rmpestano
 * Utility service for crud operations
 */
@Service
@Dependent
@Transactional(Transactional.TxType.SUPPORTS)
public class CrudService<T extends PersistenceEntity> extends BaseCriteriaSupport<T> {

    private static final Logger LOG = Logger.getLogger(CrudService.class.getName());
    private static final int DEFAULT_REMOVAL_BATCH_SIZE = 1000;

    @Inject
    protected void CrudService(InjectionPoint ip) {
        if (ip != null && ip.getType() != null && ip.getMember() != null) {
            try {
                //Used for generic service injection, e.g: @Inject @Service CrudService<Entity,Key>
                resolveEntity(ip);
            } catch (Exception e) {
            }
        }
        if (entityClass == null) {
            //Used on service inheritance, e.g: MyService extends CrudService<Entity, Key>
            ParameterizedType parameterizedType = getParameterizedType();
            entityClass = (Class<T>) parameterizedType.getActualTypeArguments()[0];
        }
    }

    private void resolveEntity(InjectionPoint ip) {
        ParameterizedType type = (ParameterizedType) ip.getType();
        Type[] typeArgs = type.getActualTypeArguments();
        entityClass = (Class<T>) typeArgs[0];
    }

    public List<T> paginate(Filter<T> filter) {
        Criteria<T, T> criteria = configRestrictions(filter);
        configSort(filter, criteria);
        return criteria.createQuery()
                .setFirstResult(filter.getFirst())
                .setMaxResults(filter.getPageSize())
                .getResultList();
    }

    protected void configSort(Filter<T> filter, Criteria<T, T> criteria) {
        if (!filter.getMultiSort().isEmpty()) { //multi sort
            for (MultiSort adminMultiSort : filter.getMultiSort()) {
                addSort(criteria, adminMultiSort.getAdminSort(), adminMultiSort.getSortField());
            }
        } else { //single field sort 
            addSort(criteria, filter.getAdminSort(), filter.getSortField());
        }
    }

    /**
     * Called before pagination, should be overriden. By default there is no restrictions.
     *
     * @param filter used to create restrictions
     * @return a criteria with configured restrictions
     */
    protected Criteria<T, T> configRestrictions(Filter<T> filter) {
        return criteria();
    }


    @Transactional
    public void insert(T entity) {
        if (entity == null) {
            throw new RuntimeException("Record cannot be null");
        }
        beforeAll(entity);
        beforeInsert(entity);
        entityManager.persist(entity);
        afterInsert(entity);
        afterAll(entity);
    }

    @Transactional
    public void remove(T entity) {
        if (entity == null) {
            throw new RuntimeException("Record cannot be null");
        }

        if (entity.getId() == null) {
            throw new RuntimeException("Record cannot be transient");
        }
        beforeAll(entity);
        beforeRemove(entity);
        if (!entityManager.contains(entity)) {
            entity = entityManager.find(entityClass, entity.getId());
        }
        entityManager.remove(entity);
        afterRemove(entity);
        afterAll(entity);
    }

    @Transactional
    public void remove(List<T> entities) {
        if (entities == null) {
            throw new RuntimeException("Entities cannot be null");
        }
        for (T t : entities) {
            this.remove(t);
        }
    }

    /**
     * Remove entities in batches
     *
     * @param entities
     * @param batchSize
     * @return number of deleted entities
     */
    @Transactional
    public int removeBatch(List<T> entities, Integer batchSize) {
        if (batchSize == null || batchSize < 1) {
            LOG.warning("Invalid batch size to remove entities, using default batch size: " + DEFAULT_REMOVAL_BATCH_SIZE);
            batchSize = DEFAULT_REMOVAL_BATCH_SIZE;
        }
        if (entities == null) {
            throw new RuntimeException("Entities cannot be null");
        }
        int removedEntitiesCount = 0;
        final String idFieldName = getEntityManager().getMetamodel().entity(entityClass).getId(entityKey).getName();
        final int total = entities.size();
        final int batches = (int) Math.ceil((double)total / batchSize);
        for (int i = 0; i < batches; i++) {
            int currentBatch = i+1;
            LOG.info("Removing batch: " + currentBatch);
            int batchStart = batchSize * i;
            int batchEnd = batchStart + batchSize;
            if (batchEnd > total) {
                batchEnd = total;
            }
            List<T> entitiesBatch = entities.subList(batchStart, batchEnd);
            Set<Serializable> pks = collectEntitiesPk(entitiesBatch);
            final int entitiesDeleted = getEntityManager().createQuery("DELETE from " + entityClass.getSimpleName() + " e WHERE e." + idFieldName + " IN :ids")
                    .setParameter("ids", pks).executeUpdate();
            LOG.info(format("Entities removed in batch %d: %d ", i, entitiesDeleted));
            removedEntitiesCount += entitiesDeleted;
        }
        return removedEntitiesCount;
    }

    private Set<Serializable> collectEntitiesPk(List<T> entities) {
        return entities.stream()
                .map(e -> (Serializable) e.getId())
                .collect(Collectors.toSet());
    }

    @Transactional
    public T update(T entity) {
        if (entity == null) {
            throw new RuntimeException("Record cannot be null");
        }
        if (entity.getId() == null) {
            throw new RuntimeException("Record cannot be transient");
        }
        beforeAll(entity);
        beforeUpdate(entity);
        entity = entityManager.merge(entity);
        entityManager.flush();
        afterUpdate(entity);
        afterAll(entity);
        return entity;
    }

    @Transactional
    public T saveOrUpdate(T entity) {
        if (entity == null) {
            throw new RuntimeException("Record cannot be null");
        }
        if (entity.getId() == null) {
            insert(entity);
        } else {
            entity = update(entity);
        }

        return entity;
    }

    /**
     * Count all
     */
    public Long count() {
        return count(criteria());
    }

    /**
     * Count by filter using configRestrictions to count
     *
     * @param filter
     * @return
     */
    public Long count(Filter<T> filter) {
        return count(configRestrictions(filter));
    }

    /**
     * Count using a pre populated criteria
     *
     * @param criteria
     * @return
     */
    public Long count(Criteria<T, T> criteria) {
        SingularAttribute<? super T, Serializable> id = getEntityManager().getMetamodel().entity(entityClass).getId(entityKey);
        return criteria.select(Long.class, countDistinct(id))
                .getSingleResult();
    }

    public T findById(Serializable id) {
        T entity = entityManager.find(entityClass, id);
        if (entity == null) {
            LOG.warning(format("Record with id %s not found for entity %s.", id, entityClass.getName()));
        }
        return entity;
    }

    /**
     * A 'criteria by example' will be created using an example entity. It will use <code>eq</code> for comparing 'simple' attributes,
     * for <code>oneToOne</code> associations the entity PK will be compared and for oneToMany association an <code>in</code> for comparing associated entities PKs.
     *
     * @param example         An entity whose attribute's value will be used for creating a criteria
     * @param usingAttributes attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
     * @return A criteria restricted by example.
     * @throws RuntimeException If no attribute is provided.
     */
    public Criteria example(T example, Attribute<T, ?>... usingAttributes) {
        return example(criteria(), example, usingAttributes);
    }

    public Criteria example(T example, boolean fetch, Attribute<T, ?>... usingAttributes) {
        return example(criteria(), example, fetch, usingAttributes);
    }

    public Criteria example(Criteria criteria, T example, Attribute<T, ?>... usingAttributes) {
        return example(criteria, example, false, usingAttributes);
    }

    /**
     * This example criteria will add restrictions to an existing criteria based on an example entity. It will use <code>eq</code> for comparing 'simple' attributes,
     * for <code>oneToOne</code> associations the entity PK will be compared and for oneToMany association an <code>in</code> for comparing associated entities PKs
     *
     * @param criteria        a criteria to add restrictions based on the example entity.
     * @param example         An entity whose attribute's value will be used for creating a criteria
     * @param fetch           If true, associations will be fetched in result mapping
     * @param usingAttributes attributes from example entity to consider. If no attribute is provided then non null attributes will be used.
     * @return A criteria restricted by example.
     * @throws RuntimeException If no attribute is provided.
     */
    public Criteria example(Criteria criteria, T example, boolean fetch, Attribute<T, ?>... usingAttributes) {
        if (criteria == null) {
            criteria = criteria();
        }
        if (usingAttributes == null || usingAttributes.length == 0) {
            usingAttributes = resolveEntityAttributes(example);
        }
        for (Attribute<T, ?> usingAttribute : usingAttributes) {
            if (usingAttribute instanceof SingularAttribute) {
                addEqExampleRestriction(criteria, example, fetch, usingAttribute);
            } else if (usingAttribute instanceof PluralAttribute) {
                addInExampleRestriction(criteria, example, fetch, usingAttribute);
            }
        }
        return criteria;
    }

    private Attribute<T, ?>[] resolveEntityAttributes(T example) {
        Set<Attribute<?, ?>> attributes = (Set<Attribute<?, ?>>) getEntityManager().getMetamodel().entity(example.getClass()).getAttributes();
        if (attributes != null && !attributes.isEmpty()) {
            return attributes.toArray(new Attribute[0]);
        }
        return Collections.emptyList().toArray(new Attribute[0]);
    }

    private SingularAttribute<T, String>[] resolveEntitySingularStringAttributes(T example) {
        Set<SingularAttribute<?, ?>> singularAttributes = (Set<SingularAttribute<?, ?>>) getEntityManager().getMetamodel().entity(example.getClass()).getSingularAttributes();
        List<SingularAttribute<T, String>> stringAttributes = new ArrayList<>();
        if (singularAttributes != null && !singularAttributes.isEmpty()) {
            for (SingularAttribute<?, ?> singularAttribute : singularAttributes) {
                if (singularAttribute.getType().getJavaType().isAssignableFrom(String.class)) {
                    stringAttributes.add((SingularAttribute<T, String>) singularAttribute);
                }
            }
        }
        return stringAttributes.toArray(new SingularAttribute[0]);
    }


    private void addEqExampleRestriction(Criteria criteria, T example, boolean fetch, Attribute<T, ?> attribute) {
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

    private void addInExampleRestriction(Criteria criteria, T example, boolean fetch, Attribute<T, ?> attribute) {
        PluralAttribute<T, ?, ?> listAttribute = (PluralAttribute<T, ?, ?>) attribute;
        Class joinClass = listAttribute.getElementType().getJavaType();
        Criteria joinCriteria = where(joinClass, JoinType.LEFT);
        if(fetch) {
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
                    SingularAttribute id = getEntityManager().getMetamodel().entity(listAttribute.getElementType().getJavaType()).getId(association.iterator().next().getId().getClass());
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


    /**
     * A 'criteria by example' will be created using an example entity. ONLY <code>String</code> attributes will be considered.
     * It will use 'likeIgnoreCase' for comparing STRING attributes of the example entity.
     *
     * @param example         An entity whose attribute's value will be used for creating a criteria
     * @param usingAttributes attributes from example entity to consider. If no attribute is provided then non null String attributes will be used.
     * @return A criteria restricted by example using 'likeIgnoreCase' for comparing attributes
     * @throws RuntimeException If no attribute is provided.
     */
    public Criteria exampleLike(T example, SingularAttribute<T, String>... usingAttributes) {
        return exampleLike(criteria(), example, usingAttributes);
    }

    /**
     * @param criteria        a pre populated criteria to add example based <code>like</code> restrictions
     * @param example         An entity whose attribute's value will be used for creating a criteria
     * @param usingAttributes attributes from example entity to consider. If no attribute is provided then non null String attributes will be used.
     * @return A criteria restricted by example using <code>likeIgnoreCase</code> for comparing attributes
     * @throws RuntimeException If no attribute is provided.
     */
    public Criteria exampleLike(Criteria criteria, T example, SingularAttribute<T, String>... usingAttributes) {

        if (usingAttributes == null || usingAttributes.length == 0) {
            usingAttributes = resolveEntitySingularStringAttributes(example);
        }

        if (criteria == null) {
            criteria = criteria();
        }

        for (SingularAttribute<T, ?> attribute : usingAttributes) {
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
        }
        return criteria;
    }


    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void beforeAll(T entity) {
    }

    public void beforeInsert(T entity) {
    }

    public void afterInsert(T entity) {
    }

    public void beforeUpdate(T entity) {
    }

    public void afterUpdate(T entity) {
    }

    public void beforeRemove(T entity) {
    }

    public void afterRemove(T entity) {
    }

    public void afterAll(T entity) {
    }

    /**
     * Creates an array of Ids (pks) from a list of entities.
     * It is useful when working with `in clauses` on DeltaSpike criteria
     * because the API only support primitive arrays.
     *
     * @param entities list of entities to create
     * @param idsType  the type of the pk list, e.g new Long[0]
     * @return primitive array containing entities pks.
     */
    @SuppressWarnings("unchecked")
    protected <ID extends Serializable> ID[] toListOfIds(Collection<? extends PersistenceEntity> entities, ID[] idsType) {
        Set<ID> ids = new HashSet<>();
        for (PersistenceEntity entity : entities) {
            ids.add((ID) entity.getId());
        }
        return (ID[]) ids.toArray(idsType);
    }

    protected void addSort(Criteria<T, T> criteria, Sort adminSort, String sortField) {
        if (sortField != null) {
            SingularAttribute sortAttribute = getEntityManager().getMetamodel().entity(entityClass).getSingularAttribute(sortField);
            if (adminSort.equals(Sort.UNSORTED)) {
                adminSort = Sort.ASCENDING;
            }
            if (adminSort.equals(Sort.ASCENDING)) {
                criteria.orderAsc(sortAttribute);
            } else {
                criteria.orderDesc(sortAttribute);
            }
        }
    }
}
