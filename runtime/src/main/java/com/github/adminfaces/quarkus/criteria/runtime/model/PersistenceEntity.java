package com.github.adminfaces.quarkus.criteria.runtime.model;

import java.io.Serializable;

public interface PersistenceEntity extends Serializable {

    <ID extends Serializable> ID getId();

}
