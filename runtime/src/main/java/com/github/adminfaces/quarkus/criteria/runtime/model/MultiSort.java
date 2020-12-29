package com.github.adminfaces.quarkus.criteria.runtime.model;

import java.util.Objects;

/**
 *
 * @author rmpestano
 */
public class MultiSort {
    
    private final Sort adminSort;
    private final String sortField;

    public MultiSort(Sort adminSort, String sortField) {
        this.adminSort = adminSort;
        this.sortField = sortField;
    }

    public Sort getAdminSort() {
        return adminSort;
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
        if (this.adminSort != other.adminSort) {
            return false;
        }
        return true;
    }
}
