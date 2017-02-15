package org.openecomp.policy.drools.healthcheck;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.openecomp.policy.drools.healthcheck.HealthCheck.Reports;

@Path("/")
public class RestHealthCheck {
	
    @GET
    @Path("{a:healthcheck|test}")
    @Produces(MediaType.APPLICATION_JSON)
    public Reports healthcheck() {  
    	Reports reports = HealthCheck.monitor.healthCheck(); 
		return reports;
    }
    
    @GET
    @Path("healthcheck/configuration")
    @Produces(MediaType.APPLICATION_JSON)
    public HealthCheck configuration() {  
    	return HealthCheck.monitor;
    }
}
