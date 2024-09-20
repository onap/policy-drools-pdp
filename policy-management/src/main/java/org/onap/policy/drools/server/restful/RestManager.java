/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021, 2023-2024 Nordix Foundation.
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

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NOT_MODIFIED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.PARTIAL_CONTENT;
import static org.onap.policy.drools.properties.DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME;

import ch.qos.logback.classic.LoggerContext;
import com.google.re2j.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.server.YamlMessageBodyHandler;
import org.onap.policy.common.utils.logging.LoggerUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.protocol.coders.EventProtocolCoderConstants;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyDroolsPdpRuntimeException;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Telemetry JAX-RS Interface to the PDP-D.
 */

@Path("/policy/pdp")
@Produces({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@Consumes({MediaType.APPLICATION_JSON, YamlMessageBodyHandler.APPLICATION_YAML})
@ToString
public class RestManager implements SwaggerApi, DefaultApi, FeaturesApi, InputsApi,
    PropertiesApi, EnvironmentApi, SwitchesApi, ControllersApi,
    TopicsApi, ToolsApi {

    private static final String OFFER_FAILED = "{}: cannot offer to topic {} because of {}";
    private static final String CANNOT_PERFORM_OPERATION = "cannot perform operation";
    private static final String NO_FILTERS = " has no filters";
    private static final String NOT_FOUND_MSG = " not found";
    private static final String DOES_NOT_EXIST_MSG = " does not exist";
    private static final String NOT_ACCEPTABLE_MSG = " not acceptable";
    private static final String FETCH_POLICY_FAILED = "{}: cannot get policy-controller because of {}";
    private static final String FETCH_POLICY_BY_NAME_FAILED = "{}: cannot get policy-controller {} because of {}";
    private static final String FETCH_POLICY_BY_TOPIC_FAILED =
        "{}: cannot get policy-controller {} topic {} because of {}";
    private static final String FETCH_DROOLS_FAILED = "{}: cannot get drools-controller {} because of {}";
    private static final String FETCH_DROOLS_BY_ENTITY_FAILED =
        "{}: cannot get: drools-controller {}, session {}, query {}, entity {} because of {}";
    private static final String FETCH_DROOLS_BY_PARAMS_FAILED =
        "{}: cannot get: drools-controller {}, session {}, query {}, entity {}, params {} because of {}";
    private static final String FETCH_DROOLS_BY_FACTTYPE_FAILED =
        "{}: cannot get: drools-controller {}, session {}, factType {}, because of {}";
    private static final String FETCH_DECODERS_BY_POLICY_FAILED =
        "{}: cannot get decoders for policy-controller {} because of {}";
    private static final String FETCH_DECODERS_BY_TOPIC_FAILED =
        "{}: cannot get decoders for policy-controller {} topic {} because of {}";
    private static final String FETCH_DECODER_BY_TYPE_FAILED =
        "{}: cannot get decoder filters for policy-controller {} topic {} type {} because of {}";
    private static final String FETCH_DECODER_BY_FILTER_FAILED =
        "{}: cannot get decoder filters for policy-controller {} topic {} type {} filters {} because of {}";
    private static final String FETCH_ENCODER_BY_FILTER_FAILED =
        "{}: cannot get encoder filters for policy-controller {} because of {}";

    private static final String SWAGGER = "/swagger/swagger.json";

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(RestManager.class);

    /**
     * Feed Ports into Resources.
     */
    private static final List<String> INPUTS = Collections.singletonList("configuration");

    /**
     * Resource Toggles.
     */
    private static final List<String> SWITCHES = Arrays.asList("activation", "lock");

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/swagger")
    public Response swagger() {
        try {
            String contents = getSwaggerContents();
            return Response.status(OK).entity(contents).build();
        } catch (Exception e) {
            logger.error("Cannot read swagger.json {} because of {}", e.getMessage(), e.toString());
            return Response.status(INTERNAL_SERVER_ERROR).build();
        }

    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine")
    public Response engine() {
        return Response.status(OK).entity(PolicyEngineConstants.getManager()).build();
    }

    /**
     * DELETE.
     *
     * @return response object
     */
    @Override
    @DELETE
    @Path("engine")
    public Response engineShutdown() {
        try {
            PolicyEngineConstants.getManager().shutdown();
        } catch (final IllegalStateException e) {
            logger.error("{}: cannot shutdown {} because of {}", this, PolicyEngineConstants.getManager(),
                e.getMessage(), e);
            return Response.status(BAD_REQUEST).entity(PolicyEngineConstants.getManager()).build();
        }

        return Response.status(OK).entity(PolicyEngineConstants.getManager()).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/features")
    public Response engineFeatures() {
        return Response.status(OK).entity(PolicyEngineConstants.getManager().getFeatures()).build();
    }

    @Override
    @GET
    @Path("engine/features/inventory")
    public Response engineFeaturesInventory() {
        return Response.status(OK).entity(PolicyEngineConstants.getManager().getFeatureProviders()).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/features/{featureName}")
    public Response engineFeature(@PathParam("featureName") String featureName) {
        try {
            return Response.status(OK).entity(PolicyEngineConstants.getManager().getFeatureProvider(featureName))
                .build();
        } catch (final IllegalArgumentException iae) {
            logger.debug("feature unavailable: {}", featureName, iae);
            return errorResponse(NOT_FOUND, iae.getMessage());
        }
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/inputs")
    public Response engineInputs() {
        return Response.status(OK).entity(INPUTS).build();
    }

    /**
     * POST.
     *
     * @return response object
     */
    @Override
    @POST
    @Path("engine/inputs/configuration")
    public Response engineUpdate(PdpdConfiguration configuration) {
        try {
            if (PolicyEngineConstants.getManager().configure(configuration)) {
                return Response.status(OK).entity(true).build();
            }
        } catch (final Exception e) {
            logger.info("{}: cannot configure {} because of {}", this, PolicyEngineConstants.getManager(),
                e.getMessage(), e);
        }

        return errorResponse(NOT_ACCEPTABLE, CANNOT_PERFORM_OPERATION);
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/properties")
    public Response engineProperties() {
        return Response.status(OK).entity(PolicyEngineConstants.getManager().getProperties()).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/environment")
    public Response engineEnvironment() {
        return Response.status(OK).entity(PolicyEngineConstants.getManager().getEnvironment()).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/environment/{envProperty}")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response engineEnvironmentProperty(@PathParam("envProperty") String envProperty) {
        return Response.status(OK).entity(PolicyEngineConstants.getManager().getEnvironmentProperty(envProperty))
            .build();
    }

    /**
     * PUT.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/environment/{envProperty}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response engineEnvironmentAdd(@PathParam("envProperty") String envProperty, String envValue) {
        final String previousValue = PolicyEngineConstants.getManager().setEnvironmentProperty(envProperty, envValue);
        return Response.status(OK).entity(previousValue).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/switches")
    public Response engineSwitches() {
        return Response.status(OK).entity(SWITCHES).build();
    }

    /**
     * PUT.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/switches/activation")
    public Response engineActivation() {
        try {
            PolicyEngineConstants.getManager().activate();
            return Response.status(OK).entity(PolicyEngineConstants.getManager()).build();
        } catch (final Exception e) {
            logger.info("{}: cannot activate {} because of {}", this, PolicyEngineConstants.getManager(),
                e.getMessage(), e);
        }

        return errorResponse(NOT_ACCEPTABLE, CANNOT_PERFORM_OPERATION);
    }

    /**
     * DELETE.
     *
     * @return response object
     */
    @Override
    @DELETE
    @Path("engine/switches/activation")
    public Response engineDeactivation() {
        try {
            PolicyEngineConstants.getManager().deactivate();
            return Response.status(OK).entity(PolicyEngineConstants.getManager()).build();
        } catch (final Exception e) {
            logger.info("{}: cannot deactivate {} because of {}", this, PolicyEngineConstants.getManager(),
                e.getMessage(), e);
        }

        return errorResponse(NOT_ACCEPTABLE, CANNOT_PERFORM_OPERATION);
    }

    /**
     * PUT.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/switches/lock")
    public Response engineLock() {
        if (PolicyEngineConstants.getManager().lock()) {
            return Response.status(OK).entity(PolicyEngineConstants.getManager()).build();
        } else {
            return errorResponse(NOT_ACCEPTABLE, CANNOT_PERFORM_OPERATION);
        }
    }

    /**
     * DELETE.
     *
     * @return response object
     */
    @Override
    @DELETE
    @Path("engine/switches/lock")
    public Response engineUnlock() {
        if (PolicyEngineConstants.getManager().unlock()) {
            return Response.status(OK).entity(PolicyEngineConstants.getManager()).build();
        } else {
            return errorResponse(NOT_ACCEPTABLE, CANNOT_PERFORM_OPERATION);
        }
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers")
    public Response controllers() {
        return Response.status(OK).entity(PolicyEngineConstants.getManager().getPolicyControllerIds()).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/inventory")
    public Response controllerInventory() {
        return Response.status(OK).entity(PolicyEngineConstants.getManager().getPolicyControllers()).build();
    }

    /**
     * POST.
     *
     * @return response object
     */
    @Override
    @POST
    @Path("engine/controllers")
    public Response controllerAdd(Properties config) {
        if (config == null) {
            return errorResponse(BAD_REQUEST, "A configuration must be provided");
        }

        var controllerName = config.getProperty(PROPERTY_CONTROLLER_NAME);
        if (controllerName == null || controllerName.isEmpty()) {
            return errorResponse(BAD_REQUEST, "Configuration must have an entry for "
                + PROPERTY_CONTROLLER_NAME);
        }

        PolicyController controller;
        try {
            controller = PolicyControllerConstants.getFactory().get(controllerName);
            if (controller != null) {
                return Response.status(NOT_MODIFIED).entity(controller).build();
            }
        } catch (final IllegalArgumentException e) {
            logger.trace("OK ", e);
            // This is OK
        } catch (final IllegalStateException e) {
            logger.info(FETCH_POLICY_FAILED, this, e.getMessage(), e);
            return errorResponse(NOT_ACCEPTABLE, controllerName + NOT_FOUND_MSG);
        }

        try {
            controller = PolicyEngineConstants.getManager().createPolicyController(
                config.getProperty(PROPERTY_CONTROLLER_NAME), config);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("{}: cannot create policy-controller because of {}", this, e.getMessage(), e);
            return errorResponse(BAD_REQUEST, e.getMessage());
        }

        try {
            if (!controller.start()) {
                logger.info("{}: cannot start {}", this, controller);
                return errorResponse(PARTIAL_CONTENT, controllerName + " can't be started");
            }
        } catch (final IllegalStateException e) {
            logger.info("{}: cannot start {} because of {}", this, controller, e.getMessage(), e);
            return Response.status(PARTIAL_CONTENT).entity(controller).build();
        }

        return Response.status(CREATED).entity(controller).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/features")
    public Response controllerFeatures() {
        return Response.status(OK).entity(PolicyEngineConstants.getManager().getFeatures()).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/features/inventory")
    public Response controllerFeaturesInventory() {
        return Response.status(OK).entity(PolicyControllerConstants.getFactory().getFeatureProviders()).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/features/{featureName}")
    public Response controllerFeature(@PathParam("featureName") String featureName) {
        try {
            return Response.status(OK).entity(PolicyControllerConstants.getFactory().getFeatureProvider(featureName))
                .build();
        } catch (final IllegalArgumentException iae) {
            logger.debug("{}: cannot feature {} because of {}", this, featureName, iae.getMessage(), iae);
            return errorResponse(NOT_FOUND, iae.getMessage());
        }
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}")
    public Response controller(@PathParam("controller") String controllerName) {

        return catchArgStateGenericEx(
            () -> Response.status(OK).entity(PolicyControllerConstants.getFactory().get(controllerName)).build(),
            e -> {
                logger.debug(FETCH_POLICY_BY_NAME_FAILED, this, controllerName, e.getMessage(), e);
                return (controllerName);
            });
    }

    /**
     * DELETE.
     *
     * @return response object
     */
    @Override
    @DELETE
    @Path("engine/controllers/{controller}")
    public Response controllerDelete(@PathParam("controller") String controllerName) {

        PolicyController controller;
        try {
            controller = PolicyControllerConstants.getFactory().get(controllerName);
            if (controller == null) {
                return errorResponse(BAD_REQUEST, controllerName + DOES_NOT_EXIST_MSG);
            }
        } catch (final IllegalArgumentException e) {
            logger.debug(FETCH_POLICY_BY_NAME_FAILED, this, controllerName, e.getMessage(), e);
            return errorResponse(BAD_REQUEST, controllerName + NOT_FOUND_MSG + ": " + e.getMessage());
        } catch (final IllegalStateException e) {
            logger.debug(FETCH_POLICY_BY_NAME_FAILED, this, controllerName, e.getMessage(), e);
            return errorResponse(NOT_ACCEPTABLE, controllerName + NOT_ACCEPTABLE_MSG);
        }

        try {
            PolicyEngineConstants.getManager().removePolicyController(controllerName);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.debug("{}: cannot remove policy-controller {} because of {}",
                this, controllerName, e.getMessage(), e);
            return errorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }

        return Response.status(OK).entity(controller).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/properties")

    public Response controllerProperties(@PathParam("controller") String controllerName) {

        return catchArgStateGenericEx(() -> {
            var controller = PolicyControllerConstants.getFactory().get(controllerName);
            return Response.status(OK).entity(controller.getProperties()).build();

        }, e -> {
            logger.debug(FETCH_POLICY_BY_NAME_FAILED, this, controllerName, e.getMessage(), e);
            return (controllerName);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/inputs")
    public Response controllerInputs(@PathParam("controller") String controllerName) {
        return Response.status(OK).entity(INPUTS).build();
    }

    /**
     * POST.
     *
     * @return response object
     */
    @Override
    @POST
    @Path("engine/controllers/{controller}/inputs/configuration")
    public Response controllerUpdate(ControllerConfiguration controllerConfiguration,
                                     @PathParam("controller") String controllerName) {

        if (StringUtils.isBlank(controllerName) || controllerConfiguration == null
            || !controllerName.equals(controllerConfiguration.getName())) {
            return Response.status(BAD_REQUEST)
                .entity("A valid or matching controller names must be provided").build();
        }

        return catchArgStateGenericEx(() -> {
            var controller = PolicyEngineConstants.getManager().updatePolicyController(controllerConfiguration);
            if (controller == null) {
                return errorResponse(BAD_REQUEST, controllerName + DOES_NOT_EXIST_MSG);
            }

            return Response.status(OK).entity(controller).build();

        }, e -> {
            logger.info("{}: cannot update policy-controller {} because of {}",
                this, controllerName, e.getMessage(), e);
            return (controllerName);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/switches")
    public Response controllerSwitches(@PathParam("controller") String controllerName) {
        return Response.status(OK).entity(SWITCHES).build();
    }

    /**
     * PUT.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/controllers/{controller}/switches/lock")
    public Response controllerLock(@PathParam("controller") String controllerName) {
        var policyController = PolicyControllerConstants.getFactory().get(controllerName);
        if (policyController.lock()) {
            return Response.status(OK).entity(policyController).build();
        } else {
            return errorResponse(NOT_ACCEPTABLE, "Controller " + controllerName + " cannot be locked");
        }
    }

    /**
     * DELETE.
     *
     * @return response object
     */
    @Override
    @DELETE
    @Path("engine/controllers/{controller}/switches/lock")
    public Response controllerUnlock(@PathParam("controller") String controllerName) {
        var policyController = PolicyControllerConstants.getFactory().get(controllerName);
        final boolean success = policyController.unlock();
        if (success) {
            return Response.status(OK).entity(policyController).build();
        } else {
            return errorResponse(NOT_ACCEPTABLE, "Controller " + controllerName + " cannot be unlocked");
        }
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/drools")
    public Response drools(@PathParam("controller") String controllerName) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            return Response.status(OK).entity(drools).build();

        }, e -> {
            logger.debug(FETCH_DROOLS_FAILED, this, controllerName, e.getMessage(), e);
            return (controllerName);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/drools/facts")
    public Response droolsFacts2(@PathParam("controller") String controllerName) {

        return catchArgStateGenericEx(() -> {
            final Map<String, Long> sessionCounts = new HashMap<>();
            var drools = this.getDroolsController(controllerName);
            for (final String session : drools.getSessionNames()) {
                sessionCounts.put(session, drools.factCount(session));
            }
            return sessionCounts;

        }, e -> {
            logger.debug(FETCH_POLICY_BY_NAME_FAILED, this, controllerName, e.getMessage(), e);
            return controllerName;
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/drools/facts/{session}")
    public Response droolsFacts1(@PathParam("controller") String controllerName,
                                 @PathParam("session") String sessionName) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            return drools.factClassNames(sessionName);

        }, e -> {
            logger.debug(FETCH_DROOLS_FAILED, this, controllerName, e.getMessage(), e);
            return (controllerName + ":" + sessionName);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/drools/facts/{session}/{factType}")
    public Response droolsFacts(
        @PathParam("controller") String controllerName,
        @PathParam("session") String sessionName,
        @PathParam("factType") String factType,
        @DefaultValue("false") @QueryParam("count") boolean count) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            final List<Object> facts = drools.facts(sessionName, factType, false);
            return (count ? facts.size() : facts);

        }, e -> {
            logger.debug(FETCH_POLICY_BY_NAME_FAILED, this, controllerName, e.getMessage(), e);
            return (controllerName + ":" + sessionName + ":" + factType);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/drools/facts/{session}/{query}/{queriedEntity}")
    public Response droolsFacts3(
        @PathParam("controller") String controllerName,
        @PathParam("session") String sessionName,
        @PathParam("query") String queryName,
        @PathParam("queriedEntity") String queriedEntity,
        @DefaultValue("false") @QueryParam("count") boolean count) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            final List<Object> facts = drools.factQuery(sessionName, queryName, queriedEntity, false);
            return (count ? facts.size() : facts);

        }, e -> {
            logger.debug(FETCH_DROOLS_BY_ENTITY_FAILED, this,
                controllerName, sessionName, queryName, queriedEntity, e.getMessage(), e);
            return (controllerName + ":" + sessionName + ":" + queryName + queriedEntity);
        });
    }

    /**
     * POST.
     *
     * @return response object
     */
    @Override
    @POST
    @Path("engine/controllers/{controller}/drools/facts/{session}/{query}/{queriedEntity}")
    public Response droolsFacts4(
        @PathParam("controller") String controllerName,
        @PathParam("session") String sessionName,
        @PathParam("query") String queryName,
        @PathParam("queriedEntity") String queriedEntity,
        List<Object> queryParameters) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            if (queryParameters == null || queryParameters.isEmpty()) {
                return drools.factQuery(sessionName, queryName, queriedEntity, false);
            } else {
                return drools.factQuery(sessionName, queryName, queriedEntity, false, queryParameters.toArray());
            }

        }, e -> {
            logger.debug(FETCH_DROOLS_BY_PARAMS_FAILED,
                this, controllerName, sessionName, queryName, queriedEntity, queryParameters, e.getMessage(), e);
            return (controllerName + ":" + sessionName + ":" + queryName + queriedEntity);
        });
    }

    /**
     * DELETE.
     *
     * @return response object
     */
    @Override
    @DELETE
    @Path("engine/controllers/{controller}/drools/facts/{session}/{factType}")
    public Response droolsFactsDelete1(
        @PathParam("controller") String controllerName,
        @PathParam("session") String sessionName,
        @PathParam("factType") String factType) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            return drools.facts(sessionName, factType, true);

        }, e -> {
            logger.debug(FETCH_DROOLS_BY_FACTTYPE_FAILED, this,
                controllerName, sessionName, factType, e.getMessage(), e);
            return (controllerName + ":" + sessionName + ":" + factType);
        });
    }

    /**
     * DELETE.
     *
     * @return response object
     */
    @Override
    @DELETE
    @Path("engine/controllers/{controller}/drools/facts/{session}/{query}/{queriedEntity}")
    public Response droolsFactsDelete(
        @PathParam("controller") String controllerName,
        @PathParam("session") String sessionName,
        @PathParam("query") String queryName,
        @PathParam("queriedEntity") String queriedEntity) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            return drools.factQuery(sessionName, queryName, queriedEntity, true);


        }, e -> {
            logger.debug(FETCH_DROOLS_BY_PARAMS_FAILED,
                this, controllerName, sessionName, queryName, queriedEntity, e.getMessage(), e.toString());
            return (controllerName + ":" + sessionName + ":" + queryName + ":" + queriedEntity);
        });
    }

    /**
     * POST.
     *
     * @return response object
     */
    @Override
    @POST
    @Path("engine/controllers/tools/coders/decoders/filters/rule")
    public Response rules(String expression) {
        return Response.status(OK).entity(new JsonProtocolFilter(expression)).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/decoders")
    public Response decoders(@PathParam("controller") String controllerName) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            return EventProtocolCoderConstants.getManager().getDecoders(drools.getGroupId(), drools.getArtifactId());

        }, e -> {
            logger.debug(FETCH_DECODERS_BY_POLICY_FAILED, this, controllerName, e.getMessage(), e);
            return (controllerName);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/decoders/filters")
    public Response decoderFilters(@PathParam("controller") String controllerName) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            return EventProtocolCoderConstants.getManager()
                .getDecoderFilters(drools.getGroupId(), drools.getArtifactId());

        }, e -> {
            logger.debug(FETCH_DECODERS_BY_POLICY_FAILED, this, controllerName, e.getMessage(), e);
            return (controllerName);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/decoders/{topic}")
    public Response decoder(
        @PathParam("controller") String controllerName,
        @PathParam("topic") String topic) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            return EventProtocolCoderConstants.getManager()
                .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);

        }, e -> {
            logger.debug(FETCH_DECODERS_BY_TOPIC_FAILED, this, controllerName, topic, e.getMessage(), e);
            return (controllerName + ":" + topic);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/decoders/{topic}/filters")
    public Response decoderFilter2(
        @PathParam("controller") String controllerName,
        @PathParam("topic") String topic) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            var decoder = EventProtocolCoderConstants.getManager()
                .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
            if (decoder == null) {
                return errorResponse(BAD_REQUEST, topic + DOES_NOT_EXIST_MSG);
            } else {
                return decoder.getCoders();
            }

        }, e -> {
            logger.debug(FETCH_DECODERS_BY_TOPIC_FAILED, this, controllerName, topic, e.getMessage(), e);
            return (controllerName + ":" + topic);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}")
    public Response decoderFilter1(
        @PathParam("controller") String controllerName,
        @PathParam("topic") String topic,
        @PathParam("factType") String factClass) {

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            var decoder = EventProtocolCoderConstants.getManager()
                .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
            var filters = decoder.getCoder(factClass);
            if (filters == null) {
                return errorResponse(BAD_REQUEST, topic + ":" + factClass + DOES_NOT_EXIST_MSG);
            } else {
                return filters;
            }

        }, e -> {
            logger.debug(FETCH_DECODER_BY_TYPE_FAILED, this, controllerName, topic, factClass, e.getMessage(), e);
            return (controllerName + ":" + topic + ":" + factClass);
        });
    }

    /**
     * PUT.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}")
    public Response decoderFilter(
        JsonProtocolFilter configFilters,
        @PathParam("controller") String controllerName,
        @PathParam("topic") String topic,
        @PathParam("factType") String factClass) {

        if (configFilters == null) {
            return errorResponse(BAD_REQUEST, "Configuration Filters not provided");
        }

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            var decoder = EventProtocolCoderConstants.getManager()
                .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
            var filters = decoder.getCoder(factClass);
            if (filters == null) {
                return errorResponse(BAD_REQUEST, topic + ":" + factClass + DOES_NOT_EXIST_MSG);
            }
            filters.setFilter(configFilters);
            return filters;

        }, e -> {
            logger.debug(FETCH_DECODER_BY_FILTER_FAILED,
                this, controllerName, topic, factClass, configFilters, e.getMessage(), e);
            return (controllerName + ":" + topic + ":" + factClass);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}/rule")
    public Response decoderFilterRules(
        @PathParam("controller") String controllerName,
        @PathParam("topic") String topic,
        @PathParam("factType") String factClass) {

        return catchArgStateGenericEx(() -> {
            var result = this.checkControllerDecoderAndFilter(controllerName, topic, factClass);

            if (result instanceof Response) {
                return result;
            } else {
                var filter = (JsonProtocolFilter) result;
                return filter.getRule();
            }
        }, e -> {
            logger.debug(FETCH_DECODER_BY_TYPE_FAILED, this,
                controllerName, topic, factClass, e.getMessage(), e);
            return (controllerName + ":" + topic + ":" + factClass);
        });
    }

    /**
     * DELETE.
     *
     * @return response object
     */
    @Override
    @DELETE
    @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}/rule")
    public Response decoderFilterRuleDelete(
        @PathParam("controller") String controllerName,
        @PathParam("topic") String topic,
        @PathParam("factType") String factClass) {

        return catchArgStateGenericEx(() -> {
            var result = this.checkControllerDecoderAndFilter(controllerName, topic, factClass);

            if (result instanceof Response) {
                return result;
            } else {
                var filter = (JsonProtocolFilter) result;

                filter.setRule(null);
                return filter.getRule();
            }

        }, e -> {
            logger.debug(FETCH_DECODER_BY_TYPE_FAILED,
                this, controllerName, topic, factClass, e.getMessage(), e);
            return (controllerName + ":" + topic + ":" + factClass);
        });
    }

    /**
     * PUT.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}/rule")
    public Response decoderFilterRule(
        @PathParam("controller") String controllerName,
        @PathParam("topic") String topic,
        @PathParam("factType") String factClass,
        String rule) {

        return catchArgStateGenericEx(() -> decoderFilterRule2(controllerName, topic, factClass, rule), e -> {
            logger.debug("{}: cannot access decoder filter rules for policy-controller {} "
                    + "topic {} type {} because of {}",
                this, controllerName, topic, factClass, e.getMessage(), e);
            return (controllerName + ":" + topic + ":" + factClass);
        });
    }

    /**
     * POST.
     *
     * @return response object
     */
    @Override
    @POST
    @Path("engine/controllers/{controller}/decoders/{topic}")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response decode(
        @PathParam("controller") String controllerName,
        @PathParam("topic") String topic,
        String json) {

        if (StringUtils.isBlank(controllerName)) {
            return errorResponse(NOT_ACCEPTABLE, "controllerName contains whitespaces " + NOT_ACCEPTABLE_MSG);
        }

        if (StringUtils.isBlank(topic)) {
            return errorResponse(NOT_ACCEPTABLE, "topic contains whitespaces " + NOT_ACCEPTABLE_MSG);
        }

        var drools = getDroolsController(controllerName);

        var result = new CodingResult();
        result.setDecoding(false);
        result.setEncoding(false);
        result.setJsonEncoding(null);

        Object event;
        try {
            event = EventProtocolCoderConstants.getManager()
                .decode(drools.getGroupId(), drools.getArtifactId(), topic, json);
            result.setDecoding(true);
        } catch (final Exception e) {
            logger.debug(FETCH_POLICY_BY_TOPIC_FAILED, this, controllerName, topic, e.getMessage(), e);
            return errorResponse(BAD_REQUEST, e.getMessage());
        }

        try {
            result.setJsonEncoding(EventProtocolCoderConstants.getManager().encode(topic, event));
            result.setEncoding(true);
        } catch (final Exception e) {
            // continue so to propagate decoding results ...
            logger.debug("{}: cannot encode for policy-controller {} topic {} because of {}",
                this, controllerName, topic, e.getMessage(), e);
        }

        return Response.status(OK).entity(result).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/controllers/{controller}/encoders")
    public Response encoderFilters(@PathParam("controller") String controllerName) {

        return catchArgStateGenericEx(() -> {
            var drools = getDroolsController(controllerName);
            return EventProtocolCoderConstants.getManager()
                .getEncoderFilters(drools.getGroupId(), drools.getArtifactId());

        }, e -> {
            logger.debug(FETCH_ENCODER_BY_FILTER_FAILED, this, controllerName, e.getMessage(), e);
            return (controllerName);
        });
    }

    @Override
    @GET
    @Path("engine/topics")
    public Response topics() {
        return Response.status(OK).entity(TopicEndpointManager.getManager()).build();
    }

    @Override
    @GET
    @Path("engine/topics/switches")
    public Response topicSwitches() {
        return Response.status(OK).entity(SWITCHES).build();
    }

    /**
     * PUT.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/topics/switches/lock")
    public Response topicsLock() {
        if (TopicEndpointManager.getManager().lock()) {
            return Response.status(OK).entity(TopicEndpointManager.getManager()).build();
        } else {
            return errorResponse(NOT_ACCEPTABLE, CANNOT_PERFORM_OPERATION);
        }
    }

    /**
     * DELETE.
     *
     * @return response object
     */
    @Override
    @DELETE
    @Path("engine/topics/switches/lock")
    public Response topicsUnlock() {
        if (TopicEndpointManager.getManager().unlock()) {
            return Response.status(OK).entity(TopicEndpointManager.getManager()).build();
        } else {
            return errorResponse(NOT_ACCEPTABLE, CANNOT_PERFORM_OPERATION);
        }
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/topics/sources")
    public Response sources() {
        return Response.status(OK).entity(TopicEndpointManager.getManager().getTopicSources()).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/topics/sinks")
    public Response sinks() {
        return Response.status(OK).entity(TopicEndpointManager.getManager().getTopicSinks()).build();
    }

    /**
     * GET sources of a communication type.
     */
    @Override
    @GET
    @Path("engine/topics/sources/{comm: kafka|noop}")
    public Response commSources(
        @PathParam("comm") String comm) {
        if (StringUtils.isBlank(comm)) {
            return errorResponse(NOT_ACCEPTABLE,
                "source communication mechanism contains whitespaces " + NOT_ACCEPTABLE_MSG);
        }

        List<TopicSource> sources = new ArrayList<>();
        var status = OK;
        switch (CommInfrastructure.valueOf(comm.toUpperCase())) {
            case NOOP:
                sources.addAll(TopicEndpointManager.getManager().getNoopTopicSources());
                break;
            case KAFKA:
                sources.addAll(TopicEndpointManager.getManager().getKafkaTopicSources());
                break;
            default:
                status = BAD_REQUEST;
                logger.debug("{} is invalid communication mechanism", comm);
                break;
        }
        return Response.status(status).entity(sources).build();
    }

    /**
     * GET sinks of a communication type.
     */
    @Override
    @GET
    @Path("engine/topics/sinks/{comm: kafka|noop}")
    public Response commSinks(
        @PathParam("comm") String comm) {
        if (StringUtils.isBlank(comm)) {
            return errorResponse(NOT_ACCEPTABLE,
                "sink communication mechanism contains whitespaces " + NOT_ACCEPTABLE_MSG);
        }

        List<TopicSink> sinks = new ArrayList<>();
        var status = OK;
        switch (CommInfrastructure.valueOf(comm.toUpperCase())) {
            case NOOP:
                sinks.addAll(TopicEndpointManager.getManager().getNoopTopicSinks());
                break;
            case KAFKA:
                sinks.addAll(TopicEndpointManager.getManager().getKafkaTopicSinks());
                break;
            default:
                status = BAD_REQUEST;
                logger.debug("{} is invalid communication mechanism", comm);
                break;
        }
        return Response.status(status).entity(sinks).build();
    }

    /**
     * GET a source.
     */
    @Override
    @GET
    @Path("engine/topics/sources/{comm: kafka|noop}/{topic}")
    public Response sourceTopic(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source = TopicEndpointManager.getManager()
            .getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return Response.status(OK).entity(source).build();
    }

    /**
     * GET a sink.
     */
    @Override
    @GET
    @Path("engine/topics/sinks/{comm: kafka|noop}/{topic}")
    public Response sinkTopic(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink = TopicEndpointManager.getManager()
            .getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return Response.status(OK).entity(sink).build();
    }

    /**
     * GET a source events.
     */
    @Override
    @GET
    @Path("engine/topics/sources/{comm: kafka|noop}/{topic}/events")
    public Response sourceEvents(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source = TopicEndpointManager.getManager()
            .getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return Response.status(OK).entity(Arrays.asList(source.getRecentEvents())).build();
    }

    /**
     * GET a sink events.
     */
    @Override
    @GET
    @Path("engine/topics/sinks/{comm: kafka|noop}/{topic}/events")
    public Response sinkEvents(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink = TopicEndpointManager.getManager()
            .getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return Response.status(OK).entity(Arrays.asList(sink.getRecentEvents())).build();
    }

    /**
     * GET source topic switches.
     */
    @Override
    @GET
    @Path("engine/topics/sources/{comm: kafka|noop}/{topic}/switches")
    public Response commSourceTopicSwitches(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        return Response.status(OK).entity(SWITCHES).build();
    }

    /**
     * GET sink topic switches.
     */
    @Override
    @GET
    @Path("engine/topics/sinks/{comm: kafka|noop}/{topic}/switches")
    public Response commSinkTopicSwitches(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        return Response.status(OK).entity(SWITCHES).build();
    }

    /**
     * PUTs a lock on a topic.
     */
    @Override
    @PUT
    @Path("engine/topics/sources/{comm: kafka|noop}/{topic}/switches/lock")
    public Response commSourceTopicLock(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source = TopicEndpointManager.getManager()
            .getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, source.lock(), source);
    }

    /**
     * Deletes the lock on a topic.
     */
    @Override
    @DELETE
    @Path("engine/topics/sources/{comm: kafka|noop}/{topic}/switches/lock")
    public Response commSourceTopicUnlock(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source = TopicEndpointManager.getManager()
            .getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, source.unlock(), source);
    }

    /**
     * Starts a topic source.
     */
    @Override
    @PUT
    @Path("engine/topics/sources/{comm: kafka|noop}/{topic}/switches/activation")
    public Response commSourceTopicActivation(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source = TopicEndpointManager.getManager()
            .getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, source.start(), source);
    }

    /**
     * Stops a topic source.
     */
    @Override
    @DELETE
    @Path("engine/topics/sources/{comm: kafka|noop}/{topic}/switches/activation")
    public Response commSourceTopicDeactivation(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source = TopicEndpointManager.getManager()
            .getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, source.stop(), source);
    }

    /**
     * PUTs a lock on a topic.
     */
    @Override
    @PUT
    @Path("engine/topics/sinks/{comm: kafka|noop}/{topic}/switches/lock")
    public Response commSinkTopicLock(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink = TopicEndpointManager.getManager()
            .getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, sink.lock(), sink);
    }

    /**
     * Deletes the lock on a topic.
     */
    @Override
    @DELETE
    @Path("engine/topics/sinks/{comm: kafka|noop}/{topic}/switches/lock")
    public Response commSinkTopicUnlock(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink = TopicEndpointManager.getManager()
            .getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, sink.unlock(), sink);
    }

    /**
     * Starts a topic sink.
     */
    @Override
    @PUT
    @Path("engine/topics/sinks/{comm: kafka|noop}/{topic}/switches/activation")
    public Response commSinkTopicActivation(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink = TopicEndpointManager.getManager()
            .getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, sink.start(), sink);
    }

    /**
     * Stops a topic sink.
     */
    @Override
    @DELETE
    @Path("engine/topics/sinks/{comm: kafka|noop}/{topic}/switches/activation")
    public Response commSinkTopicDeactivation(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink = TopicEndpointManager.getManager()
            .getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, sink.stop(), sink);
    }

    /**
     * Offers an event to a topic in a communication infrastructure.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/topics/sources/{comm: kafka|noop}/{topic}/events")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response commEventOffer(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic,
        String json) {

        return catchArgStateGenericEx(() -> {
            var source = TopicEndpointManager.getManager()
                .getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
            if (source.offer(json)) {
                return Arrays.asList(source.getRecentEvents());
            } else {
                return errorResponse(NOT_ACCEPTABLE, "Failure to inject event over " + topic);
            }

        }, e -> {
            logger.debug(OFFER_FAILED, this, topic, e.getMessage(), e);
            return (topic);
        });
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/tools/uuid")
    @Produces(MediaType.TEXT_PLAIN)
    public Response uuid() {
        return Response.status(OK).entity(UUID.randomUUID().toString()).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/tools/loggers")
    public Response loggers() {
        final List<String> names = new ArrayList<>();
        if (checkLoggerFactoryInstance()) {
            return Response.status(INTERNAL_SERVER_ERROR).entity(names).build();
        }

        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (final Logger lgr : context.getLoggerList()) {
            names.add(lgr.getName());
        }

        return Response.status(OK).entity(names).build();
    }

    /**
     * GET.
     *
     * @return response object
     */
    @Override
    @GET
    @Path("engine/tools/loggers/{logger}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response loggerName1(@PathParam("logger") String loggerName) {
        if (checkLoggerFactoryInstance()) {
            return Response.status(INTERNAL_SERVER_ERROR).build();
        }

        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var lgr = context.getLogger(loggerName);
        if (lgr == null) {
            return Response.status(NOT_FOUND).build();
        }

        final String loggerLevel = (lgr.getLevel() != null) ? lgr.getLevel().toString() : "";
        return Response.status(OK).entity(loggerLevel).build();
    }

    /**
     * PUT.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/tools/loggers/{logger}/{level}")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    public Response loggerName(@PathParam("logger") String loggerName, @PathParam("level") String loggerLevel) {

        String newLevel;
        try {
            if (StringUtils.isBlank(loggerName)) {
                return errorResponse(NOT_ACCEPTABLE, "logger name:" + NOT_ACCEPTABLE_MSG);
            }
            if (!Pattern.matches("^[a-zA-Z]{3,5}$", loggerLevel)) {
                return errorResponse(NOT_ACCEPTABLE, "logger level:" + NOT_ACCEPTABLE_MSG);
            }
            newLevel = LoggerUtils.setLevel(loggerName, loggerLevel);
        } catch (Exception e) {
            logger.warn("{}: logging framework unavailable for {} / {}", this, loggerName, loggerLevel, e);
            return Response.status(INTERNAL_SERVER_ERROR).build();
        }

        return Response.status(OK).entity(newLevel).build();
    }

    protected String getSwaggerContents() {
        try (InputStream inputStream = getClass().getResourceAsStream(SWAGGER);
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new PolicyDroolsPdpRuntimeException("Cannot read swagger.json", e);
        }
    }

    /**
     * gets the underlying drools controller from the named policy controller.
     *
     * @param controllerName the policy controller name
     * @return the underlying drools controller
     * @throws IllegalArgumentException if an invalid controller name has been passed in
     */
    protected DroolsController getDroolsController(String controllerName) {
        var controller = PolicyControllerConstants.getFactory().get(controllerName);
        if (controller == null) {
            throw new IllegalArgumentException(controllerName + DOES_NOT_EXIST_MSG);
        }

        var drools = controller.getDrools();
        if (drools == null) {
            throw new IllegalArgumentException(controllerName + " has no drools configuration");
        }

        return drools;
    }

    /**
     * Invokes a function and returns the generated response, catching illegal argument,
     * illegal state, and generic runtime exceptions.
     *
     * @param responder function that will generate a response. If it returns a "Response"
     *                  object, then that object is returned as-is. Otherwise, this method will
     *                  return an "OK" Response, using the function's return value as the "entity"
     * @param errorMsg  function that will generate an error message prefix to be included
     *                  in responses generated as a result of catching an exception
     * @return a response
     */
    protected Response catchArgStateGenericEx(Supplier<Object> responder, Function<Exception, String> errorMsg) {
        try {
            Object result = responder.get();
            if (result instanceof Response) {
                return (Response) result;
            }

            return Response.status(OK).entity(result).build();

        } catch (final IllegalArgumentException e) {
            return errorResponse(NOT_FOUND, errorMsg.apply(e) + NOT_FOUND_MSG);

        } catch (final IllegalStateException e) {
            return errorResponse(NOT_ACCEPTABLE, errorMsg.apply(e) + NOT_ACCEPTABLE_MSG);

        } catch (final RuntimeException e) {
            errorMsg.apply(e);
            return errorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    protected Object decoderFilterRule2(String controllerName, String topic, String factClass, String rule) {
        if (StringUtils.isBlank(rule)) {
            var error = controllerName + ":" + topic + ":" + factClass + " no filter rule provided";
            return errorResponse(BAD_REQUEST, error);
        }

        var result = this.checkControllerDecoderAndFilter(controllerName, topic, factClass);

        if (result instanceof Response) {
            return result;
        } else {
            var filter = (JsonProtocolFilter) result;
            filter.setRule(rule);
            return filter.getRule();
        }
    }

    protected Object checkControllerDecoderAndFilter(String controllerName, String topic, String factClass) {
        var drools = this.getDroolsController(controllerName);
        var decoder = EventProtocolCoderConstants.getManager()
            .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);

        var filters = decoder.getCoder(factClass);
        if (filters == null) {
            var error = controllerName + ":" + topic + ":" + factClass + DOES_NOT_EXIST_MSG;
            return errorResponse(BAD_REQUEST, error);
        }

        var filter = filters.getFilter();
        if (filter == null) {
            var error = controllerName + ":" + topic + ":" + factClass + NO_FILTERS;
            return errorResponse(BAD_REQUEST, error);
        }
        return filter;
    }

    private Response getResponse(String topicName, boolean success, Topic topic) {
        if (success) {
            return Response.status(OK).entity(topic).build();
        } else {
            return errorResponse(NOT_ACCEPTABLE, "cannot perform operation on " + topicName);
        }
    }

    private static boolean checkLoggerFactoryInstance() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            logger.warn("The SLF4J logger factory is not configured for logback");
            return true;
        }
        return false;
    }

    private Response errorResponse(Status status, String errorMessage) {
        var error = new Error(errorMessage);
        return Response.status(status).entity(error).build();
    }

    /*
     * Helper classes for aggregation of results
     */

    /**
     * Coding/Encoding Results Aggregation Helper class.
     */
    @Getter
    @Setter
    public static class CodingResult {
        /**
         * serialized output.
         */

        private String jsonEncoding;
        /**
         * encoding result.
         */

        private Boolean encoding;

        /**
         * decoding result.
         */
        private Boolean decoding;
    }

    /**
     * Generic Error Reporting class.
     */
    @AllArgsConstructor
    public static class Error {
        private String msg;

        public String getError() {
            return msg;
        }

        public void setError(String msg) {
            this.msg = msg;
        }
    }
}
