/*-
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019-2022 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021,2023 Nordix Foundation.
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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
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
public class RestLifecycleManager implements LifecycleApi {

    private static final Logger logger = LoggerFactory.getLogger(RestLifecycleManager.class);

    private static final StandardCoder coder = new StandardCoder();

    /**
     * GET group.
     */

    @Override
    @GET
    @Path("group")
    public Response group() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getGroup()).build();
    }

    /**
     * PUT group.
     */

    @Override
    @PUT
    @Path("group/{group}")
    public Response updateGroup(@PathParam("group") String group) {
        LifecycleFeature.getFsm().setGroup(group);
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getGroup()).build();
    }

    /**
     * GET subgroup.
     */

    @Override
    @GET
    @Path("subgroup")
    public Response subgroup1() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getSubGroup()).build();
    }

    /**
     * PUT subgroup.
     */

    @Override
    @PUT
    @Path("subgroup/{subgroup}")
    public Response subgroup(@PathParam("subgroup") String subgroup) {
        LifecycleFeature.getFsm().setSubGroup(subgroup);
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getSubGroup()).build();
    }

    /**
     * GET properties.
     */

    @Override
    @GET
    @Path("properties")
    public Response propertiesLifecycle() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getProperties()).build();
    }

    /**
     * GET state.
     */

    @Override
    @GET
    @Path("state")
    public Response state() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().state()).build();
    }

    /**
     * PUT state.
     */

    @Override
    @PUT
    @Path("state/{state}")
    public Response updateState(@PathParam("state") String state) {

        var change = new PdpStateChange();
        change.setPdpGroup(LifecycleFeature.getFsm().getGroup());
        change.setPdpSubgroup(LifecycleFeature.getFsm().getSubGroup());
        change.setState(PdpState.valueOf(state));
        change.setName(LifecycleFeature.getFsm().getPdpName());

        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().stateChange(change)).build();
    }

    /**
     * GET topic source.
     */

    @Override
    @GET
    @Path("topic/source")
    public Response sourceLifecycle() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getSource()).build();
    }

    /**
     * GET topic sink.
     */

    @Override
    @GET
    @Path("topic/sink")
    public Response sink() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getClient()).build();
    }

    /**
     * GET status interval.
     */

    @Override
    @GET
    @Path("status/interval")
    public Response updateStatusTimer() {
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getStatusTimerSeconds()).build();
    }

    /**
     * PUT timeout.
     */

    @Override
    @PUT
    @Path("status/interval/{timeout}")
    public Response statusTimer(@PathParam("timeout") Long timeout) {
        LifecycleFeature.getFsm().setStatusTimerSeconds(timeout);
        return Response.status(Response.Status.OK).entity(LifecycleFeature.getFsm().getStatusTimerSeconds()).build();
    }

    /**
     * GET policy types.
     */

    @Override
    @GET
    @Path("policyTypes")
    public Response policyTypes() {
        return Response.status(Response.Status.OK)
            .entity(LifecycleFeature.getFsm().getPolicyTypesMap().keySet())
            .build();
    }

    /**
     * GET controllers.
     */

    @Override
    @GET
    @Path("policyTypes/{policyType}/{policyTypeVersion}")
    public Response policyType(
        @PathParam("policyType") String policyType,
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

    @Override
    @GET
    @Path("policies")
    public Response policies() {
        return Response.status(Response.Status.OK)
            .entity(LifecycleFeature.getFsm().getPoliciesMap().keySet())
            .build();

    }

    /**
     * POST a Policy.
     */

    @Override
    @POST
    @Path("policies")
    public Response deployTrackedPolicy(String policy) {

        var toscaPolicy = getToscaPolicy(policy);
        if (toscaPolicy == null) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
        }

        var typeController = getPolicyTypeController(toscaPolicy);
        if (typeController == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        boolean updateResult = LifecycleFeature.getFsm().update(getDeployPolicyUpdate(List.of(toscaPolicy)));
        return Response.status((updateResult ? Response.Status.OK : Response.Status.NOT_ACCEPTABLE))
            .entity(updateResult)
            .build();
    }

    /**
     * GET a policy.
     */

    @Override
    @GET
    @Path("policies/{policyName}/{policyVersion}")
    public Response policy(
        @PathParam("policyName") String policyName,
        @PathParam("policyVersion") String policyVersion) {

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

    @Override
    @DELETE
    @Path("policies/{policyName}/{policyVersion}")
    public Response undeployPolicy(
        @PathParam("policyName") String policyName,
        @PathParam("policyVersion") String policyVersion) {

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

        return Response.status(Response.Status.OK)
            .entity(LifecycleFeature.getFsm().update(getUndeployPolicyUpdate(List.of(policy))))
            .build();
    }

    /**
     * List of policies individual Operations supported.
     */

    @Override
    @GET
    @Path("policies/operations")
    public Response policiesOperations() {
        return Response.status(Response.Status.OK).entity(List.of("deployment", "undeployment", "validation")).build();
    }

    /**
     * POST a deployment operation on a policy.
     */

    @Override
    @POST
    @Path("policies/operations/deployment")
    public Response deployOperation(String policy) {
        return deployUndeployOperation(policy, true);
    }

    /**
     * POST an undeployment operation on a policy.
     */

    @Override
    @POST
    @Path("policies/operations/undeployment")
    public Response undeployOperation(String policy) {
        return deployUndeployOperation(policy, false);
    }

    /**
     * POST a policy for validation.
     */

    @Override
    @POST
    @Path("policies/operations/validation")
    public Response validateOperation(String policy) {
        var toscaPolicy = getToscaPolicy(policy);
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
        var toscaPolicy = getToscaPolicy(policy);
        if (toscaPolicy == null) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
        }

        var typeController = getPolicyTypeController(toscaPolicy);
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

    private PdpUpdate getPolicyUpdate() {
        var update = new PdpUpdate();
        update.setName(LifecycleFeature.getFsm().getPdpName());
        update.setPdpGroup(LifecycleFeature.getFsm().getGroup());
        update.setPdpSubgroup(LifecycleFeature.getFsm().getSubGroup());
        return update;
    }

    private PdpUpdate getDeployPolicyUpdate(List<ToscaPolicy> policies) {
        PdpUpdate update = getPolicyUpdate();
        update.setPoliciesToBeDeployed(policies);
        return update;
    }

    private PdpUpdate getUndeployPolicyUpdate(List<ToscaPolicy> policies) {
        PdpUpdate update = getPolicyUpdate();
        update.setPoliciesToBeUndeployed(LifecycleFeature.fsm.getPolicyIds(policies));
        return update;
    }

}
