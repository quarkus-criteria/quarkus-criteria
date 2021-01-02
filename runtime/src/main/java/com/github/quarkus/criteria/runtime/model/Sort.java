package com.github.quarkus.criteria.runtime.model;

/**
 * @author rmpestano
 */
public enum Sort {

    ASCENDING, DESCENDING, UNSORTED;

    public boolean isAscending() {
        return ASCENDING.equals(this);
    }
}
