package com.github.quarkus.criteria.runtime.service;

import com.github.quarkus.criteria.runtime.criteria.BaseCriteriaSupport;
import com.github.quarkus.criteria.runtime.model.Filter;
import com.github.quarkus.criteria.runtime.model.MultiSort;
import com.github.quarkus.criteria.runtime.model.PersistenceEntity;
import com.github.quarkus.criteria.runtime.model.SortType;
import org.apache.deltaspike.data.api.criteria.Criteria;
import org.jboss.logmanager.Level;
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
 * Enables CRUD operations using fluent and type safe criteria on top of the underlying class
 *
 * @author rmpestano
 * @param <T> The entity type to CRUD
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
        Criteria<T, T> criteria = configPagination(filter);
        configSort(filter, criteria);
        return criteria.createQuery()
                .setFirstResult(filter.getFirst())
                .setMaxResults(filter.getPageSize())
                .getResultList();
    }

    @Transactional
    public T insert(T entity) {
        if (entity == null) {
            throw new RuntimeException("Record cannot be null");
        }
        beforeAll(entity);
        beforeInsert(entity);
        entityManager.persist(entity);
        entityManager.flush();
        afterInsert(entity);
        afterAll(entity);
        return entity;
    }

    @Transactional
    public void deleteById(Serializable id) {
        Optional.ofNullable(entityManager.getReference(entityClass, id)).ifPresent(this::delete);
    }

    @Transactional
    public void delete(T entity) {
        if (entity == null) {
            throw new RuntimeException("Record cannot be null");
        }
        if (entity.getId() == null) {
            throw new RuntimeException("Record cannot be transient");
        }
        beforeAll(entity);
        beforeDelete(entity);
        if (!entityManager.contains(entity)) {
            entity = entityManager.find(entityClass, entity.getId());
        }
        entityManager.remove(entity);
        afterDelete(entity);
        afterAll(entity);
    }

    @Transactional
    public void delete(List<T> entities) {
        if (entities == null) {
            throw new RuntimeException("Entities cannot be null");
        }
        for (T t : entities) {
            this.delete(t);
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
    public int deleteBatch(List<T> entities, Integer batchSize) {
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
            LOG.log(Level.DEBUG, "Removing batch: " + currentBatch);
            int batchStart = batchSize * i;
            int batchEnd = batchStart + batchSize;
            if (batchEnd > total) {
                batchEnd = total;
            }
            List<T> entitiesBatch = entities.subList(batchStart, batchEnd);
            Set<Serializable> pks = collectEntitiesPk(entitiesBatch);
            if(pks.size() == 0) {
                LOG.warning(format("Skipping batch %d because no primary keys were found in entities to delete.", currentBatch));
                continue;
            }
            final int entitiesDeleted = getEntityManager().createQuery("DELETE from " + entityClass.getSimpleName() + " e WHERE e." + idFieldName + " IN :ids")
                    .setParameter("ids", pks).executeUpdate();
            LOG.log(Level.DEBUG, format("Entities removed in batch %d: %d ", i, entitiesDeleted));
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

    public List<T> list() {
        return criteria().getResultList();
    }

    /**
     * Count all
     */
    public Long count() {
        return count(criteria());
    }

    /**
     * Count by filter using {@link CrudService#configPagination(Filter)} to count
     *
     * @param filter
     * @return
     */
    public Long count(Filter<T> filter) {
        return count(configPagination(filter));
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

    public void beforeDelete(T entity) {
    }

    public void afterDelete(T entity) {
    }

    public void afterAll(T entity) {
    }

    protected void configSort(Filter<T> filter, Criteria<T, T> criteria) {
        if (!filter.getMultiSort().isEmpty()) { //multi sort
            for (MultiSort multiSort : filter.getMultiSort()) {
                addSort(criteria, multiSort.getSort(), multiSort.getSortField());
            }
        } else { //single field sort
            addSort(criteria, filter.getSortType(), filter.getSortField());
        }
    }

    /**
     * Called before pagination, should be overridden. By default there is no restrictions when paging.
     *
     * @param filter used to create restrictions
     * @return a criteria with configured restrictions
     */
    protected Criteria<T, T> configPagination(Filter<T> filter) {
        return criteria();
    }


    protected void addSort(Criteria<T, T> criteria, SortType sort, String sortField) {
        if (sortField != null) {
            SingularAttribute sortAttribute = getEntityManager().getMetamodel().entity(entityClass).getSingularAttribute(sortField);
            if (sort.equals(SortType.UNSORTED)) {
                sort = SortType.ASCENDING;
            }
            if (sort.equals(SortType.ASCENDING)) {
                criteria.orderAsc(sortAttribute);
            } else {
                criteria.orderDesc(sortAttribute);
            }
        }
    }

    protected void validateFilter(Filter<T> filter) {
        if(filter == null) {
            throw new RuntimeException("Pagination filter should be provided.");
        }
    }

    private Set<Serializable> collectEntitiesPk(List<T> entities) {
        if(entities == null || entities.isEmpty()) {
            return Collections.emptySet();
        }
        return entities.stream()
                .map(e -> (Serializable) e.getId())
                .collect(Collectors.toSet());
    }
}