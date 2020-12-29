package com.github.adminfaces.quarkus.criteria.runtime.model;

/**
 * Created by rmpestano
 */
public enum Sort {

    ASCENDING, DESCENDING, UNSORTED;

    public boolean isAscending() {
        return ASCENDING.equals(this);
    }
}
