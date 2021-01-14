package com.github.quarkus.criteria.runtime.criteria.example;

import org.apache.deltaspike.data.api.criteria.Criteria;

import javax.persistence.metamodel.Attribute;
import java.util.Objects;

class JoinInfo {

    Attribute attribute;
    Criteria criteria;
    Criteria joinCriteria;

    public JoinInfo(Attribute attribute, Criteria criteria, Criteria joinCriteria) {
        this.attribute = attribute;
        this.criteria = criteria;
        this.joinCriteria = joinCriteria;
    }

    @Override
    public int hashCode() {
        return Objects.hash(attribute);
    }
}
