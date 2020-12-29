package com.github.adminfaces.quarkus.criteria;

import com.github.adminfaces.quarkus.criteria.model.*;
import com.github.adminfaces.quarkus.criteria.runtime.model.Filter;
import com.github.adminfaces.quarkus.criteria.runtime.model.Sort;
import com.github.adminfaces.quarkus.criteria.runtime.service.CrudService;
import com.github.adminfaces.quarkus.criteria.runtime.service.Service;
import com.github.adminfaces.quarkus.criteria.service.CarService;
import com.github.database.rider.cdi.api.DBRider;
import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.core.configuration.DataSetConfig;
import com.github.database.rider.core.dsl.RiderDSL;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.deltaspike.data.api.criteria.Criteria;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.*;

@DataSet(cleanBefore = true)
@DBRider
@QuarkusTest
public class CrudServiceIt {

    @Inject
    DataSource dataSource;

    @Inject
    CarService carService;

    @Inject
    @Service
    CrudService<Car> crudService;

    @Inject
    @Service
    CrudService<SalesPoint> salesPointService;

    @Test
    @DataSet("cars.yml")
    public void shouldCountCars() {
        assertThat(carService.count()).isEqualTo(4);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarById() {
        Car car = carService.findById(-1);
        assertThat(car).isNotNull()
                .extracting("id")
                .contains(-1);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarByExample() {
        Car carExample = new Car().model("Ferrari");
        List<Car> cars = carService.example(carExample, Car_.model).getResultList();
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull()
                .hasSize(1)
                .extracting("id")
                .contains(-1);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByListOfIds() {
        Car ferrari = new Car(-1);
        Car mustang = new Car(-2);
        List<Car> carsToFind = Arrays.asList(ferrari, mustang);
        List<Car> cars = carService.findCarsInList(carsToFind);
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull()
                .hasSize(2)
                .extracting("id")
                .contains(-1, -2);
    }

    @Test
    public void shouldNotInsertCarWithoutName() {
        long countBefore = carService.count();
        Car newCar = new Car().model("My Car").price(1d);
        try {
            carService.insert(newCar);
        } catch (RuntimeException e) {
            assertEquals("Car name cannot be empty", e.getMessage());
        }
        assertThat(countBefore).isEqualTo(carService.count());
    }

    @Test
    public void shouldNotInsertCarWithoutModel() {
        Car newCar = new Car().name("My Car")
                .price(1d);
        try {
            carService.insert(newCar);
        } catch (RuntimeException e) {
            assertEquals("Car model cannot be empty", e.getMessage());
        }
    }

    @Test
    @DataSet("cars.yml")
    public void shouldNotInsertCarWithDuplicateName() {
        Car newCar = new Car().model("My Car")
                .name("ferrari spider")
                .price(1d);
        try {
            carService.insert(newCar);
        } catch (RuntimeException e) {
            assertEquals("Car name must be unique", e.getMessage());
        }
    }

    @Test
    public void shouldInsertCar() {
        long countBefore = carService.count();
        assertEquals(countBefore, 0);
        Car newCar = new Car().model("My Car")
                .name("car name").price(1d);
        carService.insert(newCar);
        assertEquals(new Long(countBefore + 1), carService.count());
    }

    @Test
    @DataSet("cars.yml")
    public void shouldRemoveCar() {
        Car car = getCar();
        carService.remove(car);
        assertEquals(carService.count(carService.criteria().eq(Car_.id, 1)).intValue(), 0);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldRemoveCars() {
        assertThat(carService.count()).isEqualTo(4L);
        List<Car> cars = carService.criteria().getResultList();
        carService.remove(cars);
        assertEquals(carService.count().intValue(), 0);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldRemoveCarNotAttachedToPersistenceContext() {
        assertThat(carService.count()).isEqualTo(4L);
        Car car = new Car(-1);
        carService.remove(car);
        assertThat(carService.count()).isEqualTo(3L);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldUpdateCar() {
        Car car = getCar();
        car.name("updated name");
        carService.update(car);
        Car carFound = carService.criteria().eq(Car_.id, -1).getSingleResult();
        assertThat(carFound).isNotNull().extracting("name")
                .contains("updated name");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldUpdateCarNotAttachedToPersistenceContext() {
        Car car = new Car(-1);
        car.model("updated model").name("updated name").price(1.2);
        Car updatedCar = carService.update(car);
        assertThat(updatedCar).isNotNull().extracting("id")
                .contains(1);//a new record will be created because entity was not managed
        Car carFound = carService.criteria().eq(Car_.id, -1).getSingleResult();
        assertThat(carFound).isNotNull().extracting("model")
                .contains("Ferrari"); //entity of id -1 was not updated

        carFound = carService.criteria().eq(Car_.id, 1).getSingleResult();
        assertThat(carFound).isNotNull().extracting("model")
                .contains("updated model");
    }

    @Test
    public void shouldNotUpdateNullCar() {
        try {
            carService.update(null);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Record cannot be null");
        }
    }

    @Test
    public void shouldNotUpdateCarWithoutId() {
        Car car = new Car();
        car.model("updated model").name("updated name").price(1.2);
        try {
            carService.update(car);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Record cannot be transient");
        }
    }

    @Test
    @DataSet("cars.yml")
    public void shouldSaveOrUpdateCar() {
        Car car = getCar(-1);
        assertThat(car).extracting("model")
                .contains("Ferrari");
        car.setModel("Ferrari update");
        carService.update(car);
        Car carUpdated = getCar(-1);
        assertThat(carUpdated).extracting("model")
                .contains("Ferrari update");

        Car newCar = new Car();
        newCar.model("new model").price(1111.1)
                .name("new name");
        newCar = carService.saveOrUpdate(newCar);

        assertThat(newCar).isNotNull();

        Integer id = newCar.getId();
        assertThat(id).isNotNull();
        newCar = getCar(id);
        assertThat(newCar).isNotNull();
    }

    @Test
    @DataSet("cars.yml")
    public void shouldListCarsModel() {
        List<Car> cars = carService.listByModel("%porche%");
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(2);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldPaginateCars() {
        Filter<Car> carFilter = new Filter<Car>().setFirst(0).setPageSize(1)
                .setAdminSort(Sort.ASCENDING)
                .setSortField(Car_.id.getName());
        List<Car> cars = carService.paginate(carFilter);
        assertNotNull(cars);
        assertEquals(cars.size(), 1);
        assertEquals(cars.get(0).getId(), Integer.valueOf(-3));
        carFilter.setFirst(1);//get second database page
        cars = carService.paginate(carFilter);
        assertNotNull(cars);
        assertEquals(cars.size(), 1);
        assertEquals(cars.get(0).getId(), Integer.valueOf(-2));
        carFilter.setFirst(0);
        carFilter.setPageSize(4);
        cars = carService.paginate(carFilter);
        assertEquals(cars.size(), 4);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldPaginateAndSortCars() {
        Filter<Car> carFilter = new Filter<Car>()
                .setFirst(0)
                .setPageSize(4)
                .setSortField("model")
                .setAdminSort(Sort.DESCENDING);
        List<Car> cars = carService.paginate(carFilter);
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(4);
        assertTrue(cars.get(0).getModel().equals("Porche274"));
        assertTrue(cars.get(3).getModel().equals("Ferrari"));
    }

    @Test
    @DataSet("ferrari-and-porche.yml")
    public void shouldPaginateAndSortCarsByModelAndPrice() {
        Filter<Car> carFilter = new Filter<Car>()
                .setFirst(0)
                .setPageSize(4)
                .addMultSort(Sort.DESCENDING, "model")
                .addMultSort(Sort.ASCENDING, "price");

        List<Car> cars = carService.paginate(carFilter);
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(4);
        assertTrue(cars.get(0).getModel().equals("Porche"));
        assertTrue(cars.get(1).getModel().equals("Porche"));
        assertTrue(cars.get(0).getName().equals("Avenger"));
        assertTrue(cars.get(1).getName().equals("Cayman"));
        assertTrue(cars.get(2).getModel().equals("Ferrari"));
        assertTrue(cars.get(3).getModel().equals("Ferrari"));
        assertTrue(cars.get(2).getName().equals("Testarossa"));
        assertTrue(cars.get(3).getName().equals("Spider"));
    }

    @Test
    @DataSet("cars.yml")
    public void shouldPaginateCarsByModel() {
        Car car = new Car().model("Ferrari");
        Filter<Car> carFilter = new Filter<Car>().
                setFirst(0).setPageSize(4)
                .setEntity(car);
        List<Car> cars = carService.paginate(carFilter);
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("model").contains("Ferrari");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldPaginateCarsByPrice() {
        Car carExample = new Car().price(12999.0);
        Filter<Car> carFilter = new Filter<Car>().setFirst(0).setPageSize(2).setEntity(carExample);
        List<Car> cars = carService.paginate(carFilter);
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("model").contains("Mustang");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldPaginateCarsByIdInParam() {
        Filter<Car> carFilter = new Filter<Car>().setFirst(0)
                .setPageSize(2)
                .addParam("id", -1);
        List<Car> cars = carService.paginate(carFilter);
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("id").contains(-1);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldListCarsByPrice() {
        List<Car> cars = carService.criteria()
                .between(Car_.price, 1000D, 2450.9D)
                .orderAsc(Car_.price).getResultList();
        //ferrari and porche
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull()
                .hasSize(2).extracting("model")
                .contains("Porche", "Ferrari");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldGetCarModels() {
        List<String> models = carService.getModels("po");
        //porche and Porche274
        AssertionsForInterfaceTypes.assertThat(models).isNotNull().hasSize(2)
                .contains("Porche", "Porche274");
    }

    @Test
    @DataSet("cars.yml")
    public void shoulListCarsUsingCrudUtility() {
        assertThat(new Long(4)).isEqualTo(crudService.count());
        long count = crudService.count(crudService.criteria()
                .likeIgnoreCase(Car_.model, "%porche%")
                .gtOrEq(Car_.price, 10000D));
        assertEquals(1, count);

    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByExample() {
        Car carExample = new Car().model("Ferrari");
        List<Car> cars = crudService.example(carExample, Car_.model).getResultList();
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("model")
                .contains("Ferrari");

        carExample = new Car().model("porche").name("%avenger");
        cars = crudService.exampleLike(carExample, Car_.name, Car_.model).getResultList();

        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("name")
                .contains("porche avenger");
    }

    @DataSet("cars.yml")
    public void shouldFindCarsByExampleWithoutPassingAttributes() {
        Car carExample = new Car().model("Ferrari");
        List<Car> cars = crudService.example(carExample).getResultList();
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("model")
                .contains("Ferrari");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByExampleLikeWithoutPassingAttributes() {
        Car carExample = new Car().model("porche").name("%avenger");
        List<Car> cars = crudService.exampleLike(carExample).getResultList();
        AssertionsForInterfaceTypes.assertThat(cars).isNotNull().hasSize(1)
                .extracting("name")
                .contains("porche avenger");
    }

    @Test
    @DataSet("cars.yml")
    public void shoulGetTotalPriceByModel() {
        assertEquals((Double) 20380.53, carService.getTotalPriceByModel(new Car().model("%porche%")));
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindByCompositeKey() {
        SalesPointPK pk = new SalesPointPK(1L, 3L);
        SalesPoint salesPoint = salesPointService.findById(pk);
        assertThat(salesPoint).isNotNull().extracting("name")
                .contains("Ford Motors2");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldCountByCompositeKey() {
        Long count = salesPointService.count();
        assertThat(count).isNotNull().isEqualTo(3);
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsBySalesPoint() {
        List<Car> carsFound = carService.findBySalesPoint(new SalesPoint(new SalesPointPK(2L, 1L)));
        AssertionsForInterfaceTypes.assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsBySalesPointAddress() {
        List<Car> carsFound = carService.findBySalesPointAddress("Nissan address");
        AssertionsForInterfaceTypes.assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsBySalesPointAddressByExample() {
        Car carExample = new Car();
        SalesPoint salesPoint = new SalesPoint(new SalesPointPK(2L, 1L));
        List<SalesPoint> salesPoints = new ArrayList<>();
        salesPoints.add(salesPoint);
        carExample.setSalesPoints(salesPoints);
        List<Car> carsFound = carService.example(carExample, Car_.salesPoints).getResultList();
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

        Criteria<Car, Car> criteriaByExample = carService.example(carExample, Car_.model, Car_.brand, Car_.salesPoints);

        assertThat(carService.count(criteriaByExample).intValue()).isEqualTo(1);

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
        Criteria<Car, Car> criteriaByExample = carService.example(criteria, carExample, Car_.model);

        criteriaByExample = carService.example(criteriaByExample, carExample, Car_.salesPoints);

        assertThat(carService.count(criteriaByExample).intValue()).isEqualTo(1);

        List<Car> carsFound = criteriaByExample.getResultList();
        AssertionsForInterfaceTypes.assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet(value = "sales-points.yml", cleanBefore = true)
    public void shouldListSalesPointsUsingCarService() {
        List<SalesPoint> listSalesPointsByName = carService.listSalesPointsByName("Motors");
        AssertionsForInterfaceTypes.assertThat(listSalesPointsByName).isNotNull().hasSize(2);
    }

    @Test
    public void shouldDeleteEntitiesInBatches() throws SQLException {
        final DataSetConfig dataSetConfig = new DataSetConfig("car-batch.yml");
        RiderDSL.DBUnitConfigDSL riderDSL = RiderDSL.withConnection(dataSource.getConnection())
                .withDataSetConfig(dataSetConfig);
        riderDSL.createDataSet();
        assertThat(crudService.count()).isEqualTo(10L);
        //batch equal to entities size
        int deleted = crudService.removeBatch(crudService.criteria().getResultList(), 10);
        assertThat(deleted).isEqualTo(10);
        assertThat(crudService.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        assertThat(crudService.count()).isEqualTo(10L);
        //batch < entities size
        deleted = crudService.removeBatch(crudService.criteria().getResultList(), 9);
        assertThat(deleted).isEqualTo(10);
        assertThat(crudService.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // batch > entities size
        deleted = crudService.removeBatch(crudService.criteria().getResultList(), 11);
        assertThat(deleted).isEqualTo(10);
        assertThat(crudService.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // invalid batch size, use default batch size
        deleted = crudService.removeBatch(crudService.criteria().getResultList(), 0);
        assertThat(deleted).isEqualTo(10);
        assertThat(crudService.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // small batch size
        deleted = crudService.removeBatch(crudService.criteria().getResultList(), 1);
        assertThat(deleted).isEqualTo(10);
        assertThat(crudService.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // small batch size
        deleted = crudService.removeBatch(crudService.criteria().getResultList(), 3);
        assertThat(deleted).isEqualTo(10);
        assertThat(crudService.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // 'prime' batch size
        deleted = crudService.removeBatch(crudService.criteria().getResultList(), 7);
        assertThat(deleted).isEqualTo(10);
        assertThat(crudService.count()).isEqualTo(0L);
    }

    private Car getCar() {

        return getCar(-1);
    }

    private Car getCar(Integer id) {
        if (id == null) {
            return getCar();
        }
        assertEquals(carService.count(carService.criteria().eq(Car_.id, id)), Long.valueOf(1));
        Car car = carService.findById(id);
        assertNotNull(car);
        return car;
    }
}
