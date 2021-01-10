package com.github.quarkus.criteria.service;

import com.github.quarkus.criteria.model.*;
import com.github.quarkus.criteria.runtime.model.Filter;
import com.github.quarkus.criteria.runtime.service.CrudService;
import org.apache.deltaspike.data.api.criteria.Criteria;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.persistence.criteria.JoinType;
import javax.transaction.Transactional;
import java.io.Serializable;
import java.util.List;

import static com.github.quarkus.criteria.runtime.util.CriteriaUtils.toListOfIds;

/**
 * @author rmpestano
 */
@Transactional(Transactional.TxType.SUPPORTS)
@Dependent
public class CarService extends CrudService<Car> implements Serializable {

    @Inject
    CarCriteria carCriteria;//you can create repositories to extract complex queries from your service


    /**
     * This method is invoked before pagination so you can have more complex restrictions
     *
     * @param filter
     * @return
     */
    protected Criteria<Car, Car> configPagination(Filter<Car> filter) {

        final Criteria<Car, Car> criteria = criteria()
                .fetch(Car_.brand)
                .distinct();

        //create restrictions based on parameters map
        if (filter.hasParam("id")) {
            criteria.eq(Car_.id, filter.getIntParam("id"));
        }

        if (filter.hasParam("minPrice") && filter.hasParam("maxPrice")) {
            criteria.between(Car_.price, filter.getDoubleParam("minPrice"), filter.getDoubleParam("maxPrice"));
        } else if (filter.hasParam("minPrice")) {
            criteria.gtOrEq(Car_.price, filter.getDoubleParam("minPrice"));
        } else if (filter.hasParam("maxPrice")) {
            criteria.ltOrEq(Car_.price, filter.getDoubleParam("maxPrice"));
        }

        //create restrictions based on filter entity
        if (filter.getEntity() != null) {
            Car filterEntity = filter.getEntity();
            if (filterEntity.hasModel()) {
                criteria.likeIgnoreCase(Car_.model, "%" + filterEntity.getModel());
            }
            if (filterEntity.getPrice() != null) {
                criteria.eq(Car_.price, filterEntity.getPrice());
            }
            if (filterEntity.hasName()) {
                criteria.likeIgnoreCase(Car_.name, "%" + filterEntity.getName() + "%");
            }
        }
        return criteria;
    }

    public void beforeInsert(Car car) {
        validate(car);
    }

    public void beforeUpdate(Car car) {
        if(count(criteria()
            .eq(Car_.id, car.getId())) == 0) {
            throw new RuntimeException("Cannot update inexisting car.");
        }
        validate(car);
    }

    public void validate(Car car) {
        if (!car.hasModel()) {
            throw new RuntimeException("Car model cannot be empty");
        }
        if (!car.hasName()) {
            throw new RuntimeException("Car name cannot be empty");
        }
        if (car.getPrice() == null) {
            throw new RuntimeException("Car price cannot be empty");
        }
        if (count(criteria()
                .eqIgnoreCase(Car_.name, car.getName())
                .notEq(Car_.id, car.getId())) > 0) {
            throw new RuntimeException("Car name must be unique");
        }
    }


    @Override
    public void beforeDelete(Car car) {
        if(car.getCarSalesPoints() != null && !car.getCarSalesPoints().isEmpty()) {
            car.getCarSalesPoints()
                    .stream()
                    .forEach(carSalesPoint -> entityManager
                            .remove(entityManager.getReference(CarSalesPoint.class, carSalesPoint.getId())));
        }
        super.beforeDelete(car);
    }

    public List<Car> listByModel(String model) {
        return criteria()
                .likeIgnoreCase(Car_.model, model)
                .getResultList();
    }

    public List<String> getModels(String query) {
        return criteria()
                .select(String.class, attribute(Car_.model))
                .likeIgnoreCase(Car_.model, "%" + query + "%")
                .getResultList();
    }

    public Double getTotalPriceByModel(Car car) {
        if (!car.hasModel()) {
            throw new RuntimeException("Provide car model to get the total price.");
        }
        return carCriteria.getTotalPriceByModel(car.getModel().toUpperCase());
    }

    public List<Car> findBySalesPointAddress(String address) {
        return criteria().join(Car_.carSalesPoints, where(CarSalesPoint.class, JoinType.INNER)
                .join(CarSalesPoint_.salesPoint, where(SalesPoint.class, JoinType.LEFT)
                        .likeIgnoreCase(SalesPoint_.address, "%" + address + "%")))
                .getResultList();
    }

    public List<Car> findBySalesPoint(SalesPoint salesPoint) {
        return criteria()
                .fetch(Car_.brand, JoinType.INNER)
                .join(Car_.carSalesPoints, where(CarSalesPoint.class, JoinType.INNER)
                    .join(CarSalesPoint_.salesPoint, where(SalesPoint.class, JoinType.LEFT)
                        .eq(SalesPoint_.salesPointPK, salesPoint.getSalesPointPK())))
                .getResultList();
    }

    public List<Car> findCarsInList(List<Car> carsToFind) {
        return criteria().in(Car_.id, toListOfIds(carsToFind, new Integer[0])).getResultList();
    }

    //just to test criteria on a different entity
    public List<SalesPoint> listSalesPointsByName(String name) {
        return criteria(SalesPoint.class)
                .likeIgnoreCase(SalesPoint_.name, "%" + name + "%")
                .getResultList();
    }

    public List<CarWithNameAndPrice> carsProjection() {
        return criteria()
                .select(CarWithNameAndPrice.class, attribute(Car_.name), attribute(Car_.price))
                .join(Car_.brand, where(Brand.class)
                        .or(criteria(Brand.class)
                                        .eq(Brand_.name, "Nissan"),
                                criteria(Brand.class).eq(Brand_.name, "Tesla")))
                .join(Car_.carSalesPoints, where(CarSalesPoint.class)
                        .join(CarSalesPoint_.salesPoint, where(SalesPoint.class, JoinType.LEFT)
                        .likeIgnoreCase(SalesPoint_.name, "%Tesla%")))
                .getResultList();
    }

    public List<Car> findByBrand(Brand brand) {
        return criteria()
             .eq(Car_.brand, brand)
             .getResultList();
    }
}
