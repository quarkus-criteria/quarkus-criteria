package com.github.quarkus.criteria.runtime.service;

import javax.inject.Qualifier;
import java.lang.annotation.*;

/**
 * Marker interface to allow generic CrudService injection:
 * <code>
     {@literal @}Inject 
     {@literal @}Service 
     CrudService&lt;Entity&gt; genericService;
 * </code>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.TYPE})
@Qualifier
@Documented
public @interface Service {
}
