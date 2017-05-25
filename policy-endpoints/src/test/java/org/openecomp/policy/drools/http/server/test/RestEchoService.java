package org.openecomp.policy.drools.http.server.test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value="echo")
@Path("/junit/echo")
public class RestEchoService {
	
    @GET
    @Path("{word}")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation(
        	value="echoes back whatever received"
    )
    public String echo(@PathParam("word") String word) {   
    	return word;
    }

}
