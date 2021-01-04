package com.github.quarkus.criteria;

import com.github.database.rider.cdi.api.DBRider;
import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.core.configuration.DataSetConfig;
import com.github.database.rider.core.dsl.RiderDSL;
import com.github.quarkus.criteria.model.*;
import com.github.quarkus.criteria.runtime.model.Filter;
import com.github.quarkus.criteria.runtime.model.Sort;
import com.github.quarkus.criteria.runtime.service.CrudService;
import com.github.quarkus.criteria.runtime.service.Service;
import com.github.quarkus.criteria.service.CarService;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
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
    CrudService<Car> carCrud;

    @Inject
    @Service
    CrudService<SalesPoint> salesPointCrud;

    @Inject
    @Service
    CrudService<Brand> brandCrud;

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
        Car anotherCar = carService.findById(-2);
        assertThat(car).isNotNull()
                .extracting("id")
                .contains(-1);
        assertThat(car).isNotEqualTo(anotherCar);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldFindCarsByListOfIds() {
        Car ferrari = new Car(-1);
        Car mustang = new Car(-2);
        List<Car> carsToFind = Arrays.asList(ferrari, mustang);
        List<Car> cars = carService.findCarsInList(carsToFind);
        assertThat(cars).isNotNull()
                .hasSize(2)
                .extracting("id")
                .contains(-1, -2);
    }

    @Test
    public void shouldNotInsertCarWithoutName() {
        long countBefore = carService.count();
        Car newCar = new Car().setModel("My Car").setPrice(1d);
        try {
            carService.insert(newCar);
        } catch (RuntimeException e) {
            assertEquals("Car name cannot be empty", e.getMessage());
        }
        assertThat(countBefore).isEqualTo(carService.count());
    }

    @Test
    public void shouldNotInsertCarWithoutModel() {
        Car newCar = new Car().setName("My Car")
                .setPrice(1d);
        try {
            carService.insert(newCar);
        } catch (RuntimeException e) {
            assertEquals("Car model cannot be empty", e.getMessage());
        }
    }

    @Test
    @DataSet("cars.yml")
    public void shouldNotInsertCarWithDuplicateName() {
        Car newCar = new Car().setModel("My Car")
                .setName("ferrari spider")
                .setPrice(1d);
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
        Car newCar = new Car().setModel("My Car")
                .setName("car name").setPrice(1d);
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
        car.setName("updated name");
        carService.update(car);
        Car carFound = carService.criteria().eq(Car_.id, -1).getSingleResult();
        assertThat(carFound).isNotNull().extracting("name")
                .contains("updated name");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldUpdateCarNotAttachedToPersistenceContext() {
        Car car = new Car(-1);
        car.setModel("updated model").setName("updated name").setPrice(1.2);
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
        car.setModel("updated model").setName("updated name").setPrice(1.2);
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
        newCar.setModel("new model").setPrice(1111.1)
                .setName("new name");
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
        assertThat(cars).isNotNull().hasSize(2);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldPaginateCars() {
        Filter<Car> carFilter = new Filter<Car>().setFirst(0).setPageSize(1)
                .setSort(Sort.ASCENDING)
                .setSortField(Car_.id.getName());
        List<Car> cars = carService.paginate(carFilter);
        assertNotNull(cars);
        assertEquals(cars.size(), 1);
        assertEquals(cars.get(0).getId(), Integer.valueOf(-4));
        carFilter.setFirst(1);//get second database page
        cars = carService.paginate(carFilter);
        assertNotNull(cars);
        assertEquals(cars.size(), 1);
        assertEquals(cars.get(0).getId(), Integer.valueOf(-3));
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
                .setSort(Sort.DESCENDING);
        List<Car> cars = carService.paginate(carFilter);
        assertThat(cars).isNotNull().hasSize(4);
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
        assertThat(cars).isNotNull().hasSize(4);
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
        Car car = new Car().setModel("Ferrari");
        Filter<Car> carFilter = new Filter<Car>().
                setFirst(0).setPageSize(4)
                .setEntity(car);
        List<Car> cars = carService.paginate(carFilter);
        assertThat(cars).isNotNull().hasSize(1)
                .extracting("model").contains("Ferrari");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldPaginateCarsByPrice() {
        Car carExample = new Car().setPrice(12999.0);
        Filter<Car> carFilter = new Filter<Car>().setFirst(0).setPageSize(2).setEntity(carExample);
        List<Car> cars = carService.paginate(carFilter);
        assertThat(cars).isNotNull().hasSize(1)
                .extracting("model").contains("Mustang");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldPaginateCarsByIdInParam() {
        Filter<Car> carFilter = new Filter<Car>().setFirst(0)
                .setPageSize(2)
                .addParam("id", -1);
        List<Car> cars = carService.paginate(carFilter);
        assertThat(cars).isNotNull().hasSize(1)
                .extracting("id").contains(-1);
    }

    @Test
    @DataSet("cars.yml")
    public void shouldListCarsByPrice() {
        List<Car> cars = carService.criteria()
                .between(Car_.price, 1000D, 2450.9D)
                .orderAsc(Car_.price).getResultList();
        //ferrari and porche
        assertThat(cars).isNotNull()
                .hasSize(2).extracting("model")
                .contains("Porche", "Ferrari");
    }

    @Test
    @DataSet("cars.yml")
    public void shouldGetCarModels() {
        List<String> models = carService.getModels("po");
        //porche and Porche274
        assertThat(models).isNotNull().hasSize(2)
                .contains("Porche", "Porche274");
    }

    @Test
    @DataSet("cars.yml")
    public void shoulListCarsUsingCrudUtility() {
        assertThat(new Long(4)).isEqualTo(carCrud.count());
        long count = carCrud.count(carCrud.criteria()
                .likeIgnoreCase(Car_.model, "%porche%")
                .gtOrEq(Car_.price, 10000D));
        assertEquals(1, count);

    }

    @Test
    @DataSet("cars.yml")
    public void shoulGetTotalPriceByModel() {
        assertEquals((Double) 20380.53, carService.getTotalPriceByModel(new Car().setModel("%porche%")));
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldFindByCompositeKey() {
        SalesPointPK pk = new SalesPointPK(1L, 3L);
        SalesPoint salesPoint = salesPointCrud.findById(pk);
        assertThat(salesPoint).isNotNull().extracting("name")
                .contains("Ford Motors2");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldCountByCompositeKey() {
        Long count = salesPointCrud.count();
        assertThat(count).isNotNull().isEqualTo(4);
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsOfSpecificBrand() {
        Brand tesla = brandCrud.criteria()
                .eq(Brand_.name, "Tesla")
                .getSingleResult();

        List<Car> carsFound = carService.findByBrand(tesla);
        assertThat(carsFound).isNotNull().hasSize(2)
                .extracting("name")
                .contains("Model S", "Model X");

        List<Brand> brands = brandCrud.criteria()
                .distinct()
                .join(Brand_.cars, brandCrud.where(Car.class)
                .in(Car_.id, brandCrud.toListOfIds(carsFound, new Integer[0])))
                .getResultList();

        assertThat(brands).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Tesla");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsBySalesPoint() {
        List<Car> carsFound = carService.findBySalesPoint(new SalesPoint(new SalesPointPK(1L, 4L)));
        assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet("cars-full.yml")
    public void shouldListCarsBySalesPointAddress() {
        List<Car> carsFound = carService.findBySalesPointAddress("Nissan address");
        assertThat(carsFound).isNotNull().hasSize(1)
                .extracting("name")
                .contains("Sentra");
    }

    @Test
    @DataSet(value = "sales-points.yml", cleanBefore = true)
    public void shouldListSalesPointsUsingCarService() {
        List<SalesPoint> listSalesPointsByName = carService.listSalesPointsByName("Motors");
        assertThat(listSalesPointsByName).isNotNull().hasSize(2);
    }

    @Test
    public void shouldDeleteEntitiesInBatches() throws SQLException {
        final DataSetConfig dataSetConfig = new DataSetConfig("car-batch.yml");
        RiderDSL.DBUnitConfigDSL riderDSL = RiderDSL.withConnection(dataSource.getConnection())
                .withDataSetConfig(dataSetConfig);
        riderDSL.createDataSet();
        assertThat(carCrud.count()).isEqualTo(10L);
        //batch equal to entities size
        int deleted = carCrud.removeBatch(carCrud.criteria().getResultList(), 10);
        assertThat(deleted).isEqualTo(10);
        assertThat(carCrud.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        assertThat(carCrud.count()).isEqualTo(10L);
        //batch < entities size
        deleted = carCrud.removeBatch(carCrud.criteria().getResultList(), 9);
        assertThat(deleted).isEqualTo(10);
        assertThat(carCrud.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // batch > entities size
        deleted = carCrud.removeBatch(carCrud.criteria().getResultList(), 11);
        assertThat(deleted).isEqualTo(10);
        assertThat(carCrud.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // invalid batch size, use default batch size
        deleted = carCrud.removeBatch(carCrud.criteria().getResultList(), 0);
        assertThat(deleted).isEqualTo(10);
        assertThat(carCrud.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // small batch size
        deleted = carCrud.removeBatch(carCrud.criteria().getResultList(), 1);
        assertThat(deleted).isEqualTo(10);
        assertThat(carCrud.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // small batch size
        deleted = carCrud.removeBatch(carCrud.criteria().getResultList(), 3);
        assertThat(deleted).isEqualTo(10);
        assertThat(carCrud.count()).isEqualTo(0L);
        riderDSL.createDataSet();
        // 'prime' batch size
        deleted = carCrud.removeBatch(carCrud.criteria().getResultList(), 7);
        assertThat(deleted).isEqualTo(10);
        assertThat(carCrud.count()).isEqualTo(0L);
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
