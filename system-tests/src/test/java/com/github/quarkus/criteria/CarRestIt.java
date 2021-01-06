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
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
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
        String json =  given()
                .when().get("/api/cars/1")
                .then()
                .statusCode(OK.getStatusCode()).extract().asString();

        JsonObject jsonObject = Json.createReader(new StringReader(json)).readObject();
        assertThat(jsonObject.getString("name")).isEqualTo("Fusion");
    }

    @Test
    public void shouldPaginateCars() {
         given()
                .param("first", "0")
                .param("pageSize","2")
                .when().get("/api/cars/")
                .then()
                .statusCode(OK.getStatusCode())
                .body("", hasSize(2))
                .body("name", hasItems("Fusion", "Sentra")) //it's ordered by id see CarService#configPagination
                .body("name", not(hasItems("Model S", "Model X")));
        given()
                .param("first", "2")
                .param("pageSize","2")
                .when().get("/api/cars/")
                .then()
                .statusCode(OK.getStatusCode())
                .body("", hasSize(2))
                .body("name", hasItems("Model S", "Model X")) //it's ordered by id see CarService#configPagination
                .body("name", not(hasItems("Fusion", "Sentra")));

    }
}
