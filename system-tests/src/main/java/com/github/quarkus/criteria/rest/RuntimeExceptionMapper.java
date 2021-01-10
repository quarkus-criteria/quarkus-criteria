package com.github.quarkus.criteria.rest;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class RuntimeExceptionMapper implements ExceptionMapper<RuntimeException> {

    private static final Logger LOG = Logger.getLogger(RuntimeExceptionMapper.class.getName());

    @Override
    public Response toResponse(RuntimeException e) {
        final Map<String, String> error = Map.of("message", e.getMessage());
        LOG.log(Level.SEVERE, "", e);
        return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }
}
