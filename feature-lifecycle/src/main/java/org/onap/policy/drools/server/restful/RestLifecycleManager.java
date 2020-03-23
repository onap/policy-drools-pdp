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
import io.swagger.annotations.ApiParam;
import java.util.Properties;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.server.YamlMessageBodyHandler;
import org.onap.policy.drools.lifecycle.LifecycleFeature;
import org.onap.policy.drools.lifecycle.PolicyTypeController;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;

/**
 * REST Lifecycle Manager.
 */

@Path("/policy/pdp/engine/lifecycle")
@Produces({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@Consumes({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@Api
public class RestLifecycleManager {

    /**
     * GET group.
     */

    @GET
    @Path("group")
    @ApiOperation(value = "Retrieves the Lifecycle group",
        notes = "Lifecycle Group", response = String.class)
    public Response group() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getGroup()).build();
    }

    /**
     * PUT group.
     */

    @PUT
    @Path("group/{group}")
    @ApiOperation(value = "Updates the Lifecycle group",
            notes = "Lifecycle Group", response = String.class)
    public Response updateGroup(
        @ApiParam(value = "Group", required = true) @PathParam("group") String group) {
        LifecycleFeature.fsm.setGroup(group);
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getGroup()).build();
    }

    /**
     * GET subgroup.
     */

    @GET
    @Path("subgroup")
    @ApiOperation(value = "Retrieves the Lifecycle subgroup",
            notes = "Lifecycle Subgroup", response = String.class)
    public Response subgroup() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getSubgroup()).build();
    }

    /**
     * PUT subgroup.
     */

    @PUT
    @Path("subgroup/{subgroup}")
    @ApiOperation(value = "Retrieves the Lifecycle subgroup",
            notes = "Lifecycle Subgroup", response = String.class)
    public Response subgroup(
        @ApiParam(value = "Subgroup", required = true) @PathParam("subgroup") String subgroup) {
        LifecycleFeature.fsm.setSubgroup(subgroup);
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getSubgroup()).build();
    }

    /**
     * GET properties.
     */

    @GET
    @Path("properties")
    @ApiOperation(value = "Retrieves the Lifecycle properties",
            notes = "Lifecycle Properties", response = Properties.class)
    public Response properties() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getProperties()).build();
    }

    /**
     * GET state.
     */

    @GET
    @Path("state")
    @ApiOperation(value = "Retrieves the Lifecycle state", notes = "Lifecycle State", response = PdpState.class)
    public Response state() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.state()).build();
    }

    /**
     * PUT state.
     */

    @PUT
    @Path("state/{state}")
    @ApiOperation(value = "updates the Lifecycle state", notes = "Lifecycle State", response = Boolean.class)
    public Response updateState(
        @ApiParam(value = "state", required = true) @PathParam("state") String state) {

        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup(LifecycleFeature.fsm.getGroup());
        change.setPdpSubgroup(LifecycleFeature.fsm.getSubgroup());
        change.setState(PdpState.valueOf(state));
        change.setName(LifecycleFeature.fsm.getName());

        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.stateChange(change)).build();
    }

    /**
     * GET topic source.
     */

    @GET
    @Path("topic/source")
    @ApiOperation(value = "Retrieves the Lifecycle topic source",
            notes = "Lifecycle Topic Source", response = TopicSource.class)
    public Response source() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getSource()).build();
    }

    /**
     * GET topic sink.
     */

    @GET
    @Path("topic/sink")
    @ApiOperation(value = "Retrieves the Lifecycle topic sink",
            notes = "Lifecycle Topic Sink", response = TopicSink.class)
    public Response sink() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getClient()).build();
    }

    /**
     * GET status interval.
     */

    @GET
    @Path("status/interval")
    @ApiOperation(value = "Retrieves the Lifecycle Status Timer Interval in seconds",
            notes = "Lifecycle Status Timer Interval in seconds", response = Long.class)
    public Response updateStatusTimer() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getStatusTimerSeconds()).build();
    }

    /**
     * PUT timeout.
     */

    @PUT
    @Path("status/interval/{timeout}")
    @ApiOperation(value = "Updates the Lifecycle Status Timer Interval in seconds",
            notes = "Lifecycle Status Timer Interval in seconds", response = Long.class)
    public Response statusTimer(
            @ApiParam(value = "timeout", required = true) @PathParam("timeout") Long timeout) {
        LifecycleFeature.fsm.setStatusTimerSeconds(timeout);
        return Response.status(Response.Status.OK).entity(LifecycleFeature.fsm.getStatusTimerSeconds()).build();
    }

    /**
     * GET policy types.
     */

    @GET
    @Path("policyTypes")
    @ApiOperation(value = "List of supported policy types",
            notes = "Lifecycle Policy Types", responseContainer = "List")
    public Response policyTypes() {
        return Response.status(Response.Status.OK)
                       .entity(LifecycleFeature.fsm.getPolicyTypesMap().keySet())
                       .build();
    }

    /**
     * GET controllers.
     */

    @GET
    @Path("policyTypes/{policyType}/{policyVersion}")
    @ApiOperation(value = "Entities associated with a policy type",
            notes = "Lifecycle policy Types", response = PolicyTypeController.class)
    public Response policyType(
        @ApiParam(value = "Policy Type", required = true) @PathParam("policyType") String policyType,
        @ApiParam(value = "Policy Type Version", required = true) @PathParam("policyVersion") String policyVersion) {
        return Response.status(Response.Status.OK)
                       .entity(LifecycleFeature.fsm
                                .getPolicyTypesMap()
                                .get(new ToscaPolicyTypeIdentifier(policyType, policyVersion)))
                       .build();
    }

    /**
     * GET policies.
     */

    @GET
    @Path("policies")
    @ApiOperation(value = "List of tracked policies",
            notes = "Lifecycle Policies", responseContainer = "List")
    public Response policies() {
        return Response.status(Response.Status.OK)
                       .entity(LifecycleFeature.fsm.getPoliciesMap().keySet())
                       .build();

    }

    /**
     * GET a policy.
     */

    @GET
    @Path("policies/{policy}/{policyVersion}")
    @ApiOperation(value = "Lifecycle tracked policy",
            notes = "Lifecycle Tracked Policy", response = ToscaPolicy.class)
    public Response policy(
            @ApiParam(value = "Policy", required = true) @PathParam("policyName") String policyName,
            @ApiParam(value = "Policy Version", required = true) @PathParam("policyVersion") String policyVersion) {

        ToscaPolicy policy = LifecycleFeature.fsm
                                     .getPoliciesMap()
                                     .get(new ToscaPolicyTypeIdentifier(policyName, policyVersion));
        if (policy != null) {
            return
                Response.status(Response.Status.OK)
                        .entity(LifecycleFeature.fsm.getPolicyTypesMap()
                                        .get(new ToscaPolicyTypeIdentifier(policyName, policyVersion)))
                        .build();
        }

        return Response.status(Response.Status.NOT_FOUND).build();
    }
}
