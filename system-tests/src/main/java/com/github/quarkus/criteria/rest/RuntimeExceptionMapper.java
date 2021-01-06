package com.github.quarkus.criteria.rest;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.Map;

public class RuntimeExceptionMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException e) {
        final Map<String, String> error = Map.of("message", e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }
}
