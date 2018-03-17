/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

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

import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.event.comm.TopicEndpoint;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.event.comm.TopicSource;
import org.onap.policy.drools.event.comm.bus.DmaapTopicSink;
import org.onap.policy.drools.event.comm.bus.DmaapTopicSource;
import org.onap.policy.drools.event.comm.bus.NoopTopicSink;
import org.onap.policy.drools.event.comm.bus.UebTopicSink;
import org.onap.policy.drools.event.comm.bus.UebTopicSource;
import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter.FilterRule;
import org.onap.policy.drools.protocol.coders.ProtocolCoderToolset;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.utils.LoggerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;


/**
 * Telemetry JAX-RS Interface to the PDP-D
 */

@Path("/policy/pdp")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api
@SwaggerDefinition(
    info = @Info(description = "PDP-D Telemetry Services", version = "v1.0",
        title = "PDP-D Telemetry"),
    consumes = {MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN},
    produces = {MediaType.APPLICATION_JSON}, schemes = {SwaggerDefinition.Scheme.HTTP},
    tags = {@Tag(name = "pdp-d-telemetry", description = "Drools PDP Telemetry Operations")})
public class RestManager {
  /**
   * Logger
   */
  private static Logger logger = LoggerFactory.getLogger(RestManager.class);

  @GET
  @Path("engine")
  @ApiOperation(value = "Retrieves the Engine Operational Status",
      notes = "Top-level abstraction.  Provides a global view of resources",
      response = PolicyEngine.class)
  public Response engine() {
    return Response.status(Response.Status.OK).entity(PolicyEngine.manager).build();
  }

  @DELETE
  @Path("engine")
  @ApiOperation(value = "Shuts down the Engine",
      notes = "Deleting the engine, the top-level abstraction, equivalenty shuts it down",
      response = PolicyEngine.class)
  public Response engineShutdown() {
    try {
      PolicyEngine.manager.shutdown();
    } catch (final IllegalStateException e) {
      logger.error("{}: cannot shutdown {} because of {}", this, PolicyEngine.manager,
          e.getMessage(), e);
      return Response.status(Response.Status.BAD_REQUEST).entity(PolicyEngine.manager).build();
    }

    return Response.status(Response.Status.OK).entity(PolicyEngine.manager).build();
  }

  @GET
  @Path("engine/features")
  @ApiOperation(value = "Engine Features",
      notes = "Provides the list of loaded features using the PolicyEngineFeatureAPI",
      responseContainer = "List")
  public Response engineFeatures() {
    return Response.status(Response.Status.OK).entity(PolicyEngine.manager.getFeatures()).build();
  }

  @GET
  @Path("engine/features/inventory")
  @ApiOperation(value = "Engine Detailed Feature Inventory",
      notes = "Provides detailed list of loaded features using the PolicyEngineFeatureAPI",
      responseContainer = "List", response = PolicyEngineFeatureAPI.class)
  public Response engineFeaturesInventory() {
    return Response.status(Response.Status.OK).entity(PolicyEngine.manager.getFeatureProviders())
        .build();
  }

  @GET
  @Path("engine/features/{featureName}")
  @ApiOperation(value = "Engine Feature",
      notes = "Provides Details for a given feature Engine Provider",
      response = PolicyEngineFeatureAPI.class)
  @ApiResponses(value = {@ApiResponse(code = 404, message = "The feature cannot be found")})
  public Response engineFeature(@ApiParam(value = "Feature Name",
      required = true) @PathParam("featureName") String featureName) {
    try {
      return Response.status(Response.Status.OK)
          .entity(PolicyEngine.manager.getFeatureProvider(featureName)).build();
    } catch (final IllegalArgumentException iae) {
      logger.debug("feature unavailable: {}", featureName, iae);
      return Response.status(Response.Status.NOT_FOUND).entity(new Error(iae.getMessage())).build();
    }
  }

  @GET
  @Path("engine/inputs")
  @ApiOperation(value = "Engine Input Ports", notes = "List of input ports",
      responseContainer = "List")
  public Response engineInputs() {
    return Response.status(Response.Status.OK).entity(Arrays.asList(Inputs.values())).build();
  }

