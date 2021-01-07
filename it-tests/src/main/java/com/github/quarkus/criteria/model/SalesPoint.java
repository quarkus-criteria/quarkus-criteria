package com.github.quarkus.criteria.model;

import com.github.quarkus.criteria.runtime.model.PersistenceEntity;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "sales_point")
public class SalesPoint implements PersistenceEntity {

    @EmbeddedId
    private SalesPointPK salesPointPK;

    private String name;

    private String address;

    @OneToMany(mappedBy = "salesPoint", orphanRemoval = true)
    private List<CarSalesPoint> cars = new ArrayList<>();

    public SalesPoint() {
    }

    public SalesPoint(SalesPointPK salesPointPK) {
        this.salesPointPK = salesPointPK;
    }

    public SalesPointPK getSalesPointPK() {
        return salesPointPK;
    }

    public SalesPoint setSalesPointPK(SalesPointPK salesPointPK) {
        this.salesPointPK = salesPointPK;
        return this;
    }

    public String getName() {
        return name;
    }

    public SalesPoint setName(String name) {
        this.name = name;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public SalesPoint setAddress(String address) {
        this.address = address;
        return this;
    }

    public List<CarSalesPoint> getCars() {
        return cars;
    }

    public void setCars(List<CarSalesPoint> cars) {
        this.cars = cars;
    }

    @Override
    public SalesPointPK getId() {
        return salesPointPK;
    }


    @Override
    public String toString() {
        return "SalesPoint{" +
                "salesPointPK=" + salesPointPK +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SalesPoint that = (SalesPoint) o;
        return Objects.equals(salesPointPK, that.salesPointPK);
    }

    @Override
    public int hashCode() {
        return Objects.hash(salesPointPK);
    }
}
