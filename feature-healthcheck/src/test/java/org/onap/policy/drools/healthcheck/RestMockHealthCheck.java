/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2018,2022 AT&T Intellectual Property. All rights reserved.
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

import static org.awaitility.Awaitility.await;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.concurrent.TimeUnit;
import org.onap.policy.common.endpoints.http.server.YamlMessageBodyHandler;

@Path("/")
public class RestMockHealthCheck {

    protected static final String OK_MESSAGE = "All Alive";
    protected static volatile boolean stuck = false;
    protected static volatile long WAIT = 15;

    @GET
    @Path("healthcheck/test")
    @Produces({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
    public Response papHealthCheck() {
        return Response.status(Status.OK).entity(OK_MESSAGE).build();
    }

    @GET
    @Path("healthcheck/stuck")
    @Produces({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
    public Response stuck() {
        await().atMost(WAIT, TimeUnit.SECONDS).until(() -> !stuck);
        return Response.status(Status.OK).entity("I may be stuck: " + stuck).build();
    }
}
