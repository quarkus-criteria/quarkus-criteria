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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.quarkus.criteria.runtime.model.ComparisonOperation.*;
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

    @Inject
    @Service
    CrudService<SalesPoint> salesPointCrud;

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarByExample() {
        Car carExample = new Car().setModel("%rrari");
        List<Car> cars = carService
                .exampleBuilder.of(carExample)
                .with(ComparisonOperation.LIKE_IGNORE_CASE, Car_.model).build()
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
                .with(Car_.model)
                .build()
                .getResultList();
        assertThat(cars).isNotNull().hasSize(1)
                .extracting("model")
                .contains("Ferrari");

        carExample = new Car().setModel("porche").setName("%avenger");
        cars = crudService
                .exampleBuilder
                .of(carExample)
                .with(ComparisonOperation.LIKE_IGNORE_CASE, Car_.name, Car_.model)
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
                .with().build()
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
                .with(ComparisonOperation.LIKE_IGNORE_CASE)
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
                .with(ComparisonOperation.EQ_IGNORE_CASE)
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
                .with(CarSalesPoint_.salesPoint)
                .build()
                .fetch(CarSalesPoint_.salesPoint)
                .getResultList();
        assertThat(carSalesPointsFound).isNotNull().hasSize(1);
        assertThat(carSalesPointsFound.get(0).getCar())
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarAndCountByBrandAndPrice() {
        Car carExample = new Car().setModel("SE")
                .setPrice(12.999);
        Brand brand = new Brand(2L);

        carExample.setBrand(brand);//model SE, price <= 12.999 and brand = Nissan

        Criteria<Car, Car> criteriaByExample = carService
                .exampleBuilder.of(carExample)
                .with(Car_.model)
                .with(Car_.brand)
                .with(LT_OR_EQ, Car_.price)
                .build();

        assertThat(carService.count(criteriaByExample).intValue()).isEqualTo(1);

        List<Car> carsFound = carService
                .exampleBuilder.of(carExample)
                .withCriteria(criteriaByExample)
                .build().fetch(Car_.brand)
                .getResultList();
        assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
        assertThat(carsFound.get(0).getBrand().getName()).isEqualTo("Nissan");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByModelNamePriceAndBrand() {
        Brand brand = new Brand().setName("%ssan");
        Car carExample = new Car().setModel("SE").setName("%tra")
                .setPrice(12.999)
                .setBrand(brand);
        Car car = (Car) carService
                .exampleBuilder.of(carExample)
                .with(Car_.model) //EQ
                .with(LIKE_IGNORE_CASE, Brand_.name, Car_.name)
                .with(LT_OR_EQ, Car_.price)
                .build()
                .fetch(Car_.brand)
                .getSingleResult();
        assertThat(car).isNotNull()
                .extracting(Car::getName, Car::getModel, Car::getBrand)
                .contains("Sentra", "SE", new Brand(2L));//brand id=2 is nissan
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByBrandName() {
        Brand brand = new Brand().setName("niss%");
        Car carExample = new Car().setBrand(brand);

        List<Car> carsFound = carService
                .exampleBuilder.of(carExample)
                .with(LIKE_IGNORE_CASE, Brand_.name)
                .build()
                .fetch(Car_.brand) //bring brand association into result list
                .getResultList();

        assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
        assertThat(carsFound.get(0).getBrand().getName()).isEqualTo("Nissan");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarsBySalesPointId() {
        SalesPoint salesPoint = new SalesPoint().setSalesPointPK(new SalesPointPK(1L, 5L));
        CarSalesPoint carSalesPointExample = new CarSalesPoint().setSalesPoint(salesPoint);

        List<CarSalesPoint> carSalesPointsFound = carSalesPointCrud
                .exampleBuilder.of(carSalesPointExample)
                .withCriteria(carSalesPointCrud.criteria().distinct())
                .with(CarSalesPoint_.salesPoint)
                .build().fetch(CarSalesPoint_.salesPoint)
                .getResultList();
        assertThat(carSalesPointsFound).isNotNull().hasSize(2);
        List<Car> carsFound = carSalesPointsFound.stream()
                .map(carSalesPoint -> carSalesPoint.getCar())
                .collect(Collectors.toUnmodifiableList());
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Model S", "Model X");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarsBySalesPointAddress() {
        SalesPoint salesPoint = new SalesPoint().setAddress("Tesla HQ address");
        CarSalesPoint carSalesPointExample = new CarSalesPoint().setSalesPoint(salesPoint);

        List<CarSalesPoint> carSalesPointsFound = carSalesPointCrud
                .exampleBuilder.of(carSalesPointExample)
                .with(SalesPoint_.address)
                .build()
                .distinct()
                .fetch(CarSalesPoint_.salesPoint)
                .getResultList();
        assertThat(carSalesPointsFound).isNotNull().hasSize(2);
        List<Car> carsFound = carSalesPointsFound.stream()
                .map(carSalesPoint -> carSalesPoint.getCar())
                .collect(Collectors.toUnmodifiableList());
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Model S", "Model X");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindSalesPointOfGivenCarBrand() {
        Car car = new Car().setBrand(new Brand().setName("tesla"));
        CarSalesPoint carSalesPointExample = new CarSalesPoint().setCar(car);
        Criteria build = carSalesPointCrud
                .exampleBuilder.of(carSalesPointExample)
                .withCriteria(carSalesPointCrud.criteria()
                        .distinct()
                        .fetch(CarSalesPoint_.salesPoint)
                )
                .with(EQ_IGNORE_CASE, Brand_.name)
                .build();
        List<CarSalesPoint> carSalesPointsFound = build.getResultList();
        assertThat(carSalesPointsFound).isNotNull().hasSize(2)
                .extracting(carSalesPoint1 -> carSalesPoint1.getCar().getName())
                .contains("Model S", "Model X");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListBrandsByCarPrice() {
        Car car = new Car().setPrice(10.000);
        Brand brandExample = new Brand()
                .setCars(Set.of(car));
        List<Brand> brands = brandCrud.exampleBuilder
                .of(brandExample)
                .with(GT, Car_.price)
                .build()
                .getResultList();
        assertThat(brands).isNotNull().hasSize(2)
                .extracting(brand -> brand.getName())
                .contains("Ford", "Nissan");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListBrandsBySalesPointAddress() {
        SalesPoint salesPoint = new SalesPoint()
                .setAddress("Tesla HQ address");
        Set<Car> cars = Set.of(new Car().addSalesPoint(salesPoint));
        Brand brandExample = new Brand().setCars(cars);

        List<Brand> brands = brandCrud.exampleBuilder
                .of(brandExample)
                .with(EQ, SalesPoint_.address)
                .build()
                .distinct()
                .getResultList();
        assertThat(brands).isNotNull().hasSize(1)
                .extracting(brand -> brand.getName())
                .contains("Tesla");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListBrandsByCarId() {
        Car modelS = new Car(3);
        Brand brandExample = new Brand()
                .setCars(Set.of(modelS));
        List<Brand> brands = brandCrud.exampleBuilder
                .of(brandExample)
                .with(Car_.id)
                .build()
                .getResultList();
        assertThat(brands).isNotNull().hasSize(1)
                .extracting(brand -> brand.getName())
                .contains("Tesla");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListBrandsMultipleByCarsIds() {
        Car fusion = new Car(1);
        Car modelS = new Car(3);
        Brand brandExample = new Brand()
                .setCars(Set.of(fusion, modelS));
        List<Brand> brands = brandCrud.exampleBuilder
                .of(brandExample)
                .with(Brand_.cars)
                .build()
                .getResultList();
        assertThat(brands).isNotNull().hasSize(2)
                .extracting(brand -> brand.getName())
                .contains("Ford", "Tesla");
    }

    //@Test
    @DataSet("cars-full.yml")
    public void shouldListBrandsByCarSalesPoint() {
        List<CarSalesPoint> carSalesPoints = List.of(new CarSalesPoint(new CarSalesPointId(1, new SalesPointPK(1L, 2L))),
                new CarSalesPoint(new CarSalesPointId(2, new SalesPointPK(1L, 4L))));

        Car carExample = new Car().setCarSalesPoints(carSalesPoints);
        Brand exampleBrand = new Brand()
                .setCars(Set.of(carExample));
        List<Brand> brands = brandCrud.exampleBuilder.of(exampleBrand)
                .with(Car_.carSalesPoints)
                .build().getResultList();
        assertThat(brands).isNotNull();
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
                .withCriteria(criteria)
                .with(Car_.model)
                .build();

        criteriaByExample = carService
                .exampleBuilder.of(carExample)
                .withCriteria(criteriaByExample)
                .with(Car_.carSalesPoints)
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
                .withCriteria(carSalesPointCrud.criteria()
                        .distinct()
                        .orderAsc(CarSalesPoint_.carSalesPointId))
                .with(CarSalesPoint_.salesPoint)
                .build().fetch(CarSalesPoint_.salesPoint)
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
                .with(ComparisonOperation.IS_NULL, Brand_.name)
                .build()
                .getResultList();
        assertThat(brands).isNotNull().hasSize(1)
                .extracting("id")
                .contains(2L);

        brands = brandCrud
                .exampleBuilder.of(new Brand())
                .with(ComparisonOperation.NOT_NULL, Brand_.name)
                .build()
                .getResultList();
        assertThat(brands).isNotNull().hasSize(2)
                .extracting("id")
                .contains(1L, 3L);
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsByCarSalesPointName() {
        CarSalesPoint carSalesPointExample = new CarSalesPoint(new Car(), new SalesPoint().setName("%tesla%"));
        Car carExample = new Car().setCarSalesPoints(List.of(carSalesPointExample));
        List<Car> cars = carService
                .exampleBuilder.of(carExample)
                .with(LIKE_IGNORE_CASE, SalesPoint_.name)
                .build()
                .getResultList();
        assertThat(cars).hasSize(2)
                .extracting(car -> car.getName())
                .contains("Model S", "Model X");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsOfSpecificBrandByExample() {
        Brand tesla = brandCrud.criteria()
                .eq(Brand_.name, "Tesla")
                .getSingleResult();

        List<Car> carsFound = carService.exampleBuilder
                .of(new Car().setBrand(tesla))
                .with()
                .build()
                .getResultList();
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Model S", "Model X");

        tesla.setCars(new HashSet<>(carsFound));
        List<Brand> brands = brandCrud.exampleBuilder
                .of(tesla)
                .with(Brand_.cars)
                .build().fetch(Brand_.cars)
                .distinct()
                .getResultList();

        assertThat(brands).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Tesla");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsUsingPriceByExample() {
        List<Car> carsFound = carService.exampleBuilder
                .of(new Car().setPrice(9.999D))
                .with(LT, Car_.price)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Model S");

        carsFound = carService.exampleBuilder
                .of(new Car().setPrice(9.999D))
                .with(LT_OR_EQ, Car_.price)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Model S", "Model X");

        carsFound = carService.exampleBuilder
                .of(new Car().setPrice(9.999D))
                .with(GT, Car_.price)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Sentra", "Fusion");

        carsFound = carService.exampleBuilder
                .of(new Car().setPrice(9.999D))
                .with(GT_OR_EQ, Car_.price)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(3)
                .extracting("name")
                .contains("Sentra", "Fusion", "Model X");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsUsingNameByExample() {
        List<Car> carsFound = carService.exampleBuilder
                .of(new Car().setName("%odel%"))
                .with(LIKE, Car_.name)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Model S", "Model X");

        carsFound = carService.exampleBuilder
                .of(new Car().setName("%odel%"))
                .with(NOT_LIKE, Car_.name)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Sentra", "Fusion");

        carsFound = carService.exampleBuilder
                .of(new Car().setName("model%"))
                .with(NOT_LIKE_IGNORE_CASE, Car_.name)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Sentra", "Fusion");

        carsFound = carService.exampleBuilder
                .of(new Car().setName("model%"))
                .with(LIKE_IGNORE_CASE, Car_.name)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Model S", "Model X");

        carsFound = carService.exampleBuilder
                .of(new Car().setName("Sentra"))
                .with(NOT_EQ, Car_.name)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(3)
                .extracting("name")
                .contains("Model S", "Model X", "Fusion");

        carsFound = carService.exampleBuilder
                .of(new Car().setName("sentra"))
                .with(NOT_EQ_IGNORE_CASE, Car_.name)
                .build().getResultList();
        assertThat(carsFound).isNotNull().hasSize(3)
                .extracting("name")
                .contains("Model S", "Model X", "Fusion");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByModelUsingNullOperation() {
        Car carExample = new Car().setModel("S");
        ComparisonOperation operation = null;
        Car car = (Car) crudService
                .exampleBuilder.of(carExample)
                .with(operation, Car_.model)
                .build()
                .getSingleResult();
        assertThat(car).isNotNull()
                .extracting(c -> c.getName())
                .contains("Model S");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByModelOrName() {
        Car carExample = new Car()
                .setName("Fusion")
                .setModel("S");
        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .with(Car_.name).or(EQ, Car_.model)
                .build()
                .getResultList();
        assertThat(cars).isNotNull()
                .extracting(c -> c.getName())
                .contains("Model S", "Fusion")
                .doesNotContain("Model X", "Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByModelOrNameOrBrandName() {
        Car carExample = new Car()
                .setName("Fusion")
                .setModel("S")
                .setBrand(new Brand().setName("Nissan"));

        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .with(Car_.name)
                .or(EQ, Car_.model).or(EQ, Brand_.name)
                .build()
                .getResultList();
        assertThat(cars).isNotNull()
                .extracting(c -> c.getName())
                .contains("Model S", "Fusion", "Sentra")
                .doesNotContain("Model X");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByPriceOrModelOrNameOrBrandName() {
        Car carExample = new Car()
                .setName("Fusion")
                .setModel("S")
                .setPrice(10.000D)
                .setBrand(new Brand().setName("Nissan"));

        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .with(GT_OR_EQ, Car_.price)
                .or(Car_.name, Car_.model, Brand_.name)
                .build()
                .getResultList();
        assertThat(cars).isNotNull()
                .extracting(c -> c.getName())
                .contains("Fusion", "Sentra", "Model S")
                .doesNotContain("Model X");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByModelOrNameOrSalePointAddress() {
        Car carExample = new Car()
                .setName("Fusion")
                .setModel("S");
        SalesPoint salesPoint = new SalesPoint()
                .setAddress("Tesla HQ address");
        CarSalesPoint carSalesPoint = new CarSalesPoint(new Car(), salesPoint);
        carExample.setCarSalesPoints(List.of(carSalesPoint));

        List<Car> cars = crudService
                .exampleBuilder.of(carExample)
                .with(Car_.name)
                .or(Car_.model, SalesPoint_.address)
                .build()
                .distinct()
                .getResultList();
        assertThat(cars).isNotNull()
                .extracting(c -> c.getName())
                .contains("Fusion", "Model X", "Model S")
                .doesNotContain("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByModelOrNameOrBrandUsingMultipleCriteria() {
        Car carExample = new Car()
                .setName("Fusion");
        Criteria byName = crudService
                .exampleBuilder.of(carExample)
                .build();

        carExample = new Car()
                .setModel("S");
        Criteria byModel = crudService
                .exampleBuilder.of(carExample)
                .build();

        carExample = new Car()
                .setBrand(new Brand().setName("Nissan"));
        Criteria byBrand = crudService
                .exampleBuilder.of(carExample)
                .with(Brand_.name)
                .build();

        List<Car> cars = carService.criteria()
                .or(byName, byModel, byBrand)
                .getResultList();

        assertThat(cars).isNotNull()
                .extracting(c -> c.getName())
                .contains("Model S", "Fusion", "Sentra")
                .doesNotContain("Model X");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindCarByNameOrModelOrSalesPointAddressAndBrandName() {
        Car carExample = new Car()
                .setName("Fusion")
                .setModel("S")
                .setBrand(new Brand().setName("%ssan"));
        SalesPoint salesPoint = new SalesPoint()
                .setAddress("Tesla%");
        CarSalesPoint carSalesPoint = new CarSalesPoint(new Car(), salesPoint);
        carExample.setCarSalesPoints(List.of(carSalesPoint));
        List<Car> cars =  crudService
                .exampleBuilder.of(carExample)
                .with(LIKE, Car_.name, Brand_.name)
                .or(LIKE_IGNORE_CASE, SalesPoint_.address)
                .or(Car_.model)
                .build()
                .distinct()
                .getResultList();
        assertThat(cars).isNotNull()
                .extracting(c -> c.getName())
                .contains("Model S", "Fusion", "Sentra", "Model X");
    }

}
