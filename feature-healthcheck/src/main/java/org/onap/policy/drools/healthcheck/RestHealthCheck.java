/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019, 2022 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2023 Nordix Foundation.
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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
@Produces({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
public class RestHealthCheck implements HealthcheckApi {

    /**
     * System healthcheck per configuration.
     */

    @Override
    @GET
    @Path("healthcheck")
    public Response healthcheck() {
        var summary = getHealthcheckManager().healthCheck();
        return getResponse(summary);
    }

    /**
     * Engine Healthcheck.
     */

    @Override
    @GET
    @Path("healthcheck/engine")
    public Response engine() {
        var summary = getHealthcheckManager().engineHealthcheck();
        return getResponse(summary);
    }

    /**
     * Healthcheck on the controllers.
     */

    @Override
    @GET
    @Path("healthcheck/controllers")
    public Response controllers() {
        var summary = getHealthcheckManager().controllerHealthcheck();
        return getResponse(summary);
    }

    /**
     * Healthcheck a controller.
     */

    @Override
    @GET
    @Path("healthcheck/controllers/{controllerName}")
    public Response controllersName(@PathParam("controllerName") String controllerName) {
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

    @Override
    @GET
    @Path("healthcheck/clients")
    public Response clients() {
        var summary = getHealthcheckManager().clientHealthcheck();
        return getResponse(summary);
    }

    /**
     * Healthcheck a on a Http Client.
     */

    @Override
    @GET
    @Path("healthcheck/clients/{clientName}")
    public Response clientsName(@PathParam("clientName") String clientName) {
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
