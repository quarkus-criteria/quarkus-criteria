package com.github.quarkus.criteria;

import com.github.database.rider.cdi.api.DBRider;
import com.github.database.rider.core.api.dataset.DataSet;
import com.github.quarkus.criteria.model.*;
import com.github.quarkus.criteria.runtime.model.ComparisonOperation;
import com.github.quarkus.criteria.runtime.service.CrudService;
import com.github.quarkus.criteria.runtime.service.Service;
import com.github.quarkus.criteria.service.CarService;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.deltaspike.data.api.criteria.Criteria;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

import static com.github.quarkus.criteria.runtime.model.ComparisonOperation.LT_OR_EQ;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DBRider
@QuarkusTest
public class CriteriaByExampleIt {

    @Inject
    CarService carService;

    @Inject
    @Service
    CrudService<Car> crudService;

    @Inject
    @Service
    CrudService<Brand> brandCrud;

    @Inject
    @Service
    CrudService<CarSalesPoint> carSalesPointCrud;

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarByExample() {
        Car carExample = new Car().setModel("Ferrari");
        List<Car> cars = carService
                .exampleBuilder.of(carExample)
                .usingAttributes(Car_.model).build()
                .getResultList();
        assertThat(cars).isNotNull()
                .hasSize(1)
                .extracting("id")
                .contains(-1);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByExample() {
        Car carExample = new Car().setModel("Ferrari");
        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .usingAttributes(Car_.model)
                .build()
                .getResultList();
        assertThat(cars).isNotNull().hasSize(1)
                .extracting("model")
                .contains("Ferrari");

        carExample = new Car().setModel("porche").setName("%avenger");
        cars = crudService
                .exampleBuilder
                .of(carExample)
                .usingAttributes(ComparisonOperation.LIKE_IGNORE_CASE, Car_.name, Car_.model)
                .build()
                .getResultList();

        assertThat(cars).isNotNull().hasSize(1)
                .extracting("name")
                .contains("porche avenger");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByExampleWithoutPassingAttributes() {
        Car carExample = new Car().setModel("Ferrari");
        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .usingAttributes().build()
                .getResultList();
        assertThat(cars).isNotNull().hasSize(1)
                .extracting("model")
                .contains("Ferrari");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByExampleLikeWithoutPassingAttributes() {
        Car carExample = new Car().setModel("porche").setName("%avenger");
        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .usingAttributes(ComparisonOperation.LIKE_IGNORE_CASE)
                .build()
                .getResultList();
        assertThat(cars).isNotNull().hasSize(1)
                .extracting("name")
                .contains("porche avenger");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByExampleEqIgnoreCaseWithoutPassingAttributes() {
        Car carExample = new Car().setModel("porche");
        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .usingAttributes(ComparisonOperation.EQ_IGNORE_CASE)
                .build()
                .getResultList();
        assertThat(cars).isNotNull().hasSize(1)
                .extracting("name")
                .contains("porche avenger");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsBySalesPointIdByExample() {
        SalesPoint salesPoint = new SalesPoint(new SalesPointPK(1L, 4L));
        CarSalesPoint carSalesPointExample = new CarSalesPoint(new Car(), salesPoint);
        List<CarSalesPoint> carSalesPointsFound = carSalesPointCrud
                .exampleBuilder.of(carSalesPointExample)
                .usingAttributesAndFetch(CarSalesPoint_.salesPoint)
                .build()
                .getResultList();
        assertThat(carSalesPointsFound).isNotNull().hasSize(1);
        assertThat(carSalesPointsFound.get(0).getCar())
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByBrandAndPriceExampleCriteria() {
        Car carExample = new Car().setModel("SE")
                .setPrice(12.999);
        Brand brand = new Brand(2L);

        carExample.setBrand(brand);//model SE, price <= 12.999 and brand = Nissan

        Criteria<Car, Car> criteriaByExample = carService
                .exampleBuilder.of(carExample)
                .usingAttributes(Car_.model)
                .usingAttributes(Car_.brand)
                .usingAttributes(LT_OR_EQ, Car_.price)
                .build();

        assertThat(carService.count(criteriaByExample).intValue()).isEqualTo(1);


        List<Car> carsFound = carService
                .exampleBuilder.of(carExample)
                .usingCriteria(criteriaByExample)
                .usingAttributesAndFetch(Car_.brand)
                .build()
         .getResultList();
        assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
        assertThat(carsFound.get(0).getBrand().getName()).isEqualTo("Nissan");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByExampleUsingAnExistingCriteria() {
        Criteria<Car, Car> criteria = crudService.criteria()
                .join(Car_.brand, carService.where(Brand.class)
                        .eq(Brand_.name, "Nissan")
                ); //cars with brand nissan

        Car carExample = new Car().setModel("SE");
        //will add a restriction by car 'model' using example criteria
        Criteria<Car, Car> criteriaByExample = carService
                .exampleBuilder.of(carExample)
                .usingCriteria(criteria)
                .usingAttributes(Car_.model)
                .build();

        criteriaByExample = carService
                .exampleBuilder.of(carExample)
                .usingCriteria(criteriaByExample)
                .usingAttributes(Car_.carSalesPoints)
                .build();

        AssertionsForClassTypes.assertThat(carService.count(criteriaByExample).intValue()).isEqualTo(1);

        List<Car> carsFound = criteriaByExample.getResultList();
        assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarsByExampleFetchingAssociations() {
        SalesPoint salesPoint = carService.criteria(SalesPoint.class)
                .like(SalesPoint_.name, "Nissan%")
                .getSingleResult();
        CarSalesPoint carSalesPoint = new CarSalesPoint(new Car(), salesPoint);
        List<CarSalesPoint> resultList = carSalesPointCrud
                .exampleBuilder.of(carSalesPoint)
                .usingCriteria(carSalesPointCrud.criteria()
                        .distinct()
                        .orderAsc(CarSalesPoint_.carSalesPointId))
                .usingAttributesAndFetch(CarSalesPoint_.salesPoint).build()
                .getResultList();

        assertThat(resultList)
                .isNotNull()
                .hasSize(1);
        Car carFound = resultList.get(0).getCar();
        assertThat(carFound)
                .extracting("id", "name")
                .contains(2, "Sentra");
        List<CarSalesPoint> carSalesPointFound = carFound.getCarSalesPoints();
        assertThat(carSalesPointFound).hasSize(1);
        assertThat(carSalesPointFound.get(0).getSalesPoint())
                .extracting("name")
                .contains("Nissan Sales");
    }

    @Test
    @DataSet("brands-null-props.yml")
    public void shouldFindByExampleUsingNullOperation() {
        List<Brand> brands = brandCrud
                .exampleBuilder.of(new Brand())
                .usingAttributes(ComparisonOperation.IS_NULL, Brand_.name)
                .build()
                .getResultList();
        assertThat(brands).isNotNull().hasSize(1)
                .extracting("id")
                .contains(2L);

        brands = brandCrud
                .exampleBuilder.of(new Brand())
                .usingAttributes(ComparisonOperation.NOT_NULL, Brand_.name)
                .build()
                .getResultList();
        assertThat(brands).isNotNull().hasSize(2)
                .extracting("id")
                .contains(1L, 3L);
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsOfSpecificBrandByExample() {
        Brand tesla = brandCrud.criteria()
                .eq(Brand_.name, "Tesla")
                .getSingleResult();

        List<Car> carsFound = carService.exampleBuilder
                .of(new Car().setBrand(tesla))
                .usingAttributesAndFetch()
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Model S", "Model X");

        tesla.setCars(carsFound);
        List<Brand> brands = brandCrud.exampleBuilder
                .of(tesla)
                .usingAttributesAndFetch(Brand_.cars)
                .build().distinct()
                .getResultList();

        assertThat(brands).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Tesla");
    }
}
