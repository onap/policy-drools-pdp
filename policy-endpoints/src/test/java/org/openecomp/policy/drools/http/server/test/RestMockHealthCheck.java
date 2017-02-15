package org.openecomp.policy.drools.http.server.test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Path("/")
public class RestMockHealthCheck {
	
    @GET
    @Path("pap/test")
    @Produces(MediaType.APPLICATION_JSON)
    public Response papHealthCheck() {   
		return Response.status(Status.OK).entity("All Alive").build();
    }
    
    @GET
    @Path("pdp/test")
    @Produces(MediaType.APPLICATION_JSON)
    public Response pdpHealthCheck() {   
		return Response.status(Status.INTERNAL_SERVER_ERROR).entity("At least some Dead").build();
    }


}
