/*-
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021 Nordix Foundation.
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

import com.worldturner.medeia.api.ValidationFailedException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.server.YamlMessageBodyHandler;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.drools.lifecycle.LifecycleFeature;
import org.onap.policy.drools.lifecycle.PolicyTypeController;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST Lifecycle Manager.
 */

@Path("/policy/pdp/engine/lifecycle")
@Produces({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@Consumes({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@Api
public class RestLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(RestLifecycleManager.class);

    private static final StandardCoder coder = new StandardCoder();

    /**
     * GET group.
     */

    @GET
    @Path("group")
    @ApiOperation(value = "Retrieves the Lifecycle group",
        notes = "Lifecycle Group", response = String.class)
    public Response group() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getGroup()).build();
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
        LifecycleFeature.getFsm().setGroup(group);
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getGroup()).build();
    }

    /**
     * GET subgroup.
     */

    @GET
    @Path("subgroup")
    @ApiOperation(value = "Retrieves the Lifecycle subgroup",
            notes = "Lifecycle Subgroup", response = String.class)
    public Response subgroup() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getSubgroup()).build();
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
        LifecycleFeature.getFsm().setSubgroup(subgroup);
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getSubgroup()).build();
    }

    /**
     * GET properties.
     */

    @GET
    @Path("properties")
    @ApiOperation(value = "Retrieves the Lifecycle properties",
            notes = "Lifecycle Properties", response = Properties.class)
    public Response properties() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getProperties()).build();
    }

    /**
     * GET state.
     */

    @GET
    @Path("state")
    @ApiOperation(value = "Retrieves the Lifecycle state", notes = "Lifecycle State", response = PdpState.class)
    public Response state() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().state()).build();
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
        change.setPdpGroup(LifecycleFeature.getFsm().getGroup());
        change.setPdpSubgroup(LifecycleFeature.getFsm().getSubgroup());
        change.setState(PdpState.valueOf(state));
        change.setName(LifecycleFeature.getFsm().getName());

        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().stateChange(change)).build();
    }

    /**
     * GET topic source.
     */

    @GET
    @Path("topic/source")
    @ApiOperation(value = "Retrieves the Lifecycle topic source",
            notes = "Lifecycle Topic Source", response = TopicSource.class)
    public Response source() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getSource()).build();
    }

    /**
     * GET topic sink.
     */

    @GET
    @Path("topic/sink")
    @ApiOperation(value = "Retrieves the Lifecycle topic sink",
            notes = "Lifecycle Topic Sink", response = TopicSink.class)
    public Response sink() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getClient()).build();
    }

    /**
     * GET status interval.
     */

    @GET
    @Path("status/interval")
    @ApiOperation(value = "Retrieves the Lifecycle Status Timer Interval in seconds",
            notes = "Lifecycle Status Timer Interval in seconds", response = Long.class)
    public Response updateStatusTimer() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getStatusTimerSeconds()).build();
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
        LifecycleFeature.getFsm().setStatusTimerSeconds(timeout);
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getStatusTimerSeconds()).build();
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
                       .entity(LifecycleFeature.getFsm().getPolicyTypesMap().keySet())
                       .build();
    }

    /**
     * GET controllers.
     */

    @GET
    @Path("policyTypes/{policyType}/{policyTypeVersion}")
    @ApiOperation(value = "Entities associated with a policy type",
            notes = "Lifecycle policy Types", response = PolicyTypeController.class)
    public Response policyType(
        @ApiParam(value = "Policy Type", required = true)
            @PathParam("policyType") String policyType,
        @ApiParam(value = "Policy Type Version", required = true)
            @PathParam("policyTypeVersion") String policyTypeVersion) {
        PolicyTypeController typeController =
            LifecycleFeature.getFsm().getPolicyTypesMap()
                    .get(new ToscaConceptIdentifier(policyType, policyTypeVersion));
        if (typeController == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.status(Response.Status.OK)
                       .entity(typeController)
                       .build();
    }

    /**
     * GET policies.
     */

    @GET
    @Path("policies")
    @ApiOperation(value = "List of policies", responseContainer = "List")
    public Response policies() {
        return Response.status(Response.Status.OK)
                       .entity(LifecycleFeature.getFsm().getPoliciesMap().keySet())
                       .build();

    }

    /**
     * POST a Policy.
     */

    @POST
    @Path("policies")
    @ApiOperation(value = "Deploy a policy", response = Boolean.class)
    public Response deployTrackedPolicy(
            @ApiParam(value = "Tosca Policy", required = true) String policy) {

        ToscaPolicy toscaPolicy = getToscaPolicy(policy);
        if (toscaPolicy == null) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
        }

        PolicyTypeController typeController = getPolicyTypeController(toscaPolicy);
        if (typeController == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ToscaPolicy> policies =
                LifecycleFeature.getFsm().getPoliciesMap().values().stream().collect(Collectors.toList());
        policies.add(toscaPolicy);

        boolean updateResult = LifecycleFeature.getFsm().update(getPolicyUpdate(policies));
        return Response.status((updateResult ? Response.Status.OK : Response.Status.NOT_ACCEPTABLE))
                       .entity(updateResult)
                       .build();
    }

    /**
     * GET a policy.
     */

    @GET
    @Path("policies/{policyName}/{policyVersion}")
    @ApiOperation(value = "Retrieves a policy", response = ToscaPolicy.class)
    public Response policy(
            @ApiParam(value = "Policy Name", required = true) @PathParam("policyName") String policyName,
            @ApiParam(value = "Policy Version", required = true) @PathParam("policyVersion") String policyVersion) {

        ToscaPolicy policy;
        try {
            policy =
                LifecycleFeature.getFsm().getPoliciesMap().get(new ToscaConceptIdentifier(policyName, policyVersion));
        } catch (RuntimeException r) {
            logger.debug("policy {}:{} has not been found", policyName, policyVersion, r);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (policy == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.status(Response.Status.OK).entity(policy).build();
    }

    /**
     * DELETE a policy.
     */

    @DELETE
    @Path("policies/{policyName}/{policyVersion}")
    @ApiOperation(value = "Deletes a Lifecycle tracked policy", response = Boolean.class)
    public Response undeployPolicy(
            @ApiParam(value = "Policy", required = true) @PathParam("policyName") String policyName,
            @ApiParam(value = "Policy Version", required = true) @PathParam("policyVersion") String policyVersion) {

        ToscaPolicy policy;
        try {
            policy =
                LifecycleFeature.getFsm().getPoliciesMap().get(new ToscaConceptIdentifier(policyName, policyVersion));
        } catch (RuntimeException r) {
            logger.debug("policy {}:{} has not been found", policyName, policyVersion, r);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (policy == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ToscaPolicy> policies =
                LifecycleFeature.getFsm().getPoliciesMap().values().stream().collect(Collectors.toList());
        policies.removeIf(otherPolicy -> policy.getIdentifier().equals(otherPolicy.getIdentifier()));
        return Response.status(Response.Status.OK)
                       .entity(LifecycleFeature.getFsm().update(getPolicyUpdate(policies)))
                       .build();
    }

    /**
     * List of policies individual Operations supported.
     */

    @GET
    @Path("policies/operations")
    @ApiOperation(value = "Gets Policy Operations", responseContainer = "List")
    public Response policiesOperations() {
        return Response.status(Response.Status.OK).entity(List.of("deployment", "undeployment", "validation")).build();
    }

    /**
     * POST a deployment operation on a policy.
     */

    @POST
    @Path("policies/operations/deployment")
    @ApiOperation(value = "Deploys a policy", notes = "Deploys a policy", response = Boolean.class)
    public Response deployOperation(@ApiParam(value = "Tosca Policy", required = true) String policy) {
        return deployUndeployOperation(policy, true);
    }

    /**
     * POST an undeployment operation on a policy.
     */

    @POST
    @Path("policies/operations/undeployment")
    @ApiOperation(value = "Undeploys a policy", response = Boolean.class)
    public Response undeployOperation(@ApiParam(value = "Tosca Policy", required = true) String policy) {
        return deployUndeployOperation(policy, false);
    }

    /**
     * POST a policy for validation.
     */

    @POST
    @Path("policies/operations/validation")
    @ApiOperation(value = "Validates a policy", responseContainer = "List")
    public Response validateOperation(@ApiParam(value = "Tosca Policy", required = true) String policy) {
        ToscaPolicy toscaPolicy = getToscaPolicy(policy);
        if (toscaPolicy == null) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
        }

        try {
            LifecycleFeature.getFsm().getDomainMaker().conformance(toscaPolicy);
        } catch (ValidationFailedException v) {
            logger.trace("policy {} validation errors: {}", toscaPolicy, v.getMessage(), v);
            return Response.status(Response.Status.NOT_ACCEPTABLE).entity(v.getFailures()).build();
        }

        return Response.status(Response.Status.OK).entity(Collections.emptyList()).build();
    }

    private Response deployUndeployOperation(String policy, boolean deploy) {
        ToscaPolicy toscaPolicy = getToscaPolicy(policy);
        if (toscaPolicy == null) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
        }

        PolicyTypeController typeController = getPolicyTypeController(toscaPolicy);
        if (typeController == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.status(Response.Status.OK)
                       .entity((deploy) ? typeController.deploy(toscaPolicy) : typeController.undeploy(toscaPolicy))
                       .build();
    }

    private ToscaPolicy getToscaPolicy(String policy) {
        try {
            return coder.decode(policy, ToscaPolicy.class);
        } catch (CoderException | RuntimeException e) {
            return null;
        }
    }

    private PolicyTypeController getPolicyTypeController(ToscaPolicy policy) {
        return LifecycleFeature.getFsm().getPolicyTypesMap().get(policy.getTypeIdentifier());
    }

    private PdpUpdate getPolicyUpdate(List<ToscaPolicy> policies) {
        PdpUpdate update = new PdpUpdate();
        update.setName(LifecycleFeature.getFsm().getName());
        update.setPdpGroup(LifecycleFeature.getFsm().getGroup());
        update.setPdpSubgroup(LifecycleFeature.getFsm().getSubgroup());
        update.setPolicies(policies);
        return update;
    }

}
