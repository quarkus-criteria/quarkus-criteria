package com.github.quarkus.criteria.runtime.service;

import com.github.quarkus.criteria.runtime.criteria.BaseCriteriaSupport;
import com.github.quarkus.criteria.runtime.model.Filter;
import com.github.quarkus.criteria.runtime.model.MultiSort;
import com.github.quarkus.criteria.runtime.model.PersistenceEntity;
import com.github.quarkus.criteria.runtime.model.Sort;
import org.apache.deltaspike.data.api.criteria.Criteria;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.metamodel.*;
import javax.transaction.Transactional;
import java.io.Serializable;
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
            try {  //Used for generic service injection, e.g: @Inject @Service CrudService<Entity>
                ParameterizedType type = (ParameterizedType) ip.getType();
                Type[] typeArgs = type.getActualTypeArguments();
                entityClass = (Class<T>) typeArgs[0];
            } catch (Exception e) {
            }
        }
    }

    /**
     * @param filter Contains pagination configuration
     * @return A list based on pagination filter
     */
    public List<T> paginate(Filter<T> filter) {
        validateFilter(filter);
        Criteria<T, T> criteria = configRestrictions(filter);
        configSort(filter, criteria);
        return criteria.createQuery()
                .setFirstResult(filter.getFirst())
                .setMaxResults(filter.getPageSize())
                .getResultList();
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

    protected void configSort(Filter<T> filter, Criteria<T, T> criteria) {
        if (!filter.getMultiSort().isEmpty()) { //multi sort
            for (MultiSort multiSort : filter.getMultiSort()) {
                addSort(criteria, multiSort.getSort(), multiSort.getSortField());
            }
        } else { //single field sort
            addSort(criteria, filter.getSort(), filter.getSortField());
        }
    }

    /**
     * Called before pagination, should be overridden. By default there is no restrictions.
     *
     * @param filter used to create restrictions
     * @return a criteria with configured restrictions
     */
    protected Criteria<T, T> configRestrictions(Filter<T> filter) {
        return criteria();
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
            ids.add(entity.getId());
        }
        return ids.toArray(idsType);
    }

    protected void addSort(Criteria<T, T> criteria, Sort sort, String sortField) {
        if (sortField != null) {
            SingularAttribute sortAttribute = getEntityManager().getMetamodel().entity(entityClass).getSingularAttribute(sortField);
            if (sort.equals(Sort.UNSORTED)) {
                sort = Sort.ASCENDING;
            }
            if (sort.equals(Sort.ASCENDING)) {
                criteria.orderAsc(sortAttribute);
            } else {
                criteria.orderDesc(sortAttribute);
            }
        }
    }

    private void validateFilter(Filter<T> filter) {
        if(filter == null) {
            throw new RuntimeException("Pagination filter should be provided.");
        }
    }

    private Set<Serializable> collectEntitiesPk(List<T> entities) {
        return entities.stream()
                .map(e -> (Serializable) e.getId())
                .collect(Collectors.toSet());
    }
}
