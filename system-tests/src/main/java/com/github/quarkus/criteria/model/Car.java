/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.quarkus.criteria.model;


import com.github.quarkus.criteria.runtime.model.BaseEntity;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author rmpestano
 */
@Entity
@Table(name = "car")
public class Car extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(name = "model")
    private String model;

    @Column(name = "name")
    private String name;

    @Column(name = "price")
    private Double price;

    @ManyToOne(fetch = FetchType.LAZY)
    private Brand brand;

    @OneToMany(mappedBy = "car", fetch = FetchType.LAZY)
    private List<CarSalesPoint> carSalesPoints = new ArrayList<>();

    @Version
    private Integer version;

    public Car() {
    }

    public Car(Integer id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getPrice() {
        return price;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Car setName(String name) {
        this.name = name;
        return this;
    }

    public Car setModel(String model) {
        this.model = model;
        return this;
    }

    public Car setPrice(Double price) {
        this.price = price;
        return this;
    }

    public Car setId(Integer id) {
        this.id = id;
        return this;
    }

    public Brand getBrand() {
        return brand;
    }

    public Car setBrand(Brand brand) {
        this.brand = brand;
        return this;
    }

    public List<CarSalesPoint> getCarSalesPoints() {
        return carSalesPoints;
    }

    public void setCarSalesPoints(List<CarSalesPoint> carSalesPoints) {
        this.carSalesPoints = carSalesPoints;
    }

    public boolean hasModel() {
        return model != null && !"".equals(model.trim());
    }

    public boolean hasName() {
        return name != null && !"".equals(name.trim());
    }

    public boolean hasBrand() {
        return brand != null;
    }

    public boolean hasSalesPoint() {
        return carSalesPoints != null && !carSalesPoints.isEmpty();
    }

    @Override
    public String toString() {
        return "Car{" +
                "id=" + id +
                ", model='" + model + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price + '\'' +
                ", brand=" + (brand != null ? brand.getName() : "") +
                '}';
    }

}
