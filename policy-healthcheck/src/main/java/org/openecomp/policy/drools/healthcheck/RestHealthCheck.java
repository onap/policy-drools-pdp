package org.openecomp.policy.drools.healthcheck;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.openecomp.policy.drools.healthcheck.HealthCheck.Reports;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

@Path("/")
@Api
@Produces(MediaType.APPLICATION_JSON)
@SwaggerDefinition(
    info = @Info(
        description = "PDP-D Healthcheck Service",
        version = "v1.0",
        title = "PDP-D Healthcheck"
    ),
    consumes = {MediaType.APPLICATION_JSON},
    produces = {MediaType.APPLICATION_JSON},
    schemes = {SwaggerDefinition.Scheme.HTTP},
    tags = {
        @Tag(name = "pdp-d-healthcheck", description = "Drools PDP Healthcheck Operations")
    }
)
public class RestHealthCheck {
	
    @GET
    @Path("healthcheck")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
        	value="Perform a system healthcheck", 
        	notes="Provides healthy status of the PDP-D plus the components defined in its configuration by using a REST interface",
        	response=Reports.class
    )
    public Response healthcheck() {  
		return Response.status(Response.Status.OK).entity(HealthCheck.monitor.healthCheck()).build();
    }
    
    @GET
    @Path("healthcheck/configuration")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
        	value="Configuration", 
        	notes="Provides the Healthcheck server configuration and monitored REST clients",
        	response=HealthCheck.class
    )
    public HealthCheck configuration() {  
    	return HealthCheck.monitor;
    }
}
