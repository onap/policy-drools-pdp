/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
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

import ch.qos.logback.classic.LoggerContext;
import com.google.re2j.Pattern;
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
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.server.YamlMessageBodyHandler;
import org.onap.policy.common.utils.logging.LoggerUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.onap.policy.drools.protocol.coders.EventProtocolCoderConstants;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.ProtocolCoderToolset;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
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
    private static final String NO_FILTERS = " no filters";
    private static final String NOT_FOUND = " not found: ";
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

        try (InputStream inputStream = getClass().getResourceAsStream(SWAGGER);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String contents = reader.lines()
                    .collect(Collectors.joining(System.lineSeparator()));
            return Response.status(Response.Status.OK)
                        .entity(contents)
                        .build();
        } catch (IOException e) {
            logger.error("Cannot read swagger.json {} because of {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .build();
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
        return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager()).build();
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
            return Response.status(Response.Status.BAD_REQUEST).entity(PolicyEngineConstants.getManager()).build();
        }

        return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager()).build();
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
        return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager().getFeatures()).build();
    }

    @Override
    @GET
    @Path("engine/features/inventory")
    public Response engineFeaturesInventory() {
        return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager().getFeatureProviders())
            .build();
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
            return Response.status(Response.Status.OK)
                .entity(PolicyEngineConstants.getManager().getFeatureProvider(featureName)).build();
        } catch (final IllegalArgumentException iae) {
            logger.debug("feature unavailable: {}", featureName, iae);
            return Response.status(Response.Status.NOT_FOUND).entity(new Error(iae.getMessage())).build();
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
        return Response.status(Response.Status.OK).entity(INPUTS).build();
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
        final PolicyController controller = null;
        boolean success;
        try {
            success = PolicyEngineConstants.getManager().configure(configuration);
        } catch (final Exception e) {
            success = false;
            logger.info("{}: cannot configure {} because of {}", this, PolicyEngineConstants.getManager(),
                e.getMessage(), e);
        }

        if (!success) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).entity(new Error(CANNOT_PERFORM_OPERATION))
                .build();
        } else {
            return Response.status(Response.Status.OK).entity(controller).build();
        }
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
        return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager().getProperties()).build();
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
        return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager().getEnvironment()).build();
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
        return Response.status(Response.Status.OK)
            .entity(PolicyEngineConstants.getManager().getEnvironmentProperty(envProperty)).build();
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
        return Response.status(Response.Status.OK).entity(previousValue).build();
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
        return Response.status(Response.Status.OK).entity(SWITCHES).build();
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
        var success = true;
        try {
            PolicyEngineConstants.getManager().activate();
        } catch (final Exception e) {
            success = false;
            logger.info("{}: cannot activate {} because of {}", this, PolicyEngineConstants.getManager(),
                e.getMessage(), e);
        }

        if (!success) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).entity(new Error(CANNOT_PERFORM_OPERATION))
                .build();
        } else {
            return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager()).build();
        }
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
        var success = true;
        try {
            PolicyEngineConstants.getManager().deactivate();
        } catch (final Exception e) {
            success = false;
            logger.info("{}: cannot deactivate {} because of {}", this, PolicyEngineConstants.getManager(),
                e.getMessage(), e);
        }

        if (!success) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).entity(new Error(CANNOT_PERFORM_OPERATION))
                .build();
        } else {
            return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager()).build();
        }
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
        final boolean success = PolicyEngineConstants.getManager().lock();
        if (success) {
            return Response.status(Status.OK).entity(PolicyEngineConstants.getManager()).build();
        } else {
            return Response.status(Status.NOT_ACCEPTABLE).entity(new Error(CANNOT_PERFORM_OPERATION)).build();
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
        final boolean success = PolicyEngineConstants.getManager().unlock();
        if (success) {
            return Response.status(Status.OK).entity(PolicyEngineConstants.getManager()).build();
        } else {
            return Response.status(Status.NOT_ACCEPTABLE).entity(new Error(CANNOT_PERFORM_OPERATION)).build();
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
        return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager().getPolicyControllerIds())
            .build();
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
        return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager().getPolicyControllers())
            .build();
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
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error("A configuration must be provided"))
                .build();
        }

        final String controllerName = config.getProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME);
        if (controllerName == null || controllerName.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new Error(
                    "Configuration must have an entry for " + DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME))
                .build();
        }

        PolicyController controller;
        try {
            controller = PolicyControllerConstants.getFactory().get(controllerName);
            if (controller != null) {
                return Response.status(Response.Status.NOT_MODIFIED).entity(controller).build();
            }
        } catch (final IllegalArgumentException e) {
            logger.trace("OK ", e);
            // This is OK
        } catch (final IllegalStateException e) {
            logger.info(FETCH_POLICY_FAILED, this, e.getMessage(), e);
            return Response.status(Response.Status.NOT_ACCEPTABLE).entity(new Error(controllerName + NOT_FOUND_MSG))
                .build();
        }

        try {
            controller = PolicyEngineConstants.getManager().createPolicyController(
                config.getProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME), config);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("{}: cannot create policy-controller because of {}", this, e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error(e.getMessage())).build();
        }

        try {
            final boolean success = controller.start();
            if (!success) {
                logger.info("{}: cannot start {}", this, controller);
                return Response.status(Response.Status.PARTIAL_CONTENT)
                    .entity(new Error(controllerName + " can't be started")).build();
            }
        } catch (final IllegalStateException e) {
            logger.info("{}: cannot start {} because of {}", this, controller, e.getMessage(), e);
            return Response.status(Response.Status.PARTIAL_CONTENT).entity(controller).build();
        }

        return Response.status(Response.Status.CREATED).entity(controller).build();
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
        return Response.status(Response.Status.OK).entity(PolicyEngineConstants.getManager().getFeatures()).build();
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
        return Response.status(Response.Status.OK)
            .entity(PolicyControllerConstants.getFactory().getFeatureProviders()).build();
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
            return Response.status(Response.Status.OK)
                .entity(PolicyControllerConstants.getFactory().getFeatureProvider(featureName))
                .build();
        } catch (final IllegalArgumentException iae) {
            logger.debug("{}: cannot feature {} because of {}", this, featureName, iae.getMessage(), iae);
            return Response.status(Response.Status.NOT_FOUND).entity(new Error(iae.getMessage())).build();
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
            () -> Response.status(Response.Status.OK)
                .entity(PolicyControllerConstants.getFactory().get(controllerName)).build(),
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
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new Error(controllerName + DOES_NOT_EXIST_MSG)).build();
            }
        } catch (final IllegalArgumentException e) {
            logger.debug(FETCH_POLICY_BY_NAME_FAILED, this, controllerName, e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new Error(controllerName + NOT_FOUND + e.getMessage())).build();
        } catch (final IllegalStateException e) {
            logger.debug(FETCH_POLICY_BY_NAME_FAILED, this, controllerName, e.getMessage(), e);
            return Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity(new Error(controllerName + NOT_ACCEPTABLE_MSG)).build();
        }

        try {
            PolicyEngineConstants.getManager().removePolicyController(controllerName);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.debug("{}: cannot remove policy-controller {} because of {}", this, controllerName, e.getMessage(),
                e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new Error(e.getMessage())).build();
        }

        return Response.status(Response.Status.OK).entity(controller).build();
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
            final PolicyController controller = PolicyControllerConstants.getFactory().get(controllerName);
            return Response.status(Response.Status.OK).entity(controller.getProperties()).build();

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
        return Response.status(Response.Status.OK).entity(INPUTS).build();
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

        if (controllerName == null || controllerName.isEmpty() || controllerConfiguration == null
            || !controllerName.equals(controllerConfiguration.getName())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("A valid or matching controller names must be provided").build();
        }

        return catchArgStateGenericEx(() -> {
            var controller =
                PolicyEngineConstants.getManager().updatePolicyController(controllerConfiguration);
            if (controller == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new Error(controllerName + DOES_NOT_EXIST_MSG)).build();
            }

            return Response.status(Response.Status.OK).entity(controller).build();

        }, e -> {
            logger.info("{}: cannot update policy-controller {} because of {}", this, controllerName,
                e.getMessage(), e);
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
        return Response.status(Response.Status.OK).entity(SWITCHES).build();
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
        final boolean success = policyController.lock();
        if (success) {
            return Response.status(Status.OK).entity(policyController).build();
        } else {
            return Response.status(Status.NOT_ACCEPTABLE)
                .entity(new Error("Controller " + controllerName + " cannot be locked")).build();
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
            return Response.status(Status.OK).entity(policyController).build();
        } else {
            return Response.status(Status.NOT_ACCEPTABLE)
                .entity(new Error("Controller " + controllerName + " cannot be unlocked")).build();
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
            return Response.status(Response.Status.OK).entity(drools).build();

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
                this, controllerName, sessionName, queryName, queriedEntity, e.getMessage(), e);
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
    @Path("engine/controllers/tools/coders/decoders/filters/rule")
    public Response rules(String expression) {
        return Response.status(Status.OK).entity(new JsonProtocolFilter(expression)).build();
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
            logger.debug(FETCH_DECODERS_BY_POLICY_FAILED, this, controllerName,
                e.getMessage(), e);
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
            final ProtocolCoderToolset decoder = EventProtocolCoderConstants.getManager()
                .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
            if (decoder == null) {
                return Response.status(Response.Status.BAD_REQUEST).entity(new Error(topic + DOES_NOT_EXIST_MSG))
                    .build();
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
            final ProtocolCoderToolset decoder = EventProtocolCoderConstants.getManager()
                .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
            final CoderFilters filters = decoder.getCoder(factClass);
            if (filters == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new Error(topic + ":" + factClass + DOES_NOT_EXIST_MSG)).build();
            } else {
                return filters;
            }

        }, e -> {
            logger.debug(FETCH_DECODER_BY_TYPE_FAILED, this,
                controllerName, topic, factClass, e.getMessage(), e);
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
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error("Configuration Filters not provided"))
                .build();
        }

        return catchArgStateGenericEx(() -> {
            var drools = this.getDroolsController(controllerName);
            final ProtocolCoderToolset decoder = EventProtocolCoderConstants.getManager()
                .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
            final CoderFilters filters = decoder.getCoder(factClass);
            if (filters == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new Error(topic + ":" + factClass + DOES_NOT_EXIST_MSG)).build();
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
            var drools = this.getDroolsController(controllerName);
            final ProtocolCoderToolset decoder = EventProtocolCoderConstants.getManager()
                .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);

            final CoderFilters filters = decoder.getCoder(factClass);
            if (filters == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new Error(controllerName + ":" + topic + ":" + factClass + DOES_NOT_EXIST_MSG)).build();
            }

            final JsonProtocolFilter filter = filters.getFilter();
            if (filter == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new Error(controllerName + ":" + topic + ":" + factClass + NO_FILTERS)).build();
            }

            return filter.getRule();

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
            var drools = this.getDroolsController(controllerName);
            final ProtocolCoderToolset decoder = EventProtocolCoderConstants.getManager()
                .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);

            final CoderFilters filters = decoder.getCoder(factClass);
            if (filters == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new Error(controllerName + ":" + topic + ":" + factClass + DOES_NOT_EXIST_MSG)).build();
            }

            final JsonProtocolFilter filter = filters.getFilter();
            if (filter == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new Error(controllerName + ":" + topic + ":" + factClass + NO_FILTERS)).build();
            }

            filter.setRule(null);
            return filter.getRule();

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

    private Object decoderFilterRule2(String controllerName, String topic, String factClass, String rule) {
        var drools = this.getDroolsController(controllerName);
        final ProtocolCoderToolset decoder = EventProtocolCoderConstants.getManager()
            .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);

        final CoderFilters filters = decoder.getCoder(factClass);
        if (filters == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new Error(controllerName + ":" + topic + ":" + factClass + DOES_NOT_EXIST_MSG)).build();
        }

        final JsonProtocolFilter filter = filters.getFilter();
        if (filter == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new Error(controllerName + ":" + topic + ":" + factClass + NO_FILTERS)).build();
        }

        if (rule == null || rule.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error(controllerName + ":" + topic + ":"
                + factClass + " no filter rule provided")).build();
        }

        filter.setRule(rule);
        return filter.getRule();
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

        if (!checkValidNameInput(controllerName)) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity(new Error("controllerName contains whitespaces " + NOT_ACCEPTABLE_MSG)).build();
        }

        if (!checkValidNameInput(topic)) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity(new Error("topic contains whitespaces " + NOT_ACCEPTABLE_MSG)).build();
        }

        PolicyController policyController;
        try {
            policyController = PolicyControllerConstants.getFactory().get(controllerName);
        } catch (final IllegalArgumentException e) {
            logger.debug(FETCH_DECODERS_BY_TOPIC_FAILED, this,
                controllerName, topic, e.getMessage(), e);
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new Error(controllerName + ":" + topic + ":" + NOT_FOUND_MSG)).build();
        } catch (final IllegalStateException e) {
            logger.debug(FETCH_DECODERS_BY_TOPIC_FAILED, this,
                controllerName, topic, e.getMessage(), e);
            return Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity(new Error(controllerName + ":" + topic + ":" + NOT_ACCEPTABLE_MSG)).build();
        }

        var result = new CodingResult();
        result.setDecoding(false);
        result.setEncoding(false);
        result.setJsonEncoding(null);

        Object event;
        try {
            event = EventProtocolCoderConstants.getManager().decode(policyController.getDrools().getGroupId(),
                policyController.getDrools().getArtifactId(), topic, json);
            result.setDecoding(true);
        } catch (final Exception e) {
            logger.debug(FETCH_POLICY_BY_TOPIC_FAILED, this, controllerName, topic,
                e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error(e.getMessage())).build();
        }

        try {
            result.setJsonEncoding(EventProtocolCoderConstants.getManager().encode(topic, event));
            result.setEncoding(true);
        } catch (final Exception e) {
            // continue so to propagate decoding results ..
            logger.debug("{}: cannot encode for policy-controller {} topic {} because of {}", this, controllerName,
                topic, e.getMessage(), e);
        }

        return Response.status(Response.Status.OK).entity(result).build();
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
            final PolicyController controller = PolicyControllerConstants.getFactory().get(controllerName);
            var drools = controller.getDrools();
            return EventProtocolCoderConstants.getManager()
                .getEncoderFilters(drools.getGroupId(), drools.getArtifactId());

        }, e -> {
            logger.debug(FETCH_ENCODER_BY_FILTER_FAILED, this, controllerName,
                e.getMessage(), e);
            return (controllerName);
        });
    }

    @Override
    @GET
    @Path("engine/topics")
    public Response topics() {
        return Response.status(Response.Status.OK).entity(TopicEndpointManager.getManager()).build();
    }

    @Override
    @GET
    @Path("engine/topics/switches")
    public Response topicSwitches() {
        return Response.status(Response.Status.OK).entity(SWITCHES).build();
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
        final boolean success = TopicEndpointManager.getManager().lock();
        if (success) {
            return Response.status(Status.OK).entity(TopicEndpointManager.getManager()).build();
        } else {
            return Response.status(Status.NOT_ACCEPTABLE).entity(new Error(CANNOT_PERFORM_OPERATION)).build();
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
        final boolean success = TopicEndpointManager.getManager().unlock();
        if (success) {
            return Response.status(Status.OK).entity(TopicEndpointManager.getManager()).build();
        } else {
            return Response.status(Status.NOT_ACCEPTABLE).entity(new Error(CANNOT_PERFORM_OPERATION)).build();
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
        return Response.status(Response.Status.OK).entity(TopicEndpointManager.getManager().getTopicSources()).build();
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
        return Response.status(Response.Status.OK).entity(TopicEndpointManager.getManager().getTopicSinks()).build();
    }

    /**
     * GET sources of a communication type.
     */
    @Override
    @GET
    @Path("engine/topics/sources/{comm: ueb|dmaap|noop}")
    public Response commSources(
        @PathParam("comm") String comm) {
        if (!checkValidNameInput(comm)) {
            return Response
                .status(Response.Status.NOT_ACCEPTABLE)
                .entity(new Error("source communication mechanism contains whitespaces " + NOT_ACCEPTABLE_MSG))
                .build();
        }

        List<TopicSource> sources = new ArrayList<>();
        var status = Status.OK;
        switch (CommInfrastructure.valueOf(comm.toUpperCase())) {
            case UEB:
                sources.addAll(TopicEndpointManager.getManager().getUebTopicSources());
                break;
            case DMAAP:
                sources.addAll(TopicEndpointManager.getManager().getDmaapTopicSources());
                break;
            case NOOP:
                sources.addAll(TopicEndpointManager.getManager().getNoopTopicSources());
                break;
            default:
                status = Status.BAD_REQUEST;
                logger.debug("Invalid communication mechanism");
                break;
        }
        return Response.status(status).entity(sources).build();
    }

    /**
     * GET sinks of a communication type.
     */
    @Override
    @GET
    @Path("engine/topics/sinks/{comm: ueb|dmaap|noop}")
    public Response commSinks(
        @PathParam("comm") String comm) {
        if (!checkValidNameInput(comm)) {
            return Response
                .status(Response.Status.NOT_ACCEPTABLE)
                .entity(new Error("sink communication mechanism contains whitespaces " + NOT_ACCEPTABLE_MSG))
                .build();
        }

        List<TopicSink> sinks = new ArrayList<>();
        var status = Status.OK;
        switch (CommInfrastructure.valueOf(comm.toUpperCase())) {
            case UEB:
                sinks.addAll(TopicEndpointManager.getManager().getUebTopicSinks());
                break;
            case DMAAP:
                sinks.addAll(TopicEndpointManager.getManager().getDmaapTopicSinks());
                break;
            case NOOP:
                sinks.addAll(TopicEndpointManager.getManager().getNoopTopicSinks());
                break;
            default:
                status = Status.BAD_REQUEST;
                logger.debug("Invalid communication mechanism");
                break;
        }
        return Response.status(status).entity(sinks).build();
    }

    /**
     * GET a source.
     */
    @Override
    @GET
    @Path("engine/topics/sources/{comm: ueb|dmaap|noop}/{topic}")
    public Response sourceTopic(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        return Response
            .status(Response.Status.OK)
            .entity(TopicEndpointManager.getManager()
                .getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic))
            .build();
    }

    /**
     * GET a sink.
     */
    @Override
    @GET
    @Path("engine/topics/sinks/{comm: ueb|dmaap|noop}/{topic}")
    public Response sinkTopic(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        return Response
            .status(Response.Status.OK)
            .entity(TopicEndpointManager.getManager()
                .getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic))
            .build();
    }

    /**
     * GET a source events.
     */
    @Override
    @GET
    @Path("engine/topics/sources/{comm: ueb|dmaap|noop}/{topic}/events")
    public Response sourceEvents(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        return Response.status(Status.OK)
            .entity(Arrays.asList(TopicEndpointManager.getManager()
                .getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic)
                .getRecentEvents()))
            .build();
    }

    /**
     * GET a sink events.
     */
    @Override
    @GET
    @Path("engine/topics/sinks/{comm: ueb|dmaap|noop}/{topic}/events")
    public Response sinkEvents(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        return Response.status(Status.OK)
            .entity(Arrays.asList(TopicEndpointManager.getManager()
                .getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic)
                .getRecentEvents()))
            .build();
    }

    /**
     * GET source topic switches.
     */
    @Override
    @GET
    @Path("engine/topics/sources/{comm: ueb|dmaap|noop}/{topic}/switches")
    public Response commSourceTopicSwitches(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        return Response.status(Response.Status.OK).entity(SWITCHES).build();
    }

    /**
     * GET sink topic switches.
     */
    @Override
    @GET
    @Path("engine/topics/sinks/{comm: ueb|dmaap|noop}/{topic}/switches")
    public Response commSinkTopicSwitches(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        return Response.status(Response.Status.OK).entity(SWITCHES).build();
    }

    /**
     * PUTs a lock on a topic.
     */
    @Override
    @PUT
    @Path("engine/topics/sources/{comm: ueb|dmaap|noop}/{topic}/switches/lock")
    public Response commSourceTopicLock(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source =
            TopicEndpointManager.getManager().getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, source.lock(), source);
    }

    /**
     * DELETEs the lock on a topic.
     */
    @Override
    @DELETE
    @Path("engine/topics/sources/{comm: ueb|dmaap|noop}/{topic}/switches/lock")
    public Response commSourceTopicUnlock(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source =
            TopicEndpointManager.getManager().getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, source.unlock(), source);
    }

    /**
     * Starts a topic source.
     */
    @Override
    @PUT
    @Path("engine/topics/sources/{comm: ueb|dmaap|noop}/{topic}/switches/activation")
    public Response commSourceTopicActivation(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source =
            TopicEndpointManager.getManager().getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, source.start(), source);
    }

    /**
     * Stops a topic source.
     */
    @Override
    @DELETE
    @Path("engine/topics/sources/{comm: ueb|dmaap|noop}/{topic}/switches/activation")
    public Response commSourceTopicDeactivation(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var source =
            TopicEndpointManager.getManager().getTopicSource(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, source.stop(), source);
    }

    /**
     * PUTs a lock on a topic.
     */
    @Override
    @PUT
    @Path("engine/topics/sinks/{comm: ueb|dmaap|noop}/{topic}/switches/lock")
    public Response commSinkTopicLock(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink =
            TopicEndpointManager.getManager().getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, sink.lock(), sink);
    }

    /**
     * DELETEs the lock on a topic.
     */
    @Override
    @DELETE
    @Path("engine/topics/sinks/{comm: ueb|dmaap|noop}/{topic}/switches/lock")
    public Response commSinkTopicUnlock(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink =
            TopicEndpointManager.getManager().getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, sink.unlock(), sink);
    }

    /**
     * Starts a topic sink.
     */
    @Override
    @PUT
    @Path("engine/topics/sinks/{comm: ueb|dmaap|noop}/{topic}/switches/activation")
    public Response commSinkTopicActivation(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink =
            TopicEndpointManager.getManager().getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, sink.start(), sink);
    }

    /**
     * Stops a topic sink.
     */
    @Override
    @DELETE
    @Path("engine/topics/sinks/{comm: ueb|dmaap|noop}/{topic}/switches/activation")
    public Response commSinkTopicDeactivation(
        @PathParam("comm") String comm,
        @PathParam("topic") String topic) {
        var sink =
            TopicEndpointManager.getManager().getTopicSink(CommInfrastructure.valueOf(comm.toUpperCase()), topic);
        return getResponse(topic, sink.stop(), sink);
    }

    private Response getResponse(String topicName, boolean success, Topic topic) {
        if (success) {
            return Response.status(Status.OK).entity(topic).build();
        } else {
            return Response.status(Status.NOT_ACCEPTABLE).entity(makeTopicOperError(topicName)).build();
        }
    }

    private Error makeTopicOperError(String topic) {
        return new Error("cannot perform operation on " + topic);
    }

    /**
     * Offers an event to a topic in a communication infrastructure.
     *
     * @return response object
     */
    @Override
    @PUT
    @Path("engine/topics/sources/{comm: ueb|dmaap|noop}/{topic}/events")
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
                return Response.status(Status.NOT_ACCEPTABLE).entity(new Error("Failure to inject event over " + topic))
                    .build();
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
        return Response.status(Status.OK).entity(UUID.randomUUID().toString()).build();
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
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            logger.warn("The SLF4J logger factory is not configured for logback");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(names).build();
        }

        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (final Logger lgr : context.getLoggerList()) {
            names.add(lgr.getName());
        }

        return Response.status(Status.OK).entity(names).build();
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
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            logger.warn("The SLF4J logger factory is not configured for logback");
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var lgr = context.getLogger(loggerName);
        if (lgr == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        final String loggerLevel = (lgr.getLevel() != null) ? lgr.getLevel().toString() : "";
        return Response.status(Status.OK).entity(loggerLevel).build();
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
            if (!checkValidNameInput(loggerName)) {
                return Response.status(Response.Status.NOT_ACCEPTABLE)
                    .entity(new Error("logger name: " + NOT_ACCEPTABLE_MSG))
                    .build();
            }
            if (!Pattern.matches("^[a-zA-Z]{3,5}$", loggerLevel)) {
                return Response.status(Response.Status.NOT_ACCEPTABLE)
                    .entity(new Error("logger level: " + NOT_ACCEPTABLE_MSG))
                    .build();
            }
            newLevel = LoggerUtils.setLevel(loggerName, loggerLevel);
        } catch (final IllegalArgumentException e) {
            logger.warn("{}: invalid operation for logger {} and level {}", this, loggerName, loggerLevel, e);
            return Response.status(Status.NOT_FOUND).build();
        } catch (final IllegalStateException e) {
            logger.warn("{}: logging framework unavailable for {} / {}", this, loggerName, loggerLevel, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.status(Status.OK).entity(newLevel

        ).build();
    }

    /**
     * gets the underlying drools controller from the named policy controller.
     *
     * @param controllerName the policy controller name
     * @return the underlying drools controller
     * @throws IllegalArgumentException if an invalid controller name has been passed in
     */
    protected DroolsController getDroolsController(String controllerName) {
        final PolicyController controller = PolicyControllerConstants.getFactory().get(controllerName);
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
     *        object, then that object is returned as-is. Otherwise, this method will
     *        return an "OK" Response, using the function's return value as the "entity"
     * @param errorMsg function that will generate an error message prefix to be included
     *        in responses generated as a result of catching an exception
     * @return a response
     */
    private Response catchArgStateGenericEx(Supplier<Object> responder, Function<Exception, String> errorMsg) {
        try {
            Object result = responder.get();
            if (result instanceof Response) {
                return (Response) result;
            }

            return Response.status(Response.Status.OK).entity(result).build();

        } catch (final IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(new Error(errorMsg.apply(e) + NOT_FOUND_MSG))
                .build();

        } catch (final IllegalStateException e) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity(new Error(errorMsg.apply(e) + NOT_ACCEPTABLE_MSG)).build();

        } catch (final RuntimeException e) {
            errorMsg.apply(e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new Error(e.getMessage())).build();
        }
    }

    public static boolean checkValidNameInput(String test) {
        return Pattern.matches("\\S+", test);
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
