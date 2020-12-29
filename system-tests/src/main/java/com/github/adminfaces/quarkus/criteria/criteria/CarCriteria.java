package com.github.adminfaces.quarkus.criteria.criteria;

import com.github.adminfaces.quarkus.criteria.model.*;
import com.github.adminfaces.quarkus.criteria.runtime.criteria.BaseCriteriaSupport;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.criteria.JoinType;
import java.util.List;

@ApplicationScoped
public class CarCriteria extends BaseCriteriaSupport<Car> {
    /**
     * getEntityManager().createQuery("SELECT SUM(c.price) FROM Car c WHERE upper(c.model) like :model", Double.class)
     *                 .setParameter("model", model).getSingleResult();
     */
    public Double getTotalPriceByModel(String model) {
        return criteria()
                  .select(Double.class, sum(Car_.price))
                .eqIgnoreCase(Car_.model, model)
                .getSingleResult();
    }
}