  @POST
  @Path("engine/inputs/configuration")
  @ApiOperation(value = "Engine Input Configuration Requests",
      notes = "Feeds a configuration request input into the Engine")
  @ApiResponses(
      value = {@ApiResponse(code = 406, message = "The configuration request cannot be honored")})
  public Response engineUpdate(@ApiParam(value = "Configuration to apply",
      required = true) PdpdConfiguration configuration) {
    final PolicyController controller = null;
    boolean success = true;
    try {
      success = PolicyEngine.manager.configure(configuration);
    } catch (final Exception e) {
      success = false;
      logger.info("{}: cannot configure {} because of {}", this, PolicyEngine.manager,
          e.getMessage(), e);
    }

    if (!success)
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error("cannot perform operation")).build();
    else
      return Response.status(Response.Status.OK).entity(controller).build();
  }

  @GET
  @Path("engine/properties")
  @ApiOperation(value = "Engine Configuration Properties",
      notes = "Used for booststrapping the engine", response = Properties.class)
  public Response engineProperties() {
    return Response.status(Response.Status.OK).entity(PolicyEngine.manager.getProperties()).build();
  }

  @GET
  @Path("engine/environment")
  @ApiOperation(value = "Engine Environment Properties",
      notes = "Installation and OS environment properties used by the engine",
      response = Properties.class)
  public Response engineEnvironment() {
    return Response.status(Response.Status.OK).entity(PolicyEngine.manager.getEnvironment())
        .build();
  }

  @GET
  @Path("engine/environment/{envProperty}")
  @Consumes(MediaType.TEXT_PLAIN)
  @ApiOperation(value = "Gets an environment variable", response = String.class)
  public Response engineEnvironment(@ApiParam(value = "Environment Property",
      required = true) @PathParam("envProperty") String envProperty) {
    return Response.status(Response.Status.OK)
        .entity(PolicyEngine.manager.getEnvironmentProperty(envProperty)).build();
  }

  @PUT
  @Path("engine/environment/{envProperty}")
  @Consumes(MediaType.TEXT_PLAIN)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiOperation(value = "Adds a new environment value to the engine", response = String.class)
  public Response engineEnvironmentAdd(
      @ApiParam(value = "Environment Property",
          required = true) @PathParam("envProperty") String envProperty,
      @ApiParam(value = "Environment Value", required = true) String envValue) {
    final String previousValue = PolicyEngine.manager.setEnvironmentProperty(envProperty, envValue);
    return Response.status(Response.Status.OK).entity(previousValue).build();
  }

  @GET
  @Path("engine/switches")
  @ApiOperation(value = "Engine Control Switches", notes = "List of the Engine Control Switches",
      responseContainer = "List")
  public Response engineSwitches() {
    return Response.status(Response.Status.OK).entity(Arrays.asList(Switches.values())).build();
  }

  @PUT
  @Path("engine/switches/activation")
  @ApiOperation(value = "Switches on the Engine Activation Switch",
      notes = "Turns on Activation Switch on the Engine. This order entails that the engine "
          + "and controllers are unlocked and started",
      response = PolicyEngine.class)
  public Response engineActivation() {
    boolean success = true;
    try {
      PolicyEngine.manager.activate();
    } catch (final Exception e) {
      success = false;
      logger.info("{}: cannot activate {} because of {}", this, PolicyEngine.manager,
          e.getMessage(), e);
    }

    if (!success)
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error("cannot perform operation")).build();
    else
      return Response.status(Response.Status.OK).entity(PolicyEngine.manager).build();
  }

  @DELETE
  @Path("engine/switches/activation")
  @ApiOperation(value = "Switches off Engine Activation Switch",
      notes = "Turns off the Activation Switch on the Engine. This order entails that the engine "
          + "and controllers are locked (with the exception of those resources defined as unmanaged)",
      response = PolicyEngine.class)
  public Response engineDeactivation() {
    boolean success = true;
    try {
      PolicyEngine.manager.deactivate();
    } catch (final Exception e) {
      success = false;
      logger.info("{}: cannot deactivate {} because of {}", this, PolicyEngine.manager,
          e.getMessage(), e);
    }

    if (!success)
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error("cannot perform operation")).build();
    else
      return Response.status(Response.Status.OK).entity(PolicyEngine.manager).build();
  }

  @PUT
  @Path("engine/switches/lock")
  @ApiOperation(value = "Switches on the Engine Lock Control",
      notes = "This switch locks all the engine resources as a whole, except those that are defined unmanaged",
      response = PolicyEngine.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response engineLock() {
    final boolean success = PolicyEngine.manager.lock();
    if (success)
      return Response.status(Status.OK).entity(PolicyEngine.manager).build();
    else
      return Response.status(Status.NOT_ACCEPTABLE).entity(new Error("cannot perform operation"))
          .build();
  }

  @DELETE
  @Path("engine/switches/lock")
  @ApiOperation(value = "Switches off the Lock control",
      notes = "This switch locks all the engine resources as a whole, except those that are defined unmanaged",
      response = PolicyEngine.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response engineUnlock() {
    final boolean success = PolicyEngine.manager.unlock();
    if (success)
      return Response.status(Status.OK).entity(PolicyEngine.manager).build();
    else
      return Response.status(Status.NOT_ACCEPTABLE).entity(new Error("cannot perform operation"))
          .build();
  }

  @GET
  @Path("engine/controllers")
  @ApiOperation(value = "Lists the Policy Controllers Names",
      notes = "Unique Policy Controller Identifiers", responseContainer = "List")
  public Response controllers() {
    return Response.status(Response.Status.OK).entity(PolicyEngine.manager.getPolicyControllerIds())
        .build();
  }

  @GET
  @Path("engine/controllers/inventory")
  @ApiOperation(value = "Lists the Policy Controllers",
      notes = "Detailed list of Policy Controllers", responseContainer = "List",
      response = PolicyController.class)
  public Response controllerInventory() {
    return Response.status(Response.Status.OK).entity(PolicyEngine.manager.getPolicyControllers())
        .build();
  }

  @POST
  @Path("engine/controllers")
  @ApiOperation(value = "Creates and starts a new Policy Controller",
      notes = "Controller creation based on properties", response = PolicyController.class)
  @ApiResponses(value = {
      @ApiResponse(code = 400, message = "Invalid configuration information has been provided"),
      @ApiResponse(code = 304, message = "The controller already exists"),
      @ApiResponse(code = 406,
          message = "The administrative state of the system prevents it "
              + "from processing this request"),
      @ApiResponse(code = 206,
          message = "The controller has been created " + "but cannot be started"),
      @ApiResponse(code = 201,
          message = "The controller has been succesfully created and started")})
  public Response controllerAdd(
      @ApiParam(value = "Configuration Properties to apply", required = true) Properties config) {
    if (config == null)
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new Error("A configuration must be provided")).build();

    final String controllerName = config.getProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME);
    if (controllerName == null || controllerName.isEmpty())
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new Error(
              "Configuration must have an entry for " + PolicyProperties.PROPERTY_CONTROLLER_NAME))
          .build();

    PolicyController controller;
    try {
      controller = PolicyController.factory.get(controllerName);
      if (controller != null)
        return Response.status(Response.Status.NOT_MODIFIED).entity(controller).build();
    } catch (final IllegalArgumentException e) {
    	logger.trace("OK ", e);
      // This is OK
    } catch (final IllegalStateException e) {
      logger.info("{}: cannot get policy-controller because of {}", this, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " not found")).build();
    }

    try {
      controller = PolicyEngine.manager.createPolicyController(
          config.getProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME), config);
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
      logger.info("{}: cannot start {} because of {}", this, controller, e.getMessage(), e);;
      return Response.status(Response.Status.PARTIAL_CONTENT).entity(controller).build();
    }

    return Response.status(Response.Status.CREATED).entity(controller).build();
  }

  @GET
  @Path("engine/controllers/features")
  @ApiOperation(value = "Lists of Feature Providers Identifiers",
      notes = "Unique Policy Controller Identifiers", responseContainer = "List")
  public Response controllerFeatures() {
    return Response.status(Response.Status.OK).entity(PolicyEngine.manager.getFeatures()).build();
  }

  @GET
  @Path("engine/controllers/features/inventory")
  @ApiOperation(value = "Detailed Controllers Feature Inventory",
      notes = "Provides detailed list of loaded features using the PolicyControllerFeatureAPI",
      responseContainer = "List", response = PolicyControllerFeatureAPI.class)
  public Response controllerFeaturesInventory() {
    return Response.status(Response.Status.OK)
        .entity(PolicyController.factory.getFeatureProviders()).build();
  }

  @GET
  @Path("engine/controllers/features/{featureName}")
  @ApiOperation(value = "Controller Feature",
      notes = "Provides Details for a given Policy Controller feature provider",
      response = PolicyControllerFeatureAPI.class)
  @ApiResponses(value = {@ApiResponse(code = 404, message = "The feature cannot be found")})
  public Response controllerFeature(@ApiParam(value = "Feature Name",
      required = true) @PathParam("featureName") String featureName) {
    try {
      return Response.status(Response.Status.OK)
          .entity(PolicyController.factory.getFeatureProvider(featureName)).build();
    } catch (final IllegalArgumentException iae) {
      logger.debug("{}: cannot feature {} because of {}", this, featureName, iae.getMessage(), iae);
      return Response.status(Response.Status.NOT_FOUND).entity(new Error(iae.getMessage())).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}")
  @ApiOperation(value = "Retrieves a Policy Controller",
      notes = "A Policy Controller is a concrete drools application abstraction.  "
          + "It aggregates networking, drools, and other resources,"
          + "as provides operational controls over drools applications",
      response = PolicyController.class)
  @ApiResponses(value = {@ApiResponse(code = 404, message = "The controller cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response controller(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {
    try {
      return Response.status(Response.Status.OK)
          .entity(PolicyController.factory.get(controllerName)).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " not acceptable")).build();
    }
  }

  @DELETE
  @Path("engine/controllers/{controller}")
  @ApiOperation(value = "Deletes a Policy Controller",
      notes = "A Policy Controller is a concrete drools application abstraction.  "
          + "It aggregates networking, drools, and other resources,"
          + "as provides operational controls over drools applications",
      response = PolicyController.class)
  @ApiResponses(value = {@ApiResponse(code = 404, message = "The controller cannot be found"),
      @ApiResponse(code = 406,
          message = "The system is an administrative state that prevents "
              + "this request to be fulfilled"),
      @ApiResponse(code = 500,
          message = "A problem has occurred while deleting the Policy Controller")})
  public Response controllerDelete(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {

    PolicyController controller;
    try {
      controller = PolicyController.factory.get(controllerName);
      if (controller == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + "  does not exist")).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new Error(controllerName + " not found: " + e.getMessage())).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " not acceptable")).build();
    }

    try {
      PolicyEngine.manager.removePolicyController(controllerName);
    } catch (IllegalArgumentException | IllegalStateException e) {
      logger.debug("{}: cannot remove policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new Error(e.getMessage())).build();
    }

    return Response.status(Response.Status.OK).entity(controller).build();
  }

  @GET
  @Path("engine/controllers/{controller}/properties")
  @ApiOperation(value = "Retrieves the configuration properties of a Policy Controller",
      notes = "Configuration resources used by the controller if Properties format",
      response = PolicyController.class)
  @ApiResponses(value = {@ApiResponse(code = 404, message = "The controller cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response controllerProperties(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {
    try {
      final PolicyController controller = PolicyController.factory.get(controllerName);
      return Response.status(Response.Status.OK).entity(controller.getProperties()).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " not acceptable")).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/inputs")
  @ApiOperation(value = "Policy Controller Input Ports", notes = "List of input ports",
      responseContainer = "List")
  public Response controllerInputs() {
    return Response.status(Response.Status.OK).entity(Arrays.asList(Inputs.values())).build();
  }

  @POST
  @Path("engine/controllers/{controller}/inputs/configuration")
  @ApiOperation(value = "Policy Controller Input Configuration Requests",
      notes = "Feeds a configuration request input into the given Policy Controller")
  @ApiResponses(value = {@ApiResponse(code = 400, message = "The configuration request is invalid"),
      @ApiResponse(code = 406, message = "The configuration request cannot be honored")})
  public Response controllerUpdate(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Configuration to apply",
          required = true) ControllerConfiguration controllerConfiguration) {

    if (controllerName == null || controllerName.isEmpty() || controllerConfiguration == null
        || controllerConfiguration.getName().intern() != controllerName)
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("A valid or matching controller names must be provided").build();

    PolicyController controller;
    try {
      controller = PolicyEngine.manager.updatePolicyController(controllerConfiguration);
      if (controller == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + "  does not exist")).build();
    } catch (final IllegalArgumentException e) {
      logger.info("{}: cannot update policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new Error(controllerName + " not found: " + e.getMessage())).build();
    } catch (final Exception e) {
      logger.info("{}: cannot update policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " not acceptable")).build();
    }

    return Response.status(Response.Status.OK).entity(controller).build();
  }

  @GET
  @Path("engine/controllers/{controller}/switches")
  @ApiOperation(value = "Policy Controller Switches",
      notes = "List of the Policy Controller Switches", responseContainer = "List")
  public Response controllerSwitches() {
    return Response.status(Response.Status.OK).entity(Arrays.asList(Switches.values())).build();
  }

  @PUT
  @Path("engine/controllers/{controller}/switches/lock")
  @ApiOperation(value = "Switches on the Policy Controller Lock Control",
      notes = "This action on the switch locks the Policy Controller",
      response = PolicyController.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response controllerLock(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {
    final PolicyController policyController = PolicyController.factory.get(controllerName);
    final boolean success = policyController.lock();
    if (success)
      return Response.status(Status.OK).entity(policyController).build();
    else
      return Response.status(Status.NOT_ACCEPTABLE)
          .entity(new Error("Controller " + controllerName + " cannot be locked")).build();
  }

  @DELETE
  @Path("engine/controllers/{controller}/switches/lock")
  @ApiOperation(value = "Switches off the Policy Controller Lock Control",
      notes = "This action on the switch unlocks the Policy Controller",
      response = PolicyController.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response controllerUnlock(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {
    final PolicyController policyController = PolicyController.factory.get(controllerName);
    final boolean success = policyController.unlock();
    if (success)
      return Response.status(Status.OK).entity(policyController).build();
    else
      return Response.status(Status.NOT_ACCEPTABLE)
          .entity(new Error("Controller " + controllerName + " cannot be unlocked")).build();
  }

  @GET
  @Path("engine/controllers/{controller}/drools")
  @ApiOperation(value = "Retrieves the Drools Controller subcomponent of the Policy Controller",
      notes = "The Drools Controller provides an abstraction over the Drools subsystem",
      response = DroolsController.class)
  @ApiResponses(value = {@ApiResponse(code = 404, message = "The controller cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response drools(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      return Response.status(Response.Status.OK).entity(drools).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get drools-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get drools-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " not acceptable")).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/drools/facts")
  @ApiOperation(value = "Retrieves Facts Summary information for a given controller",
      notes = "Provides the session names, and a count of fact object in the drools working memory",
      responseContainer = "Map")
  @ApiResponses(value = {@ApiResponse(code = 404, message = "The controller cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response droolsFacts(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {
    try {
      final Map<String, Long> sessionCounts = new HashMap<>();
      final DroolsController drools = this.getDroolsController(controllerName);
      for (final String session : drools.getSessionNames()) {
        sessionCounts.put(session, drools.factCount(session));
      }
      return Response.status(Response.Status.OK).entity(sessionCounts).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " not acceptable")).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/drools/facts/{session}")
  @ApiOperation(value = "Retrieves Fact Types (classnames) for a given controller and its count",
      notes = "The fact types are the classnames of the objects inserted in the drools working memory",
      responseContainer = "Map")
  @ApiResponses(
      value = {@ApiResponse(code = 404, message = "The controller or session cannot be found"),
          @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
              + "this request to be fulfilled")})
  public Response droolsFacts(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Drools Session Name",
          required = true) @PathParam("session") String sessionName) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      return Response.status(Response.Status.OK).entity(drools.factClassNames(sessionName)).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get drools-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND).entity(new Error("entity not found"))
          .build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get drools-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + ":" + sessionName + " not acceptable")).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/drools/facts/{session}/{factType}")
  @ApiOperation(
      value = "Retrieves fact objects of a given type in the drools working memory"
          + "for a given controller and session",
      notes = "The fact types are the classnames of the objects inserted in the drools working memory",
      responseContainer = "List")
  @ApiResponses(value = {
      @ApiResponse(code = 404, message = "The controller, session, or fact type cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response droolsFacts(
      @ApiParam(value = "Fact count",
          required = false) @DefaultValue("false") @QueryParam("count") boolean count,
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Drools Session Name",
          required = true) @PathParam("session") String sessionName,
      @ApiParam(value = "Drools Fact Type",
          required = true) @PathParam("factType") String factType) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final List<Object> facts = drools.facts(sessionName, factType, false);
      if (!count)
        return Response.status(Response.Status.OK).entity(facts).build();
      else
        return Response.status(Response.Status.OK).entity(facts.size()).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + ":" + sessionName + ":" + factType + " not found"))
          .build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get policy-controller {} because of {}", this, controllerName,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(
              new Error(controllerName + ":" + sessionName + ":" + factType + " not acceptable"))
          .build();
    }
  }

  @DELETE
  @Path("engine/controllers/{controller}/drools/facts/{session}/{factType}")
  @ApiOperation(
      value = "Deletes all the fact objects of a given type from the drools working memory"
          + "for a given controller and session.   The objects retracted from the working "
          + "memory are provided in the response.",
      notes = "The fact types are the classnames of the objects inserted in the drools working memory",
      responseContainer = "List")
  @ApiResponses(value = {
      @ApiResponse(code = 404, message = "The controller, session, or fact type, cannot be found"),
      @ApiResponse(code = 406,
          message = "The system is an administrative state that prevents "
              + "this request to be fulfilled"),
      @ApiResponse(code = 500, message = "A server error has occurred processing this request")})
  public Response droolsFactsDelete(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Drools Session Name",
          required = true) @PathParam("session") String sessionName,
      @ApiParam(value = "Drools Fact Type",
          required = true) @PathParam("factType") String factType) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final List<Object> facts = drools.facts(sessionName, factType, true);
      return Response.status(Response.Status.OK).entity(facts).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get: drools-controller {}, session {}, factType {}, because of {}",
          this, controllerName, sessionName, factType, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + ":" + sessionName + ":" + factType + " not found"))
          .build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get: drools-controller {}, session {}, factType {}, because of {}",
          this, controllerName, sessionName, factType, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(
              new Error(controllerName + ":" + sessionName + ":" + factType + " not acceptable"))
          .build();
    } catch (final Exception e) {
      logger.debug("{}: cannot get: drools-controller {}, session {}, factType {}, because of {}",
          this, controllerName, sessionName, factType, e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new Error(e.getMessage())).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/drools/facts/{session}/{query}/{queriedEntity}")
  @ApiOperation(
      value = "Gets all the fact objects returned by a DRL query with no parameters from the drools working memory"
          + "for a given controller and session",
      notes = "The DRL query must be defined in the DRL file", responseContainer = "List")
  @ApiResponses(value = {
      @ApiResponse(code = 404,
          message = "The controller, session, or query information, cannot be found"),
      @ApiResponse(code = 406,
          message = "The system is an administrative state that prevents "
              + "this request to be fulfilled"),
      @ApiResponse(code = 500, message = "A server error has occurred processing this request")})
  public Response droolsFacts(
      @ApiParam(value = "Fact count",
          required = false) @DefaultValue("false") @QueryParam("count") boolean count,
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Drools Session Name",
          required = true) @PathParam("session") String sessionName,
      @ApiParam(value = "Query Name Present in DRL",
          required = true) @PathParam("query") String queryName,
      @ApiParam(value = "Query Identifier Present in the DRL Query",
          required = true) @PathParam("queriedEntity") String queriedEntity) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final List<Object> facts = drools.factQuery(sessionName, queryName, queriedEntity, false);
      if (!count)
        return Response.status(Response.Status.OK).entity(facts).build();
      else
        return Response.status(Response.Status.OK).entity(facts.size()).build();
    } catch (final IllegalArgumentException e) {
      logger.debug(
          "{}: cannot get: drools-controller {}, session {}, query {}, entity {} because of {}",
          this, controllerName, sessionName, queryName, queriedEntity, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(
              controllerName + ":" + sessionName + ":" + queryName + queriedEntity + " not found"))
          .build();
    } catch (final IllegalStateException e) {
      logger.debug(
          "{}: cannot get: drools-controller {}, session {}, query {}, entity {} because of {}",
          this, controllerName, sessionName, queryName, queriedEntity, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE).entity(new Error(
          controllerName + ":" + sessionName + ":" + queryName + queriedEntity + " not acceptable"))
          .build();
    } catch (final Exception e) {
      logger.debug(
          "{}: cannot get: drools-controller {}, session {}, query {}, entity {} because of {}",
          this, controllerName, sessionName, queryName, queriedEntity, e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new Error(e.getMessage())).build();
    }
  }

  @POST
  @Path("engine/controllers/{controller}/drools/facts/{session}/{query}/{queriedEntity}")
  @ApiOperation(
      value = "Gets all the fact objects returned by a DRL query with parameters from the drools working memory"
          + "for a given controller and session",
      notes = "The DRL query with parameters must be defined in the DRL file",
      responseContainer = "List")
  @ApiResponses(value = {
      @ApiResponse(code = 404,
          message = "The controller, session, or query information, cannot be found"),
      @ApiResponse(code = 406,
          message = "The system is an administrative state that prevents "
              + "this request to be fulfilled"),
      @ApiResponse(code = 500, message = "A server error has occurred processing this request")})
  public Response droolsFacts(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Drools Session Name",
          required = true) @PathParam("session") String sessionName,
      @ApiParam(value = "Query Name Present in DRL",
          required = true) @PathParam("query") String queryName,
      @ApiParam(value = "Query Identifier Present in the DRL Query",
          required = true) @PathParam("queriedEntity") String queriedEntity,
      @ApiParam(value = "Query Parameter Values to pass in the DRL Query",
          required = false) List<Object> queryParameters) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      List<Object> facts;
      if (queryParameters == null || queryParameters.isEmpty())
        facts = drools.factQuery(sessionName, queryName, queriedEntity, false);
      else
        facts = drools.factQuery(sessionName, queryName, queriedEntity, false,
            queryParameters.toArray());
      return Response.status(Response.Status.OK).entity(facts).build();
    } catch (final IllegalArgumentException e) {
      logger.debug(
          "{}: cannot get: drools-controller {}, session {}, query {}, entity {}, params {} because of {}",
          this, controllerName, sessionName, queryName, queriedEntity, queryParameters,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(
              controllerName + ":" + sessionName + ":" + queryName + queriedEntity + " not found"))
          .build();
    } catch (final IllegalStateException e) {
      logger.debug(
          "{}: cannot get: drools-controller {}, session {}, query {}, entity {}, params {} because of {}",
          this, controllerName, sessionName, queryName, queriedEntity, queryParameters,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE).entity(new Error(
          controllerName + ":" + sessionName + ":" + queryName + queriedEntity + " not acceptable"))
          .build();
    } catch (final Exception e) {
      logger.debug(
          "{}: cannot get: drools-controller {}, session {}, query {}, entity {}, params {} because of {}",
          this, controllerName, sessionName, queryName, queriedEntity, queryParameters,
          e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new Error(e.getMessage())).build();
    }
  }

  @DELETE
  @Path("engine/controllers/{controller}/drools/facts/{session}/{query}/{queriedEntity}")
  @ApiOperation(
      value = "Deletes all the fact objects returned by a DRL query with parameters from the drools working memory"
          + "for a given controller and session",
      notes = "The DRL query with parameters must be defined in the DRL file",
      responseContainer = "List")
  @ApiResponses(value = {
      @ApiResponse(code = 404,
          message = "The controller, session, or query information, cannot be found"),
      @ApiResponse(code = 406,
          message = "The system is an administrative state that prevents "
              + "this request to be fulfilled"),
      @ApiResponse(code = 500, message = "A server error has occurred processing this request")})
  public Response droolsFactsDelete(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Drools Session Name",
          required = true) @PathParam("session") String sessionName,
      @ApiParam(value = "Query Name Present in DRL",
          required = true) @PathParam("query") String queryName,
      @ApiParam(value = "Query Identifier Present in the DRL Query",
          required = true) @PathParam("queriedEntity") String queriedEntity,
      @ApiParam(value = "Query Parameter Values to pass in the DRL Query",
          required = false) List<Object> queryParameters) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      List<Object> facts;
      if (queryParameters == null || queryParameters.isEmpty())
        facts = drools.factQuery(sessionName, queryName, queriedEntity, true);
      else
        facts = drools.factQuery(sessionName, queryName, queriedEntity, true,
            queryParameters.toArray());
      return Response.status(Response.Status.OK).entity(facts).build();
    } catch (final IllegalArgumentException e) {
      logger.debug(
          "{}: cannot get: drools-controller {}, session {}, query {}, entity {}, params {} because of {}",
          this, controllerName, sessionName, queryName, queriedEntity, queryParameters,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(
              controllerName + ":" + sessionName + ":" + queryName + queriedEntity + " not found"))
          .build();
    } catch (final IllegalStateException e) {
      logger.debug(
          "{}: cannot get: drools-controller {}, session {}, query {}, entity {}, params {} because of {}",
          this, controllerName, sessionName, queryName, queriedEntity, queryParameters,
          e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE).entity(new Error(
          controllerName + ":" + sessionName + ":" + queryName + queriedEntity + " not acceptable"))
          .build();
    } catch (final Exception e) {
      logger.debug(
          "{}: cannot get: drools-controller {}, session {}, query {}, entity {}, params {} because of {}",
          this, controllerName, sessionName, queryName, queriedEntity, queryParameters,
          e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new Error(e.getMessage())).build();
    }
  }

  @POST
  @Path("engine/controllers/tools/coders/decoders/filters/rules/{ruleName}")
  @ApiOperation(
      value = "Produces a Decoder Rule Filter in a format that the Policy Controller can understand",
      notes = "The result can be used with other APIs to attach a filter to a decoder")
  public Response rules(
      @ApiParam(value = "Negate regex?",
          required = true) @DefaultValue("false") @QueryParam("negate") boolean negate,
      @ApiParam(value = "Rule Name", required = true) @PathParam("ruleName") String name,
      @ApiParam(value = "Regex expression", required = true) String regex) {
    String literalRegex = Pattern.quote(regex);
    if (negate)
      literalRegex = "^(?!" + literalRegex + "$).*";

    return Response.status(Status.OK).entity(new JsonProtocolFilter.FilterRule(name, literalRegex))
        .build();
  }

  @GET
  @Path("engine/controllers/{controller}/decoders")
  @ApiOperation(value = "Gets all the decoders used by a controller",
      notes = "A Policy Controller uses decoders to deserialize incoming network messages from "
          + "subscribed network topics into specific (fact) objects. "
          + "The deserialized (fact) object will typically be inserted in the drools working "
          + " memory of the controlled drools application.",
      responseContainer = "List", response = ProtocolCoderToolset.class)
  @ApiResponses(value = {@ApiResponse(code = 404, message = "The controller cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response decoders(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final List<ProtocolCoderToolset> decoders =
          EventProtocolCoder.manager.getDecoders(drools.getGroupId(), drools.getArtifactId());
      return Response.status(Response.Status.OK).entity(decoders).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} because of {}", this,
          controllerName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} because of {}", this,
          controllerName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " not acceptable")).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/decoders/filters")
  @ApiOperation(value = "Gets all the filters used by a controller",
      notes = "A Policy Controller uses decoders to deserialize incoming network messages from "
          + "subscribed network topics into specific (fact) objects. "
          + "The deserialized (fact) object will typically be inserted in the drools working "
          + " memory of the controlled drools application."
          + "Acceptance filters are used to filter out undesired network messages for the given controller",
      responseContainer = "List", response = CoderFilters.class)
  @ApiResponses(value = {@ApiResponse(code = 404, message = "The controller cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response decoderFilters(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final List<CoderFilters> filters =
          EventProtocolCoder.manager.getDecoderFilters(drools.getGroupId(), drools.getArtifactId());
      return Response.status(Response.Status.OK).entity(filters).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} because of {}", this,
          controllerName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} because of {}", this,
          controllerName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " not acceptable")).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/decoders/{topic}")
  @ApiOperation(value = "Gets all the decoders in use by a controller for a networked topic",
      notes = "A Policy Controller uses decoders to deserialize incoming network messages from "
          + "subscribed network topics into specific (fact) objects. "
          + "The deserialized (fact) object will typically be inserted in the drools working "
          + " memory of the controlled drools application.",
      responseContainer = "List", response = ProtocolCoderToolset.class)
  @ApiResponses(
      value = {@ApiResponse(code = 404, message = "The controller or topic cannot be found"),
          @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
              + "this request to be fulfilled")})
  public Response decoder(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Networked Topic Name", required = true) @PathParam("topic") String topic) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final ProtocolCoderToolset decoder = EventProtocolCoder.manager
          .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
      return Response.status(Response.Status.OK).entity(decoder).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} topic {} because of {}", this,
          controllerName, topic, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + ":" + topic + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} topic {} because of {}", this,
          controllerName, topic, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + ":" + topic + " not acceptable")).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/decoders/{topic}/filters")
  @ApiOperation(
      value = "Gets all filters attached to decoders for a given networked topic in use by a controller",
      notes = "A Policy Controller uses decoders to deserialize incoming network messages from "
          + "subscribed network topics into specific (fact) objects. "
          + "The deserialized (fact) object will typically be inserted in the drools working "
          + " memory of the controlled drools application."
          + "Acceptance filters are used to filter out undesired network messages for the given controller",
      responseContainer = "List", response = CoderFilters.class)
  @ApiResponses(
      value = {@ApiResponse(code = 404, message = "The controller or topic cannot be found"),
          @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
              + "this request to be fulfilled")})
  public Response decoderFilter(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Networked Topic Name", required = true) @PathParam("topic") String topic) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final ProtocolCoderToolset decoder = EventProtocolCoder.manager
          .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
      if (decoder == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(topic + "  does not exist")).build();
      else
        return Response.status(Response.Status.OK).entity(decoder.getCoders()).build();
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} topic {} because of {}", this,
          controllerName, topic, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + ":" + topic + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} topic {} because of {}", this,
          controllerName, topic, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + ":" + topic + " not acceptable")).build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}")
  @ApiOperation(
      value = "Gets all filters attached to decoders for a given subscribed networked topic "
          + "and fact type",
      notes = "Decoders are associated with networked topics. A Policy Controller manages "
          + "multiple topics and therefore its attached decoders. "
          + "A Policy Controller uses filters to further specify the fact mapping.  "
          + "Filters are applied on a per fact type (classname).",
      responseContainer = "List", response = CoderFilters.class)
  @ApiResponses(value = {
      @ApiResponse(code = 404, message = "The controller, topic, or fact type cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response decoderFilter(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Networked Topic Name", required = true) @PathParam("topic") String topic,
      @ApiParam(value = "Fact Type", required = true) @PathParam("factType") String factClass) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final ProtocolCoderToolset decoder = EventProtocolCoder.manager
          .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
      final CoderFilters filters = decoder.getCoder(factClass);
      if (filters == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(topic + ":" + factClass + "  does not exist")).build();
      else
        return Response.status(Response.Status.OK).entity(filters).build();
    } catch (final IllegalArgumentException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} because of {}",
          this, controllerName, topic, factClass, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + ":" + topic + ":" + factClass + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} because of {}",
          this, controllerName, topic, factClass, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + ":" + topic + ":" + factClass + " not acceptable"))
          .build();
    }
  }

  @PUT
  @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}")
  @ApiOperation(
      value = "Attaches filters to the decoder for a given networked topic " + "and fact type",
      notes = "Decoders are associated with networked topics. A Policy Controller manages "
          + "multiple topics and therefore its attached decoders. "
          + "A Policy Controller uses filters to further specify the fact mapping.  "
          + "Filters are applied on a per fact type (classname).",
      responseContainer = "List", response = CoderFilters.class)
  @ApiResponses(value = {
      @ApiResponse(code = 404,
          message = "The controller, topic, fact type, cannot be found, "
              + "or a filter has not been provided"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response decoderFilter(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic,
      @ApiParam(value = "Fact Type", required = true) @PathParam("factType") String factClass,
      @ApiParam(value = "Configuration Filter", required = true) JsonProtocolFilter configFilters) {

    if (configFilters == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new Error("Configuration Filters not provided")).build();
    }

    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final ProtocolCoderToolset decoder = EventProtocolCoder.manager
          .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);
      final CoderFilters filters = decoder.getCoder(factClass);
      if (filters == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(topic + ":" + factClass + "  does not exist")).build();
      filters.setFilter(configFilters);
      return Response.status(Response.Status.OK).entity(filters).build();
    } catch (final IllegalArgumentException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} filters {} because of {}",
          this, controllerName, topic, factClass, configFilters, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + ":" + topic + ":" + factClass + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} filters {} because of {}",
          this, controllerName, topic, factClass, configFilters, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + ":" + topic + ":" + factClass + " not acceptable"))
          .build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}/rules")
  @ApiOperation(value = "Gets the filter rules attached to a topic decoder of a controller",
      notes = "Decoders are associated with networked topics. A Policy Controller manages "
          + "multiple topics and therefore its attached decoders. "
          + "A Policy Controller uses filters to further specify the fact mapping.  "
          + "Filters are applied on a per fact type and are composed of field matching rules. ",
      responseContainer = "List", response = FilterRule.class)
  @ApiResponses(value = {
      @ApiResponse(code = 404, message = "The controller, topic, or fact type cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response decoderFilterRules(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic,
      @ApiParam(value = "Fact Type", required = true) @PathParam("factType") String factClass) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final ProtocolCoderToolset decoder = EventProtocolCoder.manager
          .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);

      final CoderFilters filters = decoder.getCoder(factClass);
      if (filters == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + ":" + topic + ":" + factClass + "  does not exist"))
            .build();

      final JsonProtocolFilter filter = filters.getFilter();
      if (filter == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + ":" + topic + ":" + factClass + "  no filters"))
            .build();

      return Response.status(Response.Status.OK).entity(filter.getRules()).build();
    } catch (final IllegalArgumentException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} because of {}",
          this, controllerName, topic, factClass, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + ":" + topic + ":" + factClass + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} because of {}",
          this, controllerName, topic, factClass, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + ":" + topic + ":" + factClass + " not acceptable"))
          .build();
    }
  }

  @GET
  @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}/rules/{ruleName}")
  @ApiOperation(value = "Gets a filter rule by name attached to a topic decoder of a controller",
      notes = "Decoders are associated with networked topics. A Policy Controller manages "
          + "multiple topics and therefore its attached decoders. "
          + "A Policy Controller uses filters to further specify the fact mapping.  "
          + "Filters are applied on a per fact type and are composed of field matching rules. ",
      responseContainer = "List", response = FilterRule.class)
  @ApiResponses(value = {
      @ApiResponse(code = 404,
          message = "The controller, topic, fact type, or rule name cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response decoderFilterRules(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic,
      @ApiParam(value = "Fact Type", required = true) @PathParam("factType") String factClass,
      @ApiParam(value = "Rule Name", required = true) @PathParam("ruleName") String ruleName) {
    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final ProtocolCoderToolset decoder = EventProtocolCoder.manager
          .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);

      final CoderFilters filters = decoder.getCoder(factClass);
      if (filters == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + ":" + topic + ":" + factClass + "  does not exist"))
            .build();

      final JsonProtocolFilter filter = filters.getFilter();
      if (filter == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + ":" + topic + ":" + factClass + "  no filters"))
            .build();

      return Response.status(Response.Status.OK).entity(filter.getRules(ruleName)).build();
    } catch (final IllegalArgumentException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} rule {} because of {}",
          this, controllerName, topic, factClass, ruleName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(
              controllerName + ":" + topic + ":" + factClass + ": " + ruleName + " not found"))
          .build();
    } catch (final IllegalStateException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} rule {} because of {}",
          this, controllerName, topic, factClass, ruleName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(
              controllerName + ":" + topic + ":" + factClass + ":" + ruleName + " not acceptable"))
          .build();
    }
  }

  @DELETE
  @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}/rules/{ruleName}")
  @ApiOperation(value = "Deletes a filter rule by name attached to a topic decoder of a controller",
      notes = "Decoders are associated with networked topics. A Policy Controller manages "
          + "multiple topics and therefore its attached decoders. "
          + "A Policy Controller uses filters to further specify the fact mapping.  "
          + "Filters are applied on a per fact type and are composed of field matching rules. ",
      responseContainer = "List", response = FilterRule.class)
  @ApiResponses(value = {
      @ApiResponse(code = 404,
          message = "The controller, topic, fact type, or rule name cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response decoderFilterRuleDelete(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic,
      @ApiParam(value = "Fact Type", required = true) @PathParam("factType") String factClass,
      @ApiParam(value = "Rule Name", required = true) @PathParam("ruleName") String ruleName,
      @ApiParam(value = "Filter Rule", required = true) FilterRule rule) {

    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final ProtocolCoderToolset decoder = EventProtocolCoder.manager
          .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);

      final CoderFilters filters = decoder.getCoder(factClass);
      if (filters == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + ":" + topic + ":" + factClass + "  does not exist"))
            .build();

      final JsonProtocolFilter filter = filters.getFilter();
      if (filter == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + ":" + topic + ":" + factClass + "  no filters"))
            .build();

      if (rule == null) {
        filter.deleteRules(ruleName);
        return Response.status(Response.Status.OK).entity(filter.getRules()).build();
      }

      if (rule.getName() == null || !rule.getName().equals(ruleName))
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + ":" + topic + ":" + factClass + ":" + ruleName
                + " rule name request inconsistencies (" + rule.getName() + ")"))
            .build();

      filter.deleteRule(ruleName, rule.getRegex());
      return Response.status(Response.Status.OK).entity(filter.getRules()).build();
    } catch (final IllegalArgumentException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} rule {} because of {}",
          this, controllerName, topic, factClass, ruleName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(
              controllerName + ":" + topic + ":" + factClass + ": " + ruleName + " not found"))
          .build();
    } catch (final IllegalStateException e) {
      logger.debug(
          "{}: cannot get decoder filters for policy-controller {} topic {} type {} rule {} because of {}",
          this, controllerName, topic, factClass, ruleName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(
              controllerName + ":" + topic + ":" + factClass + ":" + ruleName + " not acceptable"))
          .build();
    }
  }

  @PUT
  @Path("engine/controllers/{controller}/decoders/{topic}/filters/{factType}/rules")
  @ApiOperation(value = "Places a new filter rule in a topic decoder",
      notes = "Decoders are associated with networked topics. A Policy Controller manages "
          + "multiple topics and therefore its attached decoders. "
          + "A Policy Controller uses filters to further specify the fact mapping.  "
          + "Filters are applied on a per fact type and are composed of field matching rules. ",
      responseContainer = "List", response = FilterRule.class)
  @ApiResponses(value = {
      @ApiResponse(code = 404, message = "The controller, topic, or fact type cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response decoderFilterRule(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic,
      @ApiParam(value = "Fact Type", required = true) @PathParam("factType") String factClass,
      @ApiParam(value = "Rule Name", required = true) @PathParam("ruleName") String ruleName,
      @ApiParam(value = "Filter Rule", required = true) FilterRule rule) {

    try {
      final DroolsController drools = this.getDroolsController(controllerName);
      final ProtocolCoderToolset decoder = EventProtocolCoder.manager
          .getDecoders(drools.getGroupId(), drools.getArtifactId(), topic);

      final CoderFilters filters = decoder.getCoder(factClass);
      if (filters == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + ":" + topic + ":" + factClass + "  does not exist"))
            .build();

      final JsonProtocolFilter filter = filters.getFilter();
      if (filter == null)
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new Error(controllerName + ":" + topic + ":" + factClass + "  no filters"))
            .build();

      if (rule.getName() == null)
        return Response
            .status(Response.Status.BAD_REQUEST).entity(new Error(controllerName + ":" + topic + ":"
                + factClass + " rule name request inconsistencies (" + rule.getName() + ")"))
            .build();

      filter.addRule(rule.getName(), rule.getRegex());
      return Response.status(Response.Status.OK).entity(filter.getRules()).build();
    } catch (final IllegalArgumentException e) {
      logger.debug(
          "{}: cannot access decoder filter rules for policy-controller {} topic {} type {} rule {} because of {}",
          this, controllerName, topic, factClass, ruleName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + ":" + topic + ":" + factClass + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug(
          "{}: cannot access decoder filter rules for policy-controller {} topic {} type {} rule {} because of {}",
          this, controllerName, topic, factClass, ruleName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + ":" + topic + ":" + factClass + " not acceptable"))
          .build();
    }
  }

  @POST
  @Path("engine/controllers/{controller}/decoders/{topic}")
  @Consumes(MediaType.TEXT_PLAIN)
  @ApiOperation(value = "Decodes a string into a fact object, and encodes it back into a string",
      notes = "Tests the decode/encode functions of a controller", response = CodingResult.class)
  @ApiResponses(value = {@ApiResponse(code = 400, message = "Bad input has been provided"),
      @ApiResponse(code = 404, message = "The controller cannot be found"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response decode(
      @ApiParam(value = "Policy Controller Name",
          required = true) @PathParam("controller") String controllerName,
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic,
      @ApiParam(value = "JSON String to decode", required = true) String json) {

    PolicyController policyController;
    try {
      policyController = PolicyController.factory.get(controllerName);
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} topic {} because of {}", this,
          controllerName, topic, e.getMessage(), e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error(controllerName + ":" + topic + ":" + " not found")).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get decoders for policy-controller {} topic {} because of {}", this,
          controllerName, topic, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + ":" + topic + ":" + " not acceptable")).build();
    }

    final CodingResult result = new CodingResult();
    result.setDecoding(false);
    result.setEncoding(false);
    result.setJsonEncoding(null);

    Object event;
    try {
      event = EventProtocolCoder.manager.decode(policyController.getDrools().getGroupId(),
          policyController.getDrools().getArtifactId(), topic, json);
      result.setDecoding(true);
    } catch (final Exception e) {
      logger.debug("{}: cannot get policy-controller {} topic {} because of {}", this,
          controllerName, topic, e.getMessage(), e);
      return Response.status(Response.Status.BAD_REQUEST).entity(new Error(e.getMessage())).build();
    }

    try {
      result.setJsonEncoding(EventProtocolCoder.manager.encode(topic, event));
      result.setEncoding(true);
    } catch (final Exception e) {
      // continue so to propagate decoding results ..
      logger.debug("{}: cannot encode for policy-controller {} topic {} because of {}", this,
          controllerName, topic, e.getMessage(), e);
    }

    return Response.status(Response.Status.OK).entity(result).build();
  }

  @GET
  @Path("engine/controllers/{controller}/encoders")
  @ApiOperation(value = "Retrieves the encoder filters of a controller",
      notes = "The encoders serializes a fact object, typically for network transmission",
      responseContainer = "List", response = CoderFilters.class)
  @ApiResponses(value = {@ApiResponse(code = 400, message = "Bad input has been provided"),
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response encoderFilters(@ApiParam(value = "Policy Controller Name",
      required = true) @PathParam("controller") String controllerName) {
    List<CoderFilters> encoders;
    try {
      final PolicyController controller = PolicyController.factory.get(controllerName);
      final DroolsController drools = controller.getDrools();
      encoders =
          EventProtocolCoder.manager.getEncoderFilters(drools.getGroupId(), drools.getArtifactId());
    } catch (final IllegalArgumentException e) {
      logger.debug("{}: cannot get encoder filters for policy-controller {} because of {}", this,
          controllerName, e.getMessage(), e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new Error(controllerName + " not found: " + e.getMessage())).build();
    } catch (final IllegalStateException e) {
      logger.debug("{}: cannot get encoder filters for policy-controller {} because of {}", this,
          controllerName, e.getMessage(), e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(controllerName + " is not accepting the request")).build();
    }

    return Response.status(Response.Status.OK).entity(encoders).build();
  }

  @GET
  @Path("engine/topics")
  @ApiOperation(value = "Retrieves the managed topics", notes = "Network Topics Aggregation",
      response = TopicEndpoint.class)
  public Response topics() {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager).build();
  }

  @GET
  @Path("engine/topics/switches")
  @ApiOperation(value = "Topics Control Switches", notes = "List of the Topic Control Switches",
      responseContainer = "List")
  public Response topicSwitches() {
    return Response.status(Response.Status.OK).entity(Arrays.asList(Switches.values())).build();
  }

  @PUT
  @Path("engine/topics/switches/lock")
  @ApiOperation(value = "Locks all the managed topics",
      notes = "The operation affects all managed sources and sinks", response = TopicEndpoint.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response topicsLock() {
    final boolean success = TopicEndpoint.manager.lock();
    if (success)
      return Response.status(Status.OK).entity(TopicEndpoint.manager).build();
    else
      return Response.status(Status.NOT_ACCEPTABLE).entity(new Error("cannot perform operation"))
          .build();
  }

  @DELETE
  @Path("engine/topics/switches/lock")
  @ApiOperation(value = "Unlocks all the managed topics",
      notes = "The operation affects all managed sources and sinks", response = TopicEndpoint.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response topicsUnlock() {
    final boolean success = TopicEndpoint.manager.unlock();
    if (success)
      return Response.status(Status.OK).entity(TopicEndpoint.manager).build();
    else
      return Response.status(Status.NOT_ACCEPTABLE).entity(new Error("cannot perform operation"))
          .build();
  }

  @GET
  @Path("engine/topics/sources")
  @ApiOperation(value = "Retrieves the managed topic sources",
      notes = "Network Topic Sources Agregation", responseContainer = "List",
      response = TopicSource.class)
  public Response sources() {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager.getTopicSources())
        .build();
  }

  @GET
  @Path("engine/topics/sinks")
  @ApiOperation(value = "Retrieves the managed topic sinks",
      notes = "Network Topic Sinks Agregation", responseContainer = "List",
      response = TopicSink.class)
  public Response sinks() {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager.getTopicSinks())
        .build();
  }

  @GET
  @Path("engine/topics/sources/ueb")
  @ApiOperation(value = "Retrieves the UEB managed topic sources",
      notes = "UEB Topic Sources Agregation", responseContainer = "List",
      response = UebTopicSource.class)
  public Response uebSources() {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager.getUebTopicSources())
        .build();
  }

  @GET
  @Path("engine/topics/sinks/ueb")
  @ApiOperation(value = "Retrieves the UEB managed topic sinks",
      notes = "UEB Topic Sinks Agregation", responseContainer = "List",
      response = UebTopicSink.class)
  public Response uebSinks() {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager.getUebTopicSinks())
        .build();
  }

  @GET
  @Path("engine/topics/sources/dmaap")
  @ApiOperation(value = "Retrieves the DMaaP managed topic sources",
      notes = "DMaaP Topic Sources Agregation", responseContainer = "List",
      response = DmaapTopicSource.class)
  public Response dmaapSources() {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager.getDmaapTopicSources())
        .build();
  }

  @GET
  @Path("engine/topics/sinks/dmaap")
  @ApiOperation(value = "Retrieves the DMaaP managed topic sinks",
      notes = "DMaaP Topic Sinks Agregation", responseContainer = "List",
      response = DmaapTopicSink.class)
  public Response dmaapSinks() {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager.getDmaapTopicSinks())
        .build();
  }

  @GET
  @Path("engine/topics/sources/ueb/{topic}")
  @ApiOperation(value = "Retrieves an UEB managed topic source",
      notes = "This is an UEB Network Communicaton Endpoint source of messages for the Engine",
      response = UebTopicSource.class)
  public Response uebSourceTopic(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    return Response.status(Response.Status.OK)
        .entity(TopicEndpoint.manager.getUebTopicSource(topic)).build();
  }

  @GET
  @Path("engine/topics/sinks/ueb/{topic}")
  @ApiOperation(value = "Retrieves an UEB managed topic sink",
      notes = "This is an UEB Network Communicaton Endpoint destination of messages from the Engine",
      response = UebTopicSink.class)
  public Response uebSinkTopic(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager.getUebTopicSink(topic))
        .build();
  }

  @GET
  @Path("engine/topics/sources/dmaap/{topic}")
  @ApiOperation(value = "Retrieves a DMaaP managed topic source",
      notes = "This is a DMaaP Network Communicaton Endpoint source of messages for the Engine",
      response = DmaapTopicSource.class)
  public Response dmaapSourceTopic(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    return Response.status(Response.Status.OK)
        .entity(TopicEndpoint.manager.getDmaapTopicSource(topic)).build();
  }

  @GET
  @Path("engine/topics/sinks/dmaap/{topic}")
  @ApiOperation(value = "Retrieves a DMaaP managed topic sink",
      notes = "This is a DMaaP Network Communicaton Endpoint destination of messages from the Engine",
      response = DmaapTopicSink.class)
  public Response dmaapSinkTopic(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    return Response.status(Response.Status.OK)
        .entity(TopicEndpoint.manager.getDmaapTopicSink(topic)).build();
  }

  @GET
  @Path("engine/topics/sources/ueb/{topic}/events")
  @ApiOperation(value = "Retrieves the latest events received by an UEB topic",
      notes = "This is a UEB Network Communicaton Endpoint source of messages for the Engine",
      responseContainer = "List")
  public Response uebSourceEvents(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    return Response.status(Status.OK)
        .entity(Arrays.asList(TopicEndpoint.manager.getUebTopicSource(topic).getRecentEvents()))
        .build();
  }

  @GET
  @Path("engine/topics/sinks/ueb/{topic}/events")
  @ApiOperation(value = "Retrieves the latest events sent from a topic",
      notes = "This is a UEB Network Communicaton Endpoint sink of messages from the Engine",
      responseContainer = "List")
  public Response uebSinkEvents(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    return Response.status(Status.OK)
        .entity(Arrays.asList(TopicEndpoint.manager.getUebTopicSink(topic).getRecentEvents()))
        .build();
  }

  @GET
  @Path("engine/topics/sources/dmaap/{topic}/events")
  @ApiOperation(value = "Retrieves the latest events received by a DMaaP topic",
      notes = "This is a DMaaP Network Communicaton Endpoint source of messages for the Engine",
      responseContainer = "List")
  public Response dmaapSourceEvents(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    return Response.status(Status.OK)
        .entity(Arrays.asList(TopicEndpoint.manager.getDmaapTopicSource(topic).getRecentEvents()))
        .build();
  }

  @GET
  @Path("engine/topics/sinks/dmaap/{topic}/events")
  @ApiOperation(value = "Retrieves the latest events send through a DMaaP topic",
      notes = "This is a DMaaP Network Communicaton Endpoint destination of messages from the Engine",
      responseContainer = "List")
  public Response dmaapSinkEvents(@PathParam("topic") String topic) {
    return Response.status(Status.OK)
        .entity(Arrays.asList(TopicEndpoint.manager.getDmaapTopicSink(topic).getRecentEvents()))
        .build();
  }

  @GET
  @Path("engine/topics/sinks/noop")
  @ApiOperation(value = "Retrieves the NOOP managed topic sinks",
      notes = "NOOP Topic Sinks Agregation", responseContainer = "List",
      response = NoopTopicSink.class)
  public Response noopSinks() {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager.getNoopTopicSinks())
        .build();
  }

  @GET
  @Path("engine/topics/sinks/noop/{topic}")
  @ApiOperation(value = "Retrieves a NOOP managed topic sink",
      notes = "NOOP is an dev/null Network Communicaton Sink", response = NoopTopicSink.class)
  public Response noopSinkTopic(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    return Response.status(Response.Status.OK).entity(TopicEndpoint.manager.getNoopTopicSink(topic))
        .build();
  }

  @GET
  @Path("engine/topics/sinks/noop/{topic}/events")
  @ApiOperation(value = "Retrieves the latest events send through a NOOP topic",
      notes = "NOOP is an dev/null Network Communicaton Sink", responseContainer = "List")
  public Response noopSinkEvents(@PathParam("topic") String topic) {
    return Response.status(Status.OK)
        .entity(Arrays.asList(TopicEndpoint.manager.getNoopTopicSink(topic).getRecentEvents()))
        .build();
  }

  @GET
  @Path("engine/topics/sources/ueb/{topic}/switches")
  @ApiOperation(value = "UEB Topic Control Switches",
      notes = "List of the UEB Topic Control Switches", responseContainer = "List")
  public Response uebTopicSwitches() {
    return Response.status(Response.Status.OK).entity(Arrays.asList(Switches.values())).build();
  }

  @PUT
  @Path("engine/topics/sources/ueb/{topic}/switches/lock")
  @ApiOperation(value = "Locks an UEB Source topic", response = UebTopicSource.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response uebTopicLock(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    final UebTopicSource source = TopicEndpoint.manager.getUebTopicSource(topic);
    final boolean success = source.lock();
    if (success)
      return Response.status(Status.OK).entity(source).build();
    else
      return Response.status(Status.NOT_ACCEPTABLE)
          .entity(makeTopicOperError(topic)).build();
  }

  @DELETE
  @Path("engine/topics/sources/ueb/{topic}/switches/lock")
  @ApiOperation(value = "Unlocks an UEB Source topic", response = UebTopicSource.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response uebTopicUnlock(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    final UebTopicSource source = TopicEndpoint.manager.getUebTopicSource(topic);
    final boolean success = source.unlock();
    if (success)
      return Response.status(Status.OK).entity(source).build();
    else
      return Response.status(Status.NOT_ACCEPTABLE)
          .entity(makeTopicOperError(topic)).build();
  }

  private Error makeTopicOperError(String topic) {
	return new Error("cannot perform operation on " + topic);
  }

  @GET
  @Path("engine/topics/sources/dmaap/{topic}/switches")
  @ApiOperation(value = "DMaaP Topic Control Switches",
      notes = "List of the DMaaP Topic Control Switches", responseContainer = "List")
  public Response dmaapTopicSwitches() {
    return Response.status(Response.Status.OK).entity(Arrays.asList(Switches.values())).build();
  }

  @PUT
  @Path("engine/topics/sources/dmaap/{topic}/switches/lock")
  @ApiOperation(value = "Locks an DMaaP Source topic", response = DmaapTopicSource.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response dmmapTopicLock(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    final DmaapTopicSource source = TopicEndpoint.manager.getDmaapTopicSource(topic);
    final boolean success = source.lock();
    if (success)
      return Response.status(Status.OK).entity(source).build();
    else
      return Response.status(Status.NOT_ACCEPTABLE)
          .entity(makeTopicOperError(topic)).build();
  }

  @DELETE
  @Path("engine/topics/sources/dmaap/{topic}/switches/lock")
  @ApiOperation(value = "Unlocks an DMaaP Source topic", response = DmaapTopicSource.class)
  @ApiResponses(value = {
      @ApiResponse(code = 406, message = "The system is an administrative state that prevents "
          + "this request to be fulfilled")})
  public Response dmaapTopicUnlock(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic) {
    final DmaapTopicSource source = TopicEndpoint.manager.getDmaapTopicSource(topic);
    final boolean success = source.unlock();
    if (success)
      return Response.status(Status.OK).entity(source).build();
    else
      return Response.status(Status.SERVICE_UNAVAILABLE)
          .entity(makeTopicOperError(topic)).build();
  }

  @PUT
  @Path("engine/topics/sources/ueb/{topic}/events")
  @Consumes(MediaType.TEXT_PLAIN)
  @ApiOperation(value = "Offers an event to an UEB topic for internal processing by the engine",
      notes = "The offered event is treated as it was incoming from the network",
      responseContainer = "List")
  @ApiResponses(value = {
      @ApiResponse(code = 404, message = "The topic information cannot be found"),
      @ApiResponse(code = 406,
          message = "The system is an administrative state that prevents "
              + "this request to be fulfilled"),
      @ApiResponse(code = 500, message = "A server error has occurred processing this request")})
  public Response uebOffer(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic,
      @ApiParam(value = "Network Message", required = true) String json) {
    try {
      final UebTopicSource uebReader = TopicEndpoint.manager.getUebTopicSource(topic);
      final boolean success = uebReader.offer(json);
      if (success)
        return Response.status(Status.OK)
            .entity(Arrays.asList(TopicEndpoint.manager.getUebTopicSource(topic).getRecentEvents()))
            .build();
      else
        return Response.status(Status.NOT_ACCEPTABLE)
            .entity(new Error("Failure to inject event over " + topic)).build();
    } catch (final IllegalArgumentException e) {
      logNoUebEncoder(topic, e);
      return Response.status(Response.Status.NOT_FOUND).entity(new Error(topic + " not found"))
          .build();
    } catch (final IllegalStateException e) {
      logNoUebEncoder(topic, e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(topic + " not acceptable due to current state")).build();
    } catch (final Exception e) {
      logNoUebEncoder(topic, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new Error(e.getMessage())).build();
    }
  }

  private void logNoUebEncoder(String topic, Exception ex) {
	logger.debug("{}: cannot offer for encoder ueb topic for {} because of {}", this, topic,
          ex.getMessage(), ex);
  }

  @PUT
  @Path("engine/topics/sources/dmaap/{topic}/events")
  @Consumes(MediaType.TEXT_PLAIN)
  @ApiOperation(value = "Offers an event to a DMaaP topic for internal processing by the engine",
      notes = "The offered event is treated as it was incoming from the network",
      responseContainer = "List")
  @ApiResponses(value = {
      @ApiResponse(code = 404, message = "The topic information cannot be found"),
      @ApiResponse(code = 406,
          message = "The system is an administrative state that prevents "
              + "this request to be fulfilled"),
      @ApiResponse(code = 500, message = "A server error has occurred processing this request")})
  public Response dmaapOffer(
      @ApiParam(value = "Topic Name", required = true) @PathParam("topic") String topic,
      @ApiParam(value = "Network Message", required = true) String json) {
    try {
      final DmaapTopicSource dmaapReader = TopicEndpoint.manager.getDmaapTopicSource(topic);
      final boolean success = dmaapReader.offer(json);
      if (success)
        return Response.status(Status.OK)
            .entity(
                Arrays.asList(TopicEndpoint.manager.getDmaapTopicSource(topic).getRecentEvents()))
            .build();
      else
        return Response.status(Status.NOT_ACCEPTABLE)
            .entity(new Error("Failure to inject event over " + topic)).build();
    } catch (final IllegalArgumentException e) {
      logNoDmaapEncoder(topic, e);
      return Response.status(Response.Status.NOT_FOUND).entity(new Error(topic + " not found"))
          .build();
    } catch (final IllegalStateException e) {
      logNoDmaapEncoder(topic, e);
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity(new Error(topic + " not acceptable due to current state")).build();
    } catch (final Exception e) {
      logNoDmaapEncoder(topic, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new Error(e.getMessage())).build();
    }
  }

  private void logNoDmaapEncoder(String topic, Exception ex) {
	logger.debug("{}: cannot offer for encoder dmaap topic for {} because of {}", this, topic,
          ex.getMessage(), ex);
  }

  @GET
  @Path("engine/tools/uuid")
  @ApiOperation(value = "Produces an UUID", notes = "UUID generation utility")
  @Produces(MediaType.TEXT_PLAIN)
  public Response uuid() {
    return Response.status(Status.OK).entity(UUID.randomUUID().toString()).build();
  }

  @GET
  @Path("engine/tools/loggers")
  @ApiOperation(value = "all active loggers", responseContainer = "List")
  @ApiResponses(value = {@ApiResponse(code = 500, message = "logging misconfiguration")})
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

  @GET
  @Path("engine/tools/loggers/{logger}")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiOperation(value = "logging level of a logger")
  @ApiResponses(value = {@ApiResponse(code = 500, message = "logging misconfiguration"),
      @ApiResponse(code = 404, message = "logger not found")})
  public Response loggerName(
      @ApiParam(value = "Logger Name", required = true) @PathParam("logger") String loggerName) {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
      logger.warn("The SLF4J logger factory is not configured for logback");
      return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    final ch.qos.logback.classic.Logger lgr = context.getLogger(loggerName);
    if (lgr == null) {
      return Response.status(Status.NOT_FOUND).build();
    }

    final String loggerLevel = (lgr.getLevel() != null) ? lgr.getLevel().toString() : "";
    return Response.status(Status.OK).entity(loggerLevel).build();
  }

  @PUT
  @Path("engine/tools/loggers/{logger}/{level}")
  @Produces(MediaType.TEXT_PLAIN)
  @Consumes(MediaType.TEXT_PLAIN)
  @ApiOperation(value = "sets the logger level", notes = "Please use the SLF4J logger levels")
  @ApiResponses(value = {@ApiResponse(code = 500, message = "logging misconfiguration"),
      @ApiResponse(code = 404, message = "logger not found")})
  public Response loggerName(
      @ApiParam(value = "Logger Name", required = true) @PathParam("logger") String loggerName,
      @ApiParam(value = "Logger Level", required = true) @PathParam("level") String loggerLevel) {

    String newLevel;
    try {
      newLevel = LoggerUtil.setLevel(loggerName, loggerLevel);
    } catch (final IllegalArgumentException e) {
      logger.warn("{}: no logger {}", this, loggerName, loggerLevel, e);
      return Response.status(Status.NOT_FOUND).build();
    } catch (final IllegalStateException e) {
      logger.warn("{}: logging framework unavailable for {} / {}", this, loggerName, loggerLevel,
          e);
      return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    return Response.status(Status.OK).entity(newLevel

    ).build();
  }

  /**
   * gets the underlying drools controller from the named policy controller
   *
   * @param controllerName the policy controller name
   * @return the underlying drools controller
   * @throws IllegalArgumentException if an invalid controller name has been passed in
   */
  protected DroolsController getDroolsController(String controllerName) {
    final PolicyController controller = PolicyController.factory.get(controllerName);
    if (controller == null)
      throw new IllegalArgumentException(controllerName + "  does not exist");

    final DroolsController drools = controller.getDrools();
    if (drools == null)
      throw new IllegalArgumentException(controllerName + "  has no drools configuration");

    return drools;
  }

  /*
   * Helper classes for aggregation of results
   */

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("rest-telemetry-api []");
    return builder.toString();
  }

  /**
   * Coding/Encoding Results Aggregation Helper class
   */
  public static class CodingResult {
    /**
     * serialized output
     */

    private String jsonEncoding;
    /**
     * encoding result
     */

    private Boolean encoding;

    /**
     * decoding result
     */
    private Boolean decoding;

    public String getJsonEncoding() {
        return jsonEncoding;
    }

    public void setJsonEncoding(String jsonEncoding) {
        this.jsonEncoding = jsonEncoding;
    }

    public Boolean getEncoding() {
        return encoding;
    }

    public void setEncoding(Boolean encoding) {
        this.encoding = encoding;
    }

    public Boolean getDecoding() {
        return decoding;
    }

    public void setDecoding(Boolean decoding) {
        this.decoding = decoding;
    }
  }

  /**
   * Generic Error Reporting class
   */
  public static class Error {
    private String msg;

    public Error(String msg) {
      this.setError(msg);
    }

    public String getError() {
        return msg;
    }

    public void setError(String msg) {
        this.msg = msg;
    }
  }

  /**
   * Feed Ports into Resources
   */
  public enum Inputs {
    configuration,
  }

  /**
   * Resource Toggles
   */
  public enum Switches {
    activation, lock,
  }
}

