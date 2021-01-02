package com.github.quarkus.criteria.runtime.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class which holds database pagination metadata
 *
 * @author rmpestano
 * @param <T> the entity which this filter refers to.
 */
public class Filter<T extends PersistenceEntity> implements Serializable {

    private T entity;
    private int first;
    private int pageSize;
    private String sortField;
    private Sort sort;
    private List<MultiSort> multiSort = new ArrayList<>();
    private Map<String, Object> params = new HashMap<>();

    public Filter() {
    }

    public Filter(T entity) {
        this.entity = entity;
    }

    public Filter setFirst(int first) {
        this.first = first;
        return this;
    }

    public int getFirst() {
        return first;
    }

    public Filter setPageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public int getPageSize() {
        return pageSize;
    }

    public Filter setSortField(String sortField) {
        this.sortField = sortField;
        return this;
    }

    public String getSortField() {
        return sortField;
    }

    public Filter setSort(Sort sort) {
        this.sort = sort;
        return this;
    }

    public Sort getSort() {
        return sort;
    }

    public Filter setParams(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    public List<MultiSort> getMultiSort() {
        return multiSort;
    }

    public Filter setMultiSort(List<MultiSort> multiSort) {
        this.multiSort = multiSort;
        return this;
    }

    public Filter addMultSort(Sort sort, String sortField) {
        if (!multiSort.contains(sort)) {
            multiSort.add(new MultiSort(sort, sortField));
        }
        return this;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public T getEntity() {
        return entity;
    }

    public Filter setEntity(T entity) {
        this.entity = entity;
        return this;
    }

    public Filter addParam(String key, Object value) {
        getParams().put(key, value);
        return this;
    }

    public boolean hasParam(String key) {
        return getParams().containsKey(key) && getParam(key) != null;
    }

    public Object getParam(String key) {
        return getParams().get(key);
    }

    public <X> X getParam(String key, Class<X> type) {
        return hasParam(key) ? (X) getParams().get(key) : null;
    }

    public String getStringParam(String key) {
        return hasParam(key) ? getParam(key).toString() : null;
    }

    public Integer getIntParam(String key) {
        return hasParam(key) ? Integer.parseInt(getStringParam(key)) : null;
    }

    public Long getLongParam(String key) {
        return hasParam(key) ? Long.parseLong(getStringParam(key)) : null;
    }

    public Boolean getBooleanParam(String key) {
        return hasParam(key) ? Boolean.parseBoolean(getStringParam(key)) : null;
    }

    public Double getDoubleParam(String key) {
        return hasParam(key) ? Double.parseDouble(getStringParam(key)) : null;
    }
}
