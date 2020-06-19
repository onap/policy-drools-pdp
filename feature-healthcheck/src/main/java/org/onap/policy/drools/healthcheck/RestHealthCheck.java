/*-
 * ============LICENSE_START=======================================================
 * feature-healthcheck
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.onap.policy.common.endpoints.http.server.YamlMessageBodyHandler;
import org.onap.policy.drools.healthcheck.HealthCheck.Reports;

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

    @GET
    @Path("healthcheck")
    @ApiOperation(
            value = "Perform a system healthcheck",
            notes = "Provides healthy status of the PDP-D plus the components defined in its "
                + "configuration by using a REST interface",
            response = Reports.class
            )
    public Response healthcheck() {
        return Response.status(Response.Status.OK).entity(HealthCheckConstants.getManager().healthCheck()).build();
    }

    @GET
    @Path("healthcheck/configuration")
    @ApiOperation(
            value = "Configuration",
            notes = "Provides the Healthcheck server configuration and monitored REST clients",
            response = HealthCheck.class
            )
    public HealthCheck configuration() {
        return HealthCheckConstants.getManager();
    }
}
