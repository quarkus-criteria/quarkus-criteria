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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MultiSort multiSort = (MultiSort) o;
        return sort == multiSort.sort &&
                Objects.equals(sortField, multiSort.sortField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sort);
    }
}
