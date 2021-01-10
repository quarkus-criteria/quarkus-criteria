/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.quarkus.criteria.model;


import com.github.quarkus.criteria.runtime.model.BaseEntity;

import javax.persistence.*;
import java.util.List;
import java.util.Set;

/**
 * @author rmpestano
 */
@Entity
@Table(name = "brand")
public class Brand extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "brand", fetch = FetchType.LAZY)
    private Set<Car> cars;

    public Brand() {
    }

    public Brand(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Brand setName(String name) {
        this.name = name;
        return this;
    }

    public Set<Car> getCars() {
        return cars;
    }

    public Brand setCars(Set<Car> cars) {
        this.cars = cars;
        return this;
    }

    @Override
    public String toString() {
        return "Brand{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
