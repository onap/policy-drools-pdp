/*-
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.server.restful;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.onap.policy.common.endpoints.http.server.YamlMessageBodyHandler;
import org.onap.policy.drools.lifecycle.LifecycleFeature;
import org.onap.policy.drools.lifecycle.LifecycleFsm;

/**
 * REST Lifecycle Manager.
 */

@Path("/policy/pdp")
@Produces({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@Consumes({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@Api
public class RestLifecycleManager {

    @GET
    @Path("engine/lifecycle/fsm/group")
    @ApiOperation(value = "Retrieves the Lifecycle FSM",
        notes = "Lifecycle FSM", response = LifecycleFsm.class)
    public Response group() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getGroup()).build();
    }

    @GET
    @Path("engine/lifecycle/fsm/subgroup")
    @ApiOperation(value = "Retrieves the Lifecycle FSM",
            notes = "Lifecycle FSM", response = LifecycleFsm.class)
    public Response subgroup() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getSubgroup()).build();
    }

    @GET
    @Path("engine/lifecycle/fsm/state")
    @ApiOperation(value = "Retrieves the Lifecycle FSM",
        notes = "Lifecycle FSM", response = LifecycleFsm.class)
    public Response state() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.state()).build();
    }
}
