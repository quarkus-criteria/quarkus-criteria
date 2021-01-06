package com.github.quarkus.criteria.rest;

import com.github.quarkus.criteria.model.*;
import com.github.quarkus.criteria.runtime.model.Filter;
import com.github.quarkus.criteria.runtime.model.SortType;
import com.github.quarkus.criteria.runtime.service.CrudService;
import com.github.quarkus.criteria.service.CarService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.Response.*;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@ApplicationScoped
@Path("cars")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CarRest {

    @Inject
    CarService carService;

    /**
     * curl -X GET http://localhost:8080/api/cars/1 -v
     */
    @GET
    @Path("/{id : \\d+}")
    public Response findById(@PathParam("id") final Integer id) {
        return ofNullable(carService.findById(id))
                .map(Response::ok)
                .orElse(status(NOT_FOUND))
                .build();
    }

    /**
     * curl -X GET http://localhost:8080/api/cars -v
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@QueryParam("first") @DefaultValue("0") Integer startPosition,
                         @QueryParam("pageSize") @DefaultValue("10") Integer maxResult,
                         @QueryParam("sortField") @DefaultValue("id") String sortField,
                         @QueryParam("sortField") @DefaultValue("ASCENDING") SortType sortType,
                         @QueryParam("name") @DefaultValue("") String name,
                         @QueryParam("model") @DefaultValue("") String model,
                         @QueryParam("price") Double price,
                         @QueryParam("brandId") Long brandId
                         ) {

        Filter<Car> carFilter = new Filter<>(new Car().setName(name)
                .setModel(model)
                .setPrice(price))
                .setFirst(startPosition)
                .setPageSize(maxResult)
                .setSortType(sortType)
                .setSortField(sortField);
        if(brandId != null) {
            carFilter.getEntity().setBrand(new Brand(brandId));
        }

        return ok(carService.paginate(carFilter)).build();
    }

    /**
     * curl -X POST http://localhost:8080/api/car  -H "Content-Type: application/json" -d '{"name":"Car name", "model":"car model", "price":"999.9"}' -v
     */
    @POST
    public Response create(Car car, @Context UriInfo uriInfo) {
        final Car created = carService.insert(car);
        URI createdURI = uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.getId())).build();
        return Response.created(createdURI).build();
    }

    /**
     * curl -X POST http://localhost:8080/api/cars/example -H "Content-Type: application/json" -d '{"name":"Car name", "model":"car model", "price":"999.9"}' -v
     */
    @POST
    @Path("/example")
    @Produces(MediaType.APPLICATION_JSON)
    public Response findByExample(Car car) {
        return ok(carService.exampleBuilder.of(car).build().getResultList()).build();
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(Car car) {
        return ok(carService.update(car)).build();
    }

    @DELETE
    @Path("/{id : \\d+}")
    public Response delete(@PathParam("id") final Long id) {
        carService.deleteById(id);
        return noContent().build();
    }

    @POST
    @Path("delete-batch")
    public Response deleteInBatches(List<Car> carsToDelete, @QueryParam("batchSize") @DefaultValue("10") Integer batchSize) {
        carService.deleteBatch(carsToDelete, batchSize);
        return noContent().build();
    }
}


