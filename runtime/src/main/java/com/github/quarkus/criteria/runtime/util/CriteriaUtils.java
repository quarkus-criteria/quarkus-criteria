package com.github.quarkus.criteria.runtime.util;

import com.github.quarkus.criteria.runtime.model.PersistenceEntity;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CriteriaUtils {
    /**
     * Creates an array of Ids (pks) from a list of entities.
     * It is useful when working with `in clauses` on DeltaSpike criteria
     * because the API only support primitive arrays.
     *
     * @param entities list of entities to create
     * @param idsType  the type of the pk list, e.g new Long[0]
     * @return primitive array containing entities pks.
     */
    public static <ID extends Serializable> ID[] toListOfIds(Collection<? extends PersistenceEntity> entities, ID[] idsType) {
        Set<ID> ids = new HashSet<>();
        for (PersistenceEntity entity : entities) {
            ids.add(entity.getId());
        }
        return ids.toArray(idsType);
    }
}