package com.github.quarkus.criteria;

import com.github.quarkus.criteria.model.*;
import com.github.quarkus.criteria.runtime.service.CrudService;
import com.github.quarkus.criteria.runtime.service.Service;
import com.github.quarkus.criteria.service.CarService;
import io.quarkus.runtime.QuarkusApplication;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

@ApplicationScoped
public class QuarkusCriteriaApp implements QuarkusApplication {

    @Inject
    CarService carService;

    @Inject
    @Service
    CrudService<Car> carCrud; //generic injection

    @Inject
    @Service
    CrudService<Brand> brandCrud;

    @Inject
    @Service
    CrudService<SalesPoint> salesPointCrud;

    @Override
    @ActivateRequestContext
    public int run(String... args) {
        try {
            List<Brand> brands = insertBrands();
            List<SalesPoint> salesPoints = insertSalesPoints();
            insertCars(brands, salesPoints);
            System.out.println("======================================================================================");
            List<Car> cars = carService.listCars();
            System.out.println("Printing cars containing '1' in model or '2' in name that are from 'Nissan' or 'Tesla' brand and has a salesPoint which name contains 'Tesla'");
            cars.forEach(System.out::println);
            System.out.println("======================================================================================");
            System.out.println("Selecting car model and price and mapping to a DTO");
            List<CarWithNameAndPrice> carsProjection = carService.carsProjection();
            carsProjection.forEach(System.out::println);
            System.out.println("======================================================================================");
            System.out.println("Models names containing '4':  " + getAllModelsContaining4());
            System.out.println("======================================================================================");
            List<Car> carsFound = getCarsByExample();
            System.out.println("Find by cars example: cars that have a sales point named 'Nissan':");
            carsFound.stream().forEach(System.out::println);
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }

    private List<String> getAllModelsContaining4() {
         return carCrud.criteria()
                .select(String.class, carCrud.attribute(Car_.model))
                .likeIgnoreCase(Car_.model, "%4%")
                .getResultList();
    }

    private List<Car> getCarsByExample() {
        Car carExample = new Car();
        SalesPoint salesPoint = salesPointCrud.criteria()
                .eq(SalesPoint_.name, "Nissan")
                .getSingleResult();
        List<SalesPoint> salesPoints = List.of(salesPoint);
        carExample.setSalesPoints(salesPoints);
        return carService
                .exampleBuilder.of(carExample)
                .usingCriteria(carService.criteria()
                        .distinct()
                        .orderAsc(Car_.id))
                .usingFetch(true)
                .example(Car_.salesPoints).build()
                .getResultList();
    }

    private void insertCars(List<Brand> brands, List<SalesPoint> salesPoints) {
        System.out.println("Inserting new cars...");
        IntStream.rangeClosed(1, 50)
                .forEach(i -> insertCar(i, brands, salesPoints));
    }

    private List<Brand> insertBrands() {
        List.of(new Brand().setName("Nissan"),
                new Brand().setName("Ford"),
                new Brand().setName("Tesla"))
                .forEach(brandCrud::insert);
        List<Brand> brandsCreated = brandCrud.criteria().getResultList();
        System.out.println("Brands created: " + brandsCreated);
        return brandsCreated;
    }

    private List<SalesPoint> insertSalesPoints() {
        List.of(new SalesPoint(new SalesPointPK(1L, 1L)).setName("Nissan"),
                new SalesPoint(new SalesPointPK(1L, 2L)).setName("Ford Motors"),
                new SalesPoint(new SalesPointPK(1L, 3L)).setName("Tesla HQ"))
                .forEach(salesPointCrud::insert);
        List<SalesPoint> salesPointsCreated = salesPointCrud.criteria().getResultList();
        System.out.println("SalesPoints created: " + salesPointsCreated);
        return salesPointsCreated;
    }

    private void insertCar(int index, List<Brand> brands, List<SalesPoint> salesPoints) {
        Collections.shuffle(brands);
        Collections.shuffle(salesPoints);
        List<SalesPoint> carSalesPoints = List.of(salesPoints.get(0), salesPoints.get(1));
        carCrud.insert(new Car().model("model " + index)
                .name("name" + index)
                .price(Double.valueOf(index))
                .setBrand(brands.get(0))
                .setSalesPoints(carSalesPoints));
    }

   /* Car carExample = new Car();
    SalesPoint salesPoint = new SalesPoint(new SalesPointPK(2L, 1L));
    List<SalesPoint> salesPoints = new ArrayList<>();
        salesPoints.add(salesPoint);
        carExample.setSalesPoints(salesPoints);
    List<Car> carsFound = carService.example(carExample, Car_.salesPoints).getResultList();*/


}
