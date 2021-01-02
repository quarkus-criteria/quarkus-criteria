package com.github.quarkus.criteria;

import com.github.quarkus.criteria.model.Car;
import com.github.database.rider.cdi.api.DBRider;
import com.github.database.rider.core.api.dataset.DataSet;
import com.github.quarkus.criteria.criteria.CarCriteria;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DataSet(cleanBefore = true)
@DBRider
@QuarkusTest
public class CarCriteriaIt {

    @Inject
    CarCriteria carCriteria;

    @Test
    @DataSet("cars.yml")
    public void shouldListCars() {
        List<Car> cars = carCriteria.criteria().getResultList();
        assertThat(cars).hasSize(4);
    }

    @Test
    @DataSet("ferrari-and-porche.yml")
    public void shouldGetTotalPriceByModel() {
        Double price = carCriteria.getTotalPriceByModel("ferrari");
        assertThat(price).isEqualTo(112998.9);
        price = carCriteria.getTotalPriceByModel("PORCHE");
        assertThat(price).isEqualTo(11390.3);
    }
}
