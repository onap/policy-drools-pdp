/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019, 2022 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.healthcheck;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.onap.policy.common.endpoints.http.client.HttpClientFactory;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.YamlMessageBodyHandler;
import org.onap.policy.drools.healthcheck.HealthCheck.Reports;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyControllerFactory;

/**
 * REST Healthcheck JAX-RS.
 */
@Path("/")
@Api
@Produces({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@SwaggerDefinition(
    info = @Info(
        description = "PDP-D Healthcheck Service",
        version = "v1.0",
        title = "PDP-D Healthcheck"
    ),
    consumes = {MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML},
    produces = {MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML},
    schemes = {SwaggerDefinition.Scheme.HTTP},
    tags = {
        @Tag(name = "pdp-d-healthcheck", description = "Drools PDP Healthcheck Operations")
    }
    )
public class RestHealthCheck {

    /**
     * System healthcheck per configuration.
     */

    @GET
    @Path("healthcheck")
    @ApiOperation(
            value = "Perform a system healthcheck",
            notes = "Provides healthy status of the PDP-D plus the components defined in its "
                + "configuration by using a REST interface",
            response = Reports.class
            )
    public Response healthcheck() {
        var summary = getHealthcheckManager().healthCheck();
        return getResponse(summary);
    }

    /**
     * Engine Healthcheck.
     */

    @GET
    @Path("healthcheck/engine")
    @ApiOperation(
            value = "Engine Healthcheck",
            notes = "Provides a Healthcheck on the engine",
            response = HealthCheck.class
    )
    public Response engine() {
        var summary = getHealthcheckManager().engineHealthcheck();
        return getResponse(summary);
    }

    /**
     * Healthcheck on the controllers.
     */

    @GET
    @Path("healthcheck/controllers")
    @ApiOperation(
            value = "Controllers Healthcheck",
            notes = "Provides a Healthcheck on the configured controllers",
            response = Reports.class
    )
    public Response controllers() {
        var summary = getHealthcheckManager().controllerHealthcheck();
        return getResponse(summary);
    }

    /**
     * Healthcheck a controller.
     */

    @GET
    @Path("healthcheck/controllers/{controllerName}")
    @ApiOperation(
            value = "Controller Healthcheck",
            notes = "Provides a Healthcheck on a configured controller",
            response = Reports.class
    )
    public Response controllers(@ApiParam(value = "Policy Controller Name",
            required = true) @PathParam("controllerName") String controllerName) {
        try {
            var controller = getControllerFactory().get(controllerName);
            var summary = getHealthcheckManager().controllerHealthcheck(controller);
            return getResponse(summary);
        } catch (final IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (final IllegalStateException e) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
        }
    }

    /**
     * Healthcheck on the Http Clients per configuration.
     */

    @GET
    @Path("healthcheck/clients")
    @ApiOperation(
            value = "Http Clients Healthcheck",
            notes = "Provides a Healthcheck on the configured HTTP clients",
            response = Reports.class
    )
    public Response clients() {
        var summary = getHealthcheckManager().clientHealthcheck();
        return getResponse(summary);
    }

    /**
     * Healthcheck a on a Http Client.
     */

    @GET
    @Path("healthcheck/clients/{clientName}")
    @ApiOperation(
            value = "Http Client Healthcheck",
            notes = "Provides a Healthcheck on a configured HTTP client",
            response = Reports.class
    )
    public Response clients(@ApiParam(value = "Http Client Name",
            required = true) @PathParam("clientName") String clientName) {
        try {
            var client = getClientFactory().get(clientName);
            var summary = getHealthcheckManager().clientHealthcheck(client);
            return getResponse(summary);
        } catch (final IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    protected Response getResponse(Reports summary) {
        return Response.status(summary.isHealthy() ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(summary).build();
    }

    protected HttpClientFactory getClientFactory() {
        return HttpClientFactoryInstance.getClientFactory();
    }

    protected PolicyControllerFactory getControllerFactory() {
        return PolicyControllerConstants.getFactory();
    }

    protected HealthCheck getHealthcheckManager() {
        return HealthCheckConstants.getManager();
    }

}
