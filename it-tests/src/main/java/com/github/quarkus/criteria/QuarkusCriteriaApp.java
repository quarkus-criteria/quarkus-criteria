package com.github.quarkus.criteria;

import com.github.database.rider.core.configuration.DataSetConfig;
import com.github.database.rider.core.dsl.RiderDSL;
import com.github.quarkus.criteria.model.*;
import com.github.quarkus.criteria.runtime.service.CrudService;
import com.github.quarkus.criteria.runtime.service.Service;
import com.github.quarkus.criteria.service.CarService;
import io.quarkus.runtime.QuarkusApplication;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.util.List;

@ApplicationScoped
public class QuarkusCriteriaApp implements QuarkusApplication {

    @Inject
    DataSource dataSource;

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

    @Inject
    @Service
    CrudService<CarSalesPoint> carSalesPointCrud;

    /**
     DataSet:

brand:
    - id: 1
    name: Ford
    - id: 2
    name: Nissan
    - id: 3
    name: Tesla

car:
    - id: 1
    name: "Fusion"
    model: "Titanium"
    price: 15.850
    brand_id: 1
    version: 0
    - id: 2
    name: "Sentra"
    model: "SE"
    price: 12.999
    brand_id: 2
    version: 0
    - id: 3
    name: "Model S"
    model: "S"
    price: 5.2999
    brand_id: 3
    version: 0

sales_point:
    - id1: 1
    id2: 2
    name: Ford Motors
    address: "Ford motors address"
    - id1: 1
    id2: 3
    name: Ford Motors2
    address: "Ford motors2 address"
    - id1: 1
    id2: 4
    name: Nissan Sales
    address: "Nissan address"
    - id1: 1
    id2: 5
    name: Tesla HQ
    address: "Tesla HQ address"

car_sales_point:
    - car_id: 1
    SALESPOINTS_ID1: 1
    SALESPOINTS_ID2: 2
    id1: 1
    id2: 2
    - car_id: 1
    SALESPOINTS_ID1: 1
    SALESPOINTS_ID2: 3
    id1: 1
    id2: 3
    - car_id: 2
    SALESPOINTS_ID1: 1
    SALESPOINTS_ID2: 4
    id1: 1
    id2: 4
    - car_id: 3
    SALESPOINTS_ID1: 1
    SALESPOINTS_ID2: 5
    id1: 1
    id2: 5
     */
    @Override
    @ActivateRequestContext
    public int run(String... args) {
        try {
            RiderDSL.withConnection(dataSource.getConnection())
                    .withDataSetConfig(new DataSetConfig("cars.yml"))
                    .createDataSet();

            System.out.println("======================================================================================");
            System.out.println("Listing cars with: model contains 'tanium' or name = 'Sentra', brand = 'Ford' or 'Nissan' and sales point address = 'Ford motors address' ");
            List<Car> cars = carService.listCarsByModelBrandAndSalesPointAddress();
            cars.forEach(System.out::println);
            System.out.println("======================================================================================");
            System.out.println("Selecting car model and price and mapping to a DTO");
            List<CarWithNameAndPrice> carsProjection = carService.carsProjection();
            carsProjection.forEach(System.out::println);
            System.out.println("======================================================================================");
            System.out.println("Models names containing 'S':  " + getAllModelsContainingS());
            System.out.println("======================================================================================");
            List<CarSalesPoint> carsFound = getCarsByExample();
            System.out.println("Find by cars by example: cars that have a sales point named 'Nissan':");
            carsFound.stream()
                    .map(carSalesPoint -> carSalesPoint.getCar())
                    .forEach(System.out::println);
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }

    private List<String> getAllModelsContainingS() {
        return carCrud.criteria()
                .select(String.class, carCrud.attribute(Car_.model))
                 .like(Car_.model, "%S%")
                .getResultList();
    }

    private List<CarSalesPoint> getCarsByExample() {
        SalesPoint salesPoint = salesPointCrud.criteria()
                .likeIgnoreCase(SalesPoint_.name, "%Nissan%")
                .getSingleResult();
        CarSalesPoint example = new CarSalesPoint(new Car(), salesPoint);
        return carSalesPointCrud
                .exampleBuilder.of(example)
                .withCriteria(carSalesPointCrud.criteria()
                        .distinct()
                        .orderAsc(CarSalesPoint_.carSalesPointId))
                .with(CarSalesPoint_.salesPoint)
                        .build().fetch(CarSalesPoint_.salesPoint)
                .getResultList();
    }


}
