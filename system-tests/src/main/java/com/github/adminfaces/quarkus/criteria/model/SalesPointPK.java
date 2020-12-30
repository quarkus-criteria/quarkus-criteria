package com.github.adminfaces.quarkus.criteria.model;

import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SalesPointPK implements Serializable{

    private Long id1;

    private Long  id2;

    public SalesPointPK() {
    }

    public SalesPointPK(Long id1, Long id2) {
        this.id1 = id1;
        this.id2 = id2;
    }

    public Long getId1() {
        return id1;
    }

    public void setId1(Long id1) {
        this.id1 = id1;
    }

    public Long getId2() {
        return id2;
    }

    public void setId2(Long id2) {
        this.id2 = id2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SalesPointPK that = (SalesPointPK) o;
        return Objects.equals(id1, that.id1) &&
                Objects.equals(id2, that.id2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id1, id2);
    }

    @Override
    public String toString() {
        return "SalesPointPK{" +
                "id1=" + id1 +
                ", id2=" + id2 +
                '}';
    }
}
