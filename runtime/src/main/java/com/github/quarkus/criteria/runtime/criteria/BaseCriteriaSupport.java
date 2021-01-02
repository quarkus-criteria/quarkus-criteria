package com.github.quarkus.criteria.runtime.criteria;

import com.github.quarkus.criteria.runtime.model.PersistenceEntity;
import org.apache.deltaspike.data.api.criteria.Criteria;
import org.apache.deltaspike.data.api.criteria.CriteriaSupport;
import org.apache.deltaspike.data.impl.criteria.QueryCriteria;
import org.apache.deltaspike.data.impl.handler.CriteriaSupportHandler;

import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.SingularAttribute;
import javax.transaction.Transactional;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

import static java.lang.String.format;

@Dependent
@Transactional(Transactional.TxType.SUPPORTS)
public class BaseCriteriaSupport<T extends PersistenceEntity> extends CriteriaSupportHandler<T> implements CriteriaSupport<T>, Serializable {

    @Inject
    protected EntityManager entityManager;
    protected Class<T> entityClass;
    protected Class<Serializable> entityKey;
    public ExampleBuilder<T> exampleBuilder;

    @PostConstruct
    @ActivateRequestContext
    public void init() {
        resolveEntityClass();
        exampleBuilder = new ExampleBuilder<>(entityManager);
    }

    protected void resolveEntityClass() {
        if (entityClass == null) {
            ParameterizedType type = getParameterizedType();
            Type[] typeArgs = type.getActualTypeArguments();
            entityClass = (Class<T>) typeArgs[0];
        }
        if (entityKey == null) {
            entityKey = resolveEntityKey(entityClass);
        }
    }

    protected ParameterizedType getParameterizedType() {
        ParameterizedType parameterizedType;
        if (getClass().getGenericSuperclass() instanceof ParameterizedType) {
            parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        } else if (getClass().getSuperclass().getGenericSuperclass() instanceof ParameterizedType) {
            parameterizedType = (ParameterizedType) getClass().getSuperclass().getGenericSuperclass();
        } else {
            parameterizedType = (ParameterizedType) getClass().getSuperclass().getSuperclass().getGenericSuperclass();
        }
        return parameterizedType;
    }

    /**
     * @param entityClass
     * @return a criteria for underlying entityClass
     */
    public <E extends PersistenceEntity> Criteria<E, E> criteria(Class<E> entityClass) {
        return new QueryCriteria<>(entityClass, entityClass, getEntityManager());
    }

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    public Class<Serializable> getEntityKey() {
        return entityKey;
    }

    private Class resolveEntityKey(Class entityClass) {
        final Metamodel metamodel = getEntityManager().getMetamodel();
        final EntityType entity = metamodel.entity(entityClass);
        final Set<SingularAttribute> singularAttributes = entity.getSingularAttributes();
        for (SingularAttribute singularAttribute : singularAttributes) {
            if (singularAttribute.isId()) {
                return singularAttribute.getJavaType();
            }
        }
        throw new RuntimeException(format("Id property not found for entity %s", entityClass));
    }
}
