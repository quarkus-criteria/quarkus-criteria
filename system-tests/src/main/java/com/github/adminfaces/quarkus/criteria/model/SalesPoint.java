package com.github.adminfaces.quarkus.criteria.model;

import com.github.adminfaces.quarkus.criteria.runtime.model.PersistenceEntity;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "sales_point")
public class SalesPoint implements PersistenceEntity {

    @EmbeddedId
    private SalesPointPK salesPointPK;

    private String name;

    private String address;

    public SalesPoint() {
    }

    public SalesPoint(SalesPointPK salesPointPK) {
        this.salesPointPK = salesPointPK;
    }

    public SalesPointPK getSalesPointPK() {
        return salesPointPK;
    }

    public void setSalesPointPK(SalesPointPK salesPointPK) {
        this.salesPointPK = salesPointPK;
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

    @Override
    public SalesPointPK getId() {
        return salesPointPK;
    }

    @Override
    public String toString() {
        return "SalesPoint{" +
                "name='" + name + '\'' +
                '}';
    }
}
