package com.github.quarkus.criteria.model;

import com.github.quarkus.criteria.runtime.model.PersistenceEntity;

import javax.persistence.*;
import java.util.Objects;

@Entity(name = "CarSalesPoint")
@Table(name = "car_sales_point")
public class CarSalesPoint implements PersistenceEntity {

    @EmbeddedId
    private CarSalesPointId carSalesPointId;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("id")
    private Car car;

    @ManyToOne
    @JoinColumns({@JoinColumn(name = "SALESPOINTS_ID1"), @JoinColumn(name = "SALESPOINTS_ID2")})
    @MapsId("salesPointPK")
    private SalesPoint salesPoint;

    public CarSalesPoint() {
    }

    public CarSalesPoint(CarSalesPointId carSalesPointId) {
        this.carSalesPointId = carSalesPointId;
    }

    public CarSalesPoint(Car car, SalesPoint salesPoint) {
        this.car = car;
        this.salesPoint = salesPoint;
        this.carSalesPointId = new CarSalesPointId(car.getId(), salesPoint.getId());
    }

    public CarSalesPointId getCarSalesPointId() {
        return carSalesPointId;
    }

    public void setCarSalesPointId(CarSalesPointId carSalesPointId) {
        this.carSalesPointId = carSalesPointId;
    }

    public Car getCar() {
        return car;
    }

    public CarSalesPoint setCar(Car car) {
        this.car = car;
        return this;
    }

    public SalesPoint getSalesPoint() {
        return salesPoint;
    }

    public CarSalesPoint setSalesPoint(SalesPoint salesPoint) {
        this.salesPoint = salesPoint;
        return this;
    }

    @Override
    public String toString() {
        return "CarSalesPoint{" +
                "car=(" + car != null ? (car.getId() + ", "+car.getName()) : ""+")"+
                ", salesPoint=" + salesPoint != null ? salesPoint.toString() : "" +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CarSalesPoint that = (CarSalesPoint) o;
        return Objects.equals(car, that.car) &&
                Objects.equals(salesPoint, that.salesPoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(car, salesPoint);
    }

    @Override
    public  CarSalesPointId getId() {
        return carSalesPointId;
    }
}
