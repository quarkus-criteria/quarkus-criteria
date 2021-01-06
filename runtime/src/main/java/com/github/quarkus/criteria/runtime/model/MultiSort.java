package com.github.quarkus.criteria.runtime.model;

import java.util.Objects;

/**
 * @author rmpestano
 */
public class MultiSort {
    
    private final SortType sort;
    private final String sortField;

    public MultiSort(SortType sort, String sortField) {
        this.sort = sort;
        this.sortField = sortField;
    }

    public SortType getSort() {
        return sort;
    }

    public String getSortField() {
        return sortField;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MultiSort other = (MultiSort) obj;
        if (!Objects.equals(this.sortField, other.sortField)) {
            return false;
        }
        if (this.sort != other.sort) {
            return false;
        }
        return true;
    }
}
