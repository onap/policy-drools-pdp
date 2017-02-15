package org.openecomp.policy.drools.http.server.test;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.openecomp.policy.drools.http.server.HttpServletServer;

@Path("/junit/endpoints")
public class RestEndpoints {

    @GET
    @Path("http/servers")
    @Produces(MediaType.TEXT_PLAIN)
    public String httpServers() {   
    	List<HttpServletServer> servers = 
    			HttpServletServer.factory.inventory();
    	return servers.toString();
    }
    
    
}
