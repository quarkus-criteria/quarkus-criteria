package com.github.quarkus.criteria;

import com.github.database.rider.core.configuration.DataSetConfig;
import com.github.database.rider.core.dsl.RiderDSL;
import com.github.quarkus.criteria.infra.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.Response.Status.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
public class CarRestIt {

    @BeforeAll
    public static void initDB() throws SQLException {
        Connection connection = DriverManager.getConnection(System.getProperty("quarkus.datasource.jdbc.url"), "car", "car");
        RiderDSL.withConnection(connection)
                .withDataSetConfig(new DataSetConfig("cars.yml"))
                .createDataSet();
    }

    @Test
    public void shouldFindCarById() {
        given()
                .when().get("/api/cars/1")
                .then()
                .statusCode(OK.getStatusCode())
                .body("name", equalTo("Fusion"));
    }

    @Test
    public void shouldListCars() {
        given()
                .when().get("/api/cars")
                .then()
                .statusCode(OK.getStatusCode())
                .body("", hasSize(4))
                .body("name", hasItems("Fusion", "Sentra", "Model S", "Model X"));
    }

    @Test
    public void shouldPaginateCars() {
        given()
                .param("first", "0")
                .param("pageSize", "2")
                .when().get("/api/cars/")
                .then()
                .statusCode(OK.getStatusCode())
                .body("", hasSize(2))
                .body("name", hasItems("Fusion", "Sentra")) //it's ordered by id see CarService#configPagination
                .body("name", not(hasItems("Model S", "Model X")));
        given()
                .param("first", "2")
                .param("pageSize", "2")
                .when().get("/api/cars/")
                .then()
                .statusCode(OK.getStatusCode())
                .body("", hasSize(2))
                .body("name", hasItems("Model S", "Model X")) //it's ordered by id see CarService#configPagination
                .body("name", not(hasItems("Fusion", "Sentra")));
    }

    @Test
    public void shouldDeleteCarById() throws SQLException {
        try {
            given()
                    .when().delete("/api/cars/1")
                    .then()
                    .statusCode(NO_CONTENT.getStatusCode());

            given()
                    .when().get("/api/cars/1")
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode());
        } finally {
            initDB();
        }
    }

    @Test
    public void shouldFindCarByExample() throws SQLException {
        JsonObject carExample = Json.createObjectBuilder()
                .add("model", "S")
                .build();
        given()
                .body(carExample.toString())
                .contentType("application/json")
                .when().post("/api/cars/example")
                .then()
                .statusCode(OK.getStatusCode())
                .body("", hasSize(2))
                .body("name", hasItems("Model S", "Model X"));
    }

    @Test
    public void shouldNotUpdateCarInexisting() {
        JsonObject carToUpdate = Json.createObjectBuilder()
                .add("id", 99)
                .build();
        given().
                body(carToUpdate.toString()).
                contentType("application/json").
                when().
                put("/api/cars/99").  //dataset has car with id =1
                then().
                statusCode(BAD_REQUEST.getStatusCode())
                .body("message", equalTo("Cannot update inexisting car."));

    }

    @Test
    public void shouldUpdateCar() throws SQLException {
        JsonObject carToUpdate = Json.createObjectBuilder()
                .add("id", 1)
                .add("version", 0)
                .add("model", "Titanium")
                .add("name", "Fusion updated")
                .add("price", "20.999")
                .add("brand", Json.createObjectBuilder()
                        .add("id", 1).build())
                .add("carSalesPoints", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("carSalesPointId", Json.createObjectBuilder()
                                        .add("carId", 1)
                                        .add("salesPointId", Json.createObjectBuilder()
                                                .add("id1", 1)
                                                .add("id2", 2).build()) //salesPointId
                                        .build() //carSalesPointId
                                ).build())//carSalesPoint
                        .add(Json.createObjectBuilder().add("carSalesPointId", Json.createObjectBuilder()
                                        .add("carId", 1)
                                        .add("salesPointId", Json.createObjectBuilder()
                                                .add("id1", 1)
                                                .add("id2", 3).build()) //salesPointId
                                        .build() //carSalesPointId
                                ).build()//carSalesPoint
                        ).build())//carSalesPoints
                .build();
        try {
            given().
                    body(carToUpdate.toString()).
                    contentType("application/json").
                    when().
                    put("/api/cars/1").  //dataset has car with id =1
                    then().
                    statusCode(NO_CONTENT.getStatusCode());
            given()
                    .when().get("/api/cars/1")
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("name", equalTo("Fusion updated"))
                    .body("version", equalTo(1))
                    .body("price", equalTo(20.999F));

        } finally {
            initDB();
        }
    }
}
