package com.github.adminfaces.quarkus.criteria.runtime.service;

import javax.inject.Qualifier;
import java.lang.annotation.*;

/**
 * Marker interface to allow generic CrudServive injection: 
 * <code>
     {@literal @}Inject 
     {@literal @}Service 
     CrudService&lt;Entity,PK&gt; genericService; 
 * </code>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.TYPE})
@Qualifier
@Documented
public @interface Service {
}
