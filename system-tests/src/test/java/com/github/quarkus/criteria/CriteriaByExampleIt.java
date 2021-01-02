package com.github.quarkus.criteria;

import com.github.quarkus.criteria.model.*;
import com.github.quarkus.criteria.runtime.service.CrudService;
import com.github.quarkus.criteria.runtime.service.Service;
import com.github.quarkus.criteria.service.CarService;
import com.github.database.rider.cdi.api.DBRider;
import com.github.database.rider.core.api.dataset.DataSet;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.deltaspike.data.api.criteria.Criteria;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DBRider
@QuarkusTest
public class CriteriaByExampleIt {

    @Inject
    CarService carService;

    @Inject
    @Service
    CrudService<Car> crudService;

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarByExample() {
        Car carExample = new Car().model("Ferrari");
        List<Car> cars = carService
                .exampleBuilder.of(carExample)
                .example(Car_.model).build()
                .getResultList();
        assertThat(cars).isNotNull()
                .hasSize(1)
                .extracting("id")
                .contains(-1);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByExample() {
        Car carExample = new Car().model("Ferrari");
        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .example(Car_.model)
                .build()
                .getResultList();
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("model")
                .contains("Ferrari");

        carExample = new Car().model("porche").name("%avenger");
        cars = crudService
                .exampleBuilder
                .of(carExample)
                .exampleLike(Car_.name, Car_.model)
                .build()
                .getResultList();

        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("name")
                .contains("porche avenger");
    }

    @DataSet("cars.yml")
    public void shouldFindCarsByExampleWithoutPassingAttributes() {
        Car carExample = new Car().model("Ferrari");
        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .example().build()
                .getResultList();
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("model")
                .contains("Ferrari");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByExampleLikeWithoutPassingAttributes() {
        Car carExample = new Car().model("porche").name("%avenger");
        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .exampleLike()
                .build()
                .getResultList();
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("name")
                .contains("porche avenger");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsBySalesPointAddressByExample() {
        Car carExample = new Car();
        SalesPoint salesPoint = new SalesPoint(new SalesPointPK(2L, 1L));
        List<SalesPoint> salesPoints = new ArrayList<>();
        salesPoints.add(salesPoint);
        carExample.setSalesPoints(salesPoints);
        List<Car> carsFound = carService
                .exampleBuilder.of(carExample)
                .example(Car_.salesPoints)
                .build()
                .getResultList();
        AssertionsForInterfaceTypes.assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByMultipleExampleCriteria() {
        Car carExample = new Car().model("SE");
        SalesPoint salesPoint = new SalesPoint(new SalesPointPK(2L, 1L));
        List<SalesPoint> salesPoints = new ArrayList<>();
        salesPoints.add(salesPoint);
        carExample.setSalesPoints(salesPoints);

        Brand brand = new Brand(2L);

        carExample.setBrand(brand);//model SE, brand: Nissan, salesPint: Nissan Sales

        Criteria<Car, Car> criteriaByExample = carService
                .exampleBuilder.of(carExample)
                .example(Car_.model, Car_.brand, Car_.salesPoints)
                .build();

        AssertionsForClassTypes.assertThat(carService.count(criteriaByExample).intValue()).isEqualTo(1);

        List<Car> carsFound = criteriaByExample.getResultList();
        AssertionsForInterfaceTypes.assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByExampleUsingAnExistingCriteria() {
        Criteria<Car, Car> criteria = crudService.criteria()
                .join(Car_.brand, carService.where(Brand.class)
                        .eq(Brand_.name, "Nissan")
                ); //cars with brand nissan

        Car carExample = new Car().model("SE");
        //will add a restriction by car 'model' using example criteria
        Criteria<Car, Car> criteriaByExample = carService
                .exampleBuilder.of(carExample)
                .usingCriteria(criteria)
                .example(Car_.model)
                .build();

        criteriaByExample = carService
                .exampleBuilder.of(carExample)
                .usingCriteria(criteriaByExample)
                .example(Car_.salesPoints)
                .build();

        AssertionsForClassTypes.assertThat(carService.count(criteriaByExample).intValue()).isEqualTo(1);

        List<Car> carsFound = criteriaByExample.getResultList();
        AssertionsForInterfaceTypes.assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarsByExampleFetchingAssociatios() {
        Car carExample = new Car();
        SalesPoint salesPoint = carService.criteria(SalesPoint.class)
                .like(SalesPoint_.name, "Nissan%")
                .getSingleResult();
        List<SalesPoint> salesPoints = List.of(salesPoint);
        carExample.setSalesPoints(salesPoints);
        List<Car> resultList = carService
                .exampleBuilder.of(carExample)
                .usingCriteria(carService.criteria()
                        .distinct()
                        .orderAsc(Car_.id))
                .usingFetch(true)
                .example(Car_.salesPoints).build()
                .getResultList();

        assertThat(resultList)
                .isNotNull()
                .hasSize(1);
        Car carFound = resultList.get(0);
        assertThat(carFound)
                .extracting("id", "name")
                .contains(2, "Sentra");
        List<SalesPoint> carSalesPoint = carFound.getSalesPoints();
        assertThat(carSalesPoint).hasSize(1);
        assertThat(carSalesPoint.get(0))
                .extracting("name")
                .contains("Nissan Sales");
    }
}
