package com.github.quarkus.criteria.service;

import com.github.quarkus.criteria.model.*;
import com.github.quarkus.criteria.runtime.criteria.BaseCriteriaSupport;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
class CarCriteria extends BaseCriteriaSupport<Car> {
    /**
     * getEntityManager().createQuery("SELECT SUM(c.price) FROM Car c WHERE upper(c.model) like :model", Double.class)
     *                 .setParameter("model", model).getSingleResult();
     */
    public Double getTotalPriceByModel(String model) {
        return criteria()
                  .select(Double.class, sum(Car_.price))
                .likeIgnoreCase(Car_.model, model)
                .getSingleResult();
    }
}
