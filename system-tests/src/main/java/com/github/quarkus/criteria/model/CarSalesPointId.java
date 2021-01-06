package com.github.quarkus.criteria.model;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CarSalesPointId implements Serializable {

    @Column(name = "CAR_ID")
    private Integer carId;

    @Column(name = "SALESPOINT_ID")
    private SalesPointPK salesPointId;

    public CarSalesPointId() {
    }

    public CarSalesPointId(Integer carId, SalesPointPK salesPointId) {
        this.carId = carId;
        this.salesPointId = salesPointId;
    }

    public Integer getCarId() {
        return carId;
    }

    public void setCarId(Integer carId) {
        this.carId = carId;
    }

    public SalesPointPK getSalesPointId() {
        return salesPointId;
    }

    public void setSalesPointId(SalesPointPK salesPointId) {
        this.salesPointId = salesPointId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CarSalesPointId that = (CarSalesPointId) o;
        return Objects.equals(carId, that.carId) &&
                Objects.equals(salesPointId, that.salesPointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carId, salesPointId);
    }
}
