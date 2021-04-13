/*
 * ============LICENSE_START=======================================================
 * ONAP
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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
import java.util.Properties;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.server.YamlMessageBodyHandler;
import org.onap.policy.drools.legacy.config.LegacyConfigFeature;

/**
 * REST Legacy Configuration Manager.
 */

@Path("/policy/pdp/engine/legacy/config")
@Produces({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@Consumes({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@Api
public class RestLegacyConfigManager {

    /**
     * GET properties.
     */
    @GET
    @Path("properties")
    @ApiOperation(value = "Retrieves the legacy configuration properties",
            notes = "Legacy Configuration Properties", response = Properties.class)
    public Response properties() {
        return Response.status(Response.Status.OK)
                       .entity(LegacyConfigFeature.getLegacyConfig().getProperties()).build();
    }

    /**
     * GET the topic source.
     */
    @GET
    @Path("topic/source")
    @ApiOperation(value = "Retrieves the legacy configuration topic source",
            notes = "Legacy Configuration Source", response = TopicSource.class)
    public Response source() {
        return Response.status(Response.Status.OK)
                       .entity(LegacyConfigFeature.getLegacyConfig().getSource()).build();
    }
}
