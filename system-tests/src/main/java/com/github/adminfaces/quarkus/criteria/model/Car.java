/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.adminfaces.quarkus.criteria.model;


import com.github.adminfaces.quarkus.criteria.runtime.model.BaseEntity;

import javax.persistence.*;
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

    @OneToOne
    private Brand brand;

    @ManyToMany
    private List<SalesPoint> salesPoints;

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

    public Double getPrice() {
        return price;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Car model(String model) {
        this.model = model;
        return this;
    }

    public Car price(Double price) {
        this.price = price;
        return this;
    }

    public Car name(String name) {
        this.name = name;
        return this;
    }

    public Brand getBrand() {
        return brand;
    }

    public Car setBrand(Brand brand) {
        this.brand = brand;
        return this;
    }

    public List<SalesPoint> getSalesPoints() {
        return salesPoints;
    }

    public Car setSalesPoints(List<SalesPoint> salesPoints) {
        this.salesPoints = salesPoints;
        return this;
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
        return salesPoints != null && !salesPoints.isEmpty();
    }

    @Override
    public String toString() {
        return "Car{" +
                "id=" + id +
                ", model='" + model + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price + '\'' +
                ", brand=" + (brand != null ? brand.getName() : "") +
                ", salesPoints=" + (salesPoints != null ? salesPoints : "") +
                '}';
    }
}
