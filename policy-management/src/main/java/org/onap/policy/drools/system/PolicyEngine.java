/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.jmx.PdpJmxListener;
import org.onap.policy.drools.event.comm.Topic;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.event.comm.TopicEndpoint;
import org.onap.policy.drools.event.comm.TopicListener;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.event.comm.TopicSource;
import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.http.server.HttpServletServer;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.Lockable;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.properties.Startable;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.server.restful.RestManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Policy Engine, the top abstraction for the Drools PDP Policy Engine. It abstracts away a Drools
 * PDP Engine from management purposes. This is the best place to looking at the code from a top
 * down approach. Other managed entities can be obtained from the PolicyEngine, hierarchically. <br>
 * PolicyEngine 1 --- * PolicyController 1 --- 1 DroolsController 1 --- 1 PolicyContainer 1 --- *
 * PolicySession <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1 UebTopicReader <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1 DmaapTopicReader <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1 DmaapTopicWriter <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1 RestTopicReader <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1 RestTopicWriter <br>
 * PolicyEngine 1 --- 1 ManagementServer
 */
public interface PolicyEngine extends Startable, Lockable, TopicListener {
  /**
   * Default Telemetry Server Port
   */
  public static final int TELEMETRY_SERVER_DEFAULT_PORT = 9696;

  /**
   * Default Telemetry Server Hostname
   */
  public static final String TELEMETRY_SERVER_DEFAULT_HOST = "localhost";

  /**
   * Default Telemetry Server Name
   */
  public static final String TELEMETRY_SERVER_DEFAULT_NAME = "TELEMETRY";

  /**
   * Boot the engine
   *
   * @param cliArgs command line arguments
   */
  public void boot(String cliArgs[]);

  /**
   * configure the policy engine according to the given properties
   *
   * @param properties Policy Engine properties
   * @throws IllegalArgumentException when invalid or insufficient properties are provided
   */
  public void configure(Properties properties);

  /**
   * configure the engine's environment. General lab installation configuration is made available to
   * the Engine. Typically, custom lab installation that may be needed by arbitrary drools
   * applications are made available, for example network component and database host addresses.
   * Multiple environments can be passed in and tracked by the engine.
   *
   * @param properties an environment properties
   */
  public void setEnvironment(Properties properties);

  /**
   * gets the engine's environment
   *
   * @return
   */
  public Properties getEnvironment();

  /**
   * gets an environment's value, by 1) first from the engine's environment, and 2) from the OS
   * environment
   *
   * @param key environment key
   * @return environment value or null if absent
   */
  public String getEnvironmentProperty(String key);

  /**
   * sets an engine's environment property
   *
   * @param key
   * @param value
   * @return
   */
  public String setEnvironmentProperty(String key, String value);

  /**
   * registers a new Policy Controller with the Policy Engine initialized per properties.
   *
   * @param controller name
   * @param properties properties to initialize the Policy Controller
   * @throws IllegalArgumentException when invalid or insufficient properties are provided
   * @throws IllegalStateException when the engine is in a state where this operation is not
   *         permitted.
   * @return the newly instantiated Policy Controller
   */
  public PolicyController createPolicyController(String name, Properties properties);

  /**
   * updates the Policy Engine with the given configuration
   *
   * @param configuration the configuration
   * @return success or failure
   * @throws IllegalArgumentException if invalid argument provided
   * @throws IllegalStateException if the system is in an invalid state
   */
  public boolean configure(PdpdConfiguration configuration);

  /**
   * updates a set of Policy Controllers with configuration information
   *
   * @param configuration
   * @return
   * @throws IllegalArgumentException
   * @throws IllegalStateException
   */
  public List<PolicyController> updatePolicyControllers(
      List<ControllerConfiguration> configuration);

  /**
   * updates an already existing Policy Controller with configuration information
   *
   * @param configuration configuration
   *
   * @return the updated Policy Controller
   * @throws IllegalArgumentException in the configuration is invalid
   * @throws IllegalStateException if the controller is in a bad state
   * @throws Exception any other reason
   */
  public PolicyController updatePolicyController(ControllerConfiguration configuration);

  /**
   * removes the Policy Controller identified by its name from the Policy Engine
   *
   * @param name name of the Policy Controller
   * @return the removed Policy Controller
   */
  public void removePolicyController(String name);

  /**
   * removes a Policy Controller from the Policy Engine
   *
   * @param controller the Policy Controller to remove from the Policy Engine
   */
  public void removePolicyController(PolicyController controller);

  /**
   * returns a list of the available Policy Controllers
   *
   * @return list of Policy Controllers
   */
  public List<PolicyController> getPolicyControllers();


  /**
   * get policy controller names
   *
   * @return list of controller names
   */
  public List<String> getPolicyControllerIds();

  /**
   * get unmanaged sources
   *
   * @return unmanaged sources
   */
  public List<TopicSource> getSources();

  /**
   * get unmanaged sinks
   *
   * @return unmanaged sinks
   */
  public List<TopicSink> getSinks();

  /**
   * get unmmanaged http servers list
   *
   * @return http servers
   */
  public List<HttpServletServer> getHttpServers();

  /**
   * get properties configuration
   *
   * @return properties objects
   */
  public Properties getProperties();

  /**
   * get features attached to the Policy Engine
   *
   * @return list of features
   */
  public List<PolicyEngineFeatureAPI> getFeatureProviders();

  /**
   * get named feature attached to the Policy Engine
   *
   * @return the feature
   */
  public PolicyEngineFeatureAPI getFeatureProvider(String featureName)
      throws IllegalArgumentException;

  /**
   * get features attached to the Policy Engine
   *
   * @return list of features
   */
  public List<String> getFeatures();

  /**
   * Attempts the dispatching of an "event" object
   *
   * @param topic topic
   * @param event the event object to send
   *
   * @return true if successful, false if a failure has occurred.
   * @throws IllegalArgumentException when invalid or insufficient properties are provided
   * @throws IllegalStateException when the engine is in a state where this operation is not
   *         permitted (ie. locked or stopped).
   */
  public boolean deliver(String topic, Object event)
      throws IllegalArgumentException, IllegalStateException;

  /**
   * Attempts the dispatching of an "event" object over communication infrastructure "busType"
   *
   * @param eventBus Communication infrastructure identifier
   * @param topic topic
   * @param event the event object to send
   *
   * @return true if successful, false if a failure has occurred.
   * @throws IllegalArgumentException when invalid or insufficient properties are provided
   * @throws IllegalStateException when the engine is in a state where this operation is not
   *         permitted (ie. locked or stopped).
   * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
   *         missing (ie. communication infrastructure not supported.
   */
  public boolean deliver(String busType, String topic, Object event)
      throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException;

  /**
   * Attempts the dispatching of an "event" object over communication infrastructure "busType"
   *
   * @param eventBus Communication infrastructure enum
   * @param topic topic
   * @param event the event object to send
   *
   * @return true if successful, false if a failure has occurred.
   * @throws IllegalArgumentException when invalid or insufficient properties are provided
   * @throws IllegalStateException when the engine is in a state where this operation is not
   *         permitted (ie. locked or stopped).
   * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
   *         missing (ie. communication infrastructure not supported.
   */
  public boolean deliver(CommInfrastructure busType, String topic, Object event)
      throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException;

  /**
   * Attempts delivering of an String over communication infrastructure "busType"
   *
   * @param eventBus Communication infrastructure identifier
   * @param topic topic
   * @param event the event object to send
   *
   * @return true if successful, false if a failure has occurred.
   * @throws IllegalArgumentException when invalid or insufficient properties are provided
   * @throws IllegalStateException when the engine is in a state where this operation is not
   *         permitted (ie. locked or stopped).
   * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
   *         missing (ie. communication infrastructure not supported.
   */
  public boolean deliver(CommInfrastructure busType, String topic, String event)
      throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException;

  /**
   * Invoked when the host goes into the active state.
   */
  public void activate();

  /**
   * Invoked when the host goes into the standby state.
   */
  public void deactivate();

  /**
   * produces a default telemetry configuration
   *
   * @return policy engine configuration
   */
  public Properties defaultTelemetryConfig();

  /**
   * Policy Engine Manager
   */
  public final static PolicyEngine manager = new PolicyEngineManager();
}


/**
 * Policy Engine Manager Implementation
 */
class PolicyEngineManager implements PolicyEngine {
  /**
   * logger
   */
  private static final Logger logger = LoggerFactory.getLogger(PolicyEngineManager.class);

  /**
   * Is the Policy Engine running?
   */
  protected boolean alive = false;

  /**
   * Is the engine locked?
   */
  protected boolean locked = false;

  /**
   * Properties used to initialize the engine
   */
  protected Properties properties;

  /**
   * Environment Properties
   */
  protected final Properties environment = new Properties();

  /**
   * Policy Engine Sources
   */
  protected List<? extends TopicSource> sources = new ArrayList<>();

  /**
   * Policy Engine Sinks
   */
  protected List<? extends TopicSink> sinks = new ArrayList<>();

  /**
   * Policy Engine HTTP Servers
   */
  protected List<HttpServletServer> httpServers = new ArrayList<HttpServletServer>();

  /**
   * gson parser to decode configuration requests
   */
  protected final Gson decoder = new GsonBuilder().disableHtmlEscaping().create();


  @Override
  public synchronized void boot(String cliArgs[]) {

    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.beforeBoot(this, cliArgs))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-boot failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    try {
      PolicyContainer.globalInit(cliArgs);
    } catch (final Exception e) {
      logger.error("{}: cannot init policy-container because of {}", this, e.getMessage(), e);
    }

    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.afterBoot(this))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-boot failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }
  }

  @Override
  public synchronized void setEnvironment(Properties properties) {
    this.environment.putAll(properties);
  }

  @JsonIgnore
  @Override
  public synchronized Properties getEnvironment() {
    return this.environment;
  }

  @Override
  public synchronized String getEnvironmentProperty(String envKey) {
    String value = this.environment.getProperty(envKey);
    if (value == null)
      value = System.getenv(envKey);
    return value;
  }

  @Override
  public synchronized String setEnvironmentProperty(String envKey, String envValue) {
    return (String) this.environment.setProperty(envKey, envValue);
  }

  @Override
  public final Properties defaultTelemetryConfig() {
    final Properties defaultConfig = new Properties();

    defaultConfig.put(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES, "TELEMETRY");
    defaultConfig.put(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "."
        + TELEMETRY_SERVER_DEFAULT_NAME + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX,
        TELEMETRY_SERVER_DEFAULT_HOST);
    defaultConfig.put(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "."
        + TELEMETRY_SERVER_DEFAULT_NAME + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX,
        "" + TELEMETRY_SERVER_DEFAULT_PORT);
    defaultConfig.put(
        PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
            + PolicyProperties.PROPERTY_HTTP_REST_PACKAGES_SUFFIX,
        RestManager.class.getPackage().getName());
    defaultConfig.put(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "."
        + TELEMETRY_SERVER_DEFAULT_NAME + PolicyProperties.PROPERTY_HTTP_SWAGGER_SUFFIX,
        "" + Boolean.TRUE);
    defaultConfig.put(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "."
        + TELEMETRY_SERVER_DEFAULT_NAME + PolicyProperties.PROPERTY_MANAGED_SUFFIX,
        "" + Boolean.FALSE);

    return defaultConfig;
  }

  @Override
  public synchronized void configure(Properties properties) {

    if (properties == null) {
      logger.warn("No properties provided");
      throw new IllegalArgumentException("No properties provided");
    }

    /* policy-engine dispatch pre configure hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.beforeConfigure(this, properties))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-configure failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    this.properties = properties;

    try {
      this.sources = TopicEndpoint.manager.addTopicSources(properties);
      for (final TopicSource source : this.sources) {
        source.register(this);
      }
    } catch (final Exception e) {
      logger.error("{}: add-sources failed", this, e);
    }

    try {
      this.sinks = TopicEndpoint.manager.addTopicSinks(properties);
    } catch (final IllegalArgumentException e) {
      logger.error("{}: add-sinks failed", this, e);
    }

    try {
      this.httpServers = HttpServletServer.factory.build(properties);
    } catch (final IllegalArgumentException e) {
      logger.error("{}: add-http-servers failed", this, e);
    }

    /* policy-engine dispatch post configure hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.afterConfigure(this))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-configure failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    return;
  }

  @Override
  public synchronized PolicyController createPolicyController(String name, Properties properties)
      throws IllegalArgumentException, IllegalStateException {

    // check if a PROPERTY_CONTROLLER_NAME property is present
    // if so, override the given name

    final String propertyControllerName =
        properties.getProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME);
    if (propertyControllerName != null && !propertyControllerName.isEmpty()) {
      if (!propertyControllerName.equals(name)) {
        throw new IllegalStateException("Proposed name (" + name + ") and properties name ("
            + propertyControllerName + ") don't match");
      }
      name = propertyControllerName;
    }

    PolicyController controller;
    for (final PolicyControllerFeatureAPI controllerFeature : PolicyControllerFeatureAPI.providers
        .getList()) {
      try {
        controller = controllerFeature.beforeCreate(name, properties);
        if (controller != null)
          return controller;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-controller-create failure because of {}", this,
            controllerFeature.getClass().getName(), e.getMessage(), e);
      }
    }

    controller = PolicyController.factory.build(name, properties);
    if (this.isLocked())
      controller.lock();

    // feature hook
    for (final PolicyControllerFeatureAPI controllerFeature : PolicyControllerFeatureAPI.providers
        .getList()) {
      try {
        if (controllerFeature.afterCreate(controller))
          return controller;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-controller-create failure because of {}", this,
            controllerFeature.getClass().getName(), e.getMessage(), e);
      }
    }

    return controller;
  }


  @Override
  public boolean configure(PdpdConfiguration config) {

    if (config == null)
      throw new IllegalArgumentException("No configuration provided");

    final String entity = config.getEntity();

    switch (entity) {
      case PdpdConfiguration.CONFIG_ENTITY_CONTROLLER:
        /* only this one supported for now */
        final List<ControllerConfiguration> configControllers = config.getControllers();
        if (configControllers == null || configControllers.isEmpty()) {
          if (logger.isInfoEnabled())
            logger.info("No controller configuration provided: " + config);
          return false;
        }
        final List<PolicyController> policyControllers =
            this.updatePolicyControllers(config.getControllers());
        if (policyControllers == null || policyControllers.isEmpty())
          return false;
        else if (policyControllers.size() == configControllers.size())
          return true;

        return false;
      default:
        final String msg = "Configuration Entity is not supported: " + entity;
        logger.warn(msg);
        throw new IllegalArgumentException(msg);
    }
  }

  @Override
  public List<PolicyController> updatePolicyControllers(
      List<ControllerConfiguration> configControllers)
      throws IllegalArgumentException, IllegalStateException {

    final List<PolicyController> policyControllers = new ArrayList<PolicyController>();
    if (configControllers == null || configControllers.isEmpty()) {
      if (logger.isInfoEnabled())
        logger.info("No controller configuration provided: " + configControllers);
      return policyControllers;
    }

    for (final ControllerConfiguration configController : configControllers) {
      try {
        final PolicyController policyController = this.updatePolicyController(configController);
        policyControllers.add(policyController);
      } catch (final Exception e) {
        logger.error("{}: cannot update-policy-controllers because of {}", this, e.getMessage(), e);
      }
    }

    return policyControllers;
  }

  @Override
  public synchronized PolicyController updatePolicyController(ControllerConfiguration configController) {

    if (configController == null)
      throw new IllegalArgumentException("No controller configuration has been provided");

    final String controllerName = configController.getName();
    if (controllerName == null || controllerName.isEmpty()) {
      logger.warn("controller-name  must be provided");
      throw new IllegalArgumentException("No controller configuration has been provided");
    }

    PolicyController policyController = null;
    try {
      final String operation = configController.getOperation();
      if (operation == null || operation.isEmpty()) {
        logger.warn("operation must be provided");
        throw new IllegalArgumentException("operation must be provided");
      }

      try {
        policyController = PolicyController.factory.get(controllerName);
      } catch (final IllegalArgumentException e) {
        // not found
        logger.warn("Policy Controller " + controllerName + " not found", e);
      }

      if (policyController == null) {

        if (operation.equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK)
            || operation
                .equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK)) {
          throw new IllegalArgumentException(
              controllerName + " is not available for operation " + operation);
        }

        /* Recovery case */

        logger.warn("controller " + controllerName + " does not exist.  "
            + "Attempting recovery from disk");

        final Properties properties =
            SystemPersistence.manager.getControllerProperties(controllerName);

        /*
         * returned properties cannot be null (per implementation) assert (properties != null)
         */

        if (properties == null) {
          throw new IllegalArgumentException(controllerName + " is invalid");
        }

        logger.warn("controller " + controllerName + " being recovered. "
            + "Reset controller's bad maven coordinates to brainless");

        /*
         * try to bring up bad controller in brainless mode, after having it working, apply the new
         * create/update operation.
         */
        properties.setProperty(PolicyProperties.RULES_GROUPID, DroolsController.NO_GROUP_ID);
        properties.setProperty(PolicyProperties.RULES_ARTIFACTID, DroolsController.NO_ARTIFACT_ID);
        properties.setProperty(PolicyProperties.RULES_VERSION, DroolsController.NO_VERSION);

        policyController = PolicyEngine.manager.createPolicyController(controllerName, properties);

        /* fall through to do brain update operation */
      }

      switch (operation) {
        case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_CREATE:
          PolicyController.factory.patch(policyController, configController.getDrools());
          break;
        case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UPDATE:
          policyController.unlock();
          PolicyController.factory.patch(policyController, configController.getDrools());
          break;
        case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK:
          policyController.lock();
          break;
        case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK:
          policyController.unlock();
          break;
        default:
          final String msg = "Controller Operation Configuration is not supported: " + operation
              + " for " + controllerName;
          logger.warn(msg);
          throw new IllegalArgumentException(msg);
      }

      return policyController;
    } catch (final Exception e) {
      logger.error("{}: cannot update-policy-controller because of {}", this, e.getMessage(), e);
      throw e;
    } catch (final LinkageError e) {
      logger.error("{}: cannot update-policy-controllers (rules) because of {}", this,
          e.getMessage(), e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public synchronized boolean start() {

    /* policy-engine dispatch pre start hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.beforeStart(this))
          return true;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-start failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    boolean success = true;
    if (this.locked)
      throw new IllegalStateException("Engine is locked");

    this.alive = true;

    /* Start Policy Engine exclusively-owned (unmanaged) http servers */

    for (final HttpServletServer httpServer : this.httpServers) {
      try {
        if (!httpServer.waitedStart(10 * 1000L))
          success = false;
      } catch (final Exception e) {
        logger.error("{}: cannot start http-server {} because of {}", this, httpServer,
            e.getMessage(), e);
      }
    }

    /* Start Policy Engine exclusively-owned (unmanaged) sources */

    for (final TopicSource source : this.sources) {
      try {
        if (!source.start())
          success = false;
      } catch (final Exception e) {
        logger.error("{}: cannot start topic-source {} because of {}", this, source, e.getMessage(),
            e);
      }
    }

    /* Start Policy Engine owned (unmanaged) sinks */

    for (final TopicSink sink : this.sinks) {
      try {
        if (!sink.start())
          success = false;
      } catch (final Exception e) {
        logger.error("{}: cannot start topic-sink {} because of {}", this, sink, e.getMessage(), e);
      }
    }

    /* Start Policy Controllers */

    final List<PolicyController> controllers = PolicyController.factory.inventory();
    for (final PolicyController controller : controllers) {
      try {
        if (!controller.start())
          success = false;
      } catch (final Exception e) {
        logger.error("{}: cannot start policy-controller {} because of {}", this, controller,
            e.getMessage(), e);
        success = false;
      }
    }

    /* Start managed Topic Endpoints */

    try {
      if (!TopicEndpoint.manager.start())
        success = false;
    } catch (final IllegalStateException e) {
      logger.warn("{}: Topic Endpoint Manager is in an invalid state because of {}", this,
          e.getMessage(), e);
    }


    // Start the JMX listener

    PdpJmxListener.start();

    /* policy-engine dispatch after start hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.afterStart(this))
          return success;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-start failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    return success;
  }

  @Override
  public synchronized boolean stop() {

    /* policy-engine dispatch pre stop hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.beforeStop(this))
          return true;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-stop failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    /* stop regardless of the lock state */

    boolean success = true;
    if (!this.alive)
      return true;

    this.alive = false;

    final List<PolicyController> controllers = PolicyController.factory.inventory();
    for (final PolicyController controller : controllers) {
      try {
        if (!controller.stop())
          success = false;
      } catch (final Exception e) {
        logger.error("{}: cannot stop policy-controller {} because of {}", this, controller,
            e.getMessage(), e);
        success = false;
      }
    }

    /* Stop Policy Engine owned (unmanaged) sources */
    for (final TopicSource source : this.sources) {
      try {
        if (!source.stop())
          success = false;
      } catch (final Exception e) {
        logger.error("{}: cannot start topic-source {} because of {}", this, source, e.getMessage(),
            e);
      }
    }

    /* Stop Policy Engine owned (unmanaged) sinks */
    for (final TopicSink sink : this.sinks) {
      try {
        if (!sink.stop())
          success = false;
      } catch (final Exception e) {
        logger.error("{}: cannot start topic-sink {} because of {}", this, sink, e.getMessage(), e);
      }
    }

    /* stop all managed topics sources and sinks */
    if (!TopicEndpoint.manager.stop())
      success = false;

    /* stop all unmanaged http servers */
    for (final HttpServletServer httpServer : this.httpServers) {
      try {
        if (!httpServer.stop())
          success = false;
      } catch (final Exception e) {
        logger.error("{}: cannot start http-server {} because of {}", this, httpServer,
            e.getMessage(), e);
      }
    }

    /* policy-engine dispatch pre stop hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.afterStop(this))
          return success;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-stop failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    return success;
  }

  @Override
  public synchronized void shutdown() {
    
    /* 
     * shutdown activity even when underlying subcomponents
     * (features, controllers, topics, etc ..) are stuck 
     */
    
    Thread exitThread = new Thread(new Runnable() {    
      private static final long SHUTDOWN_MAX_GRACE_TIME = 30000L;
      
      @Override
      public void run() {
        try {
          Thread.sleep(SHUTDOWN_MAX_GRACE_TIME);
          logger.warn("{}: abnormal termination - shutdown graceful time period expiration", 
              PolicyEngineManager.this);
        } catch (final InterruptedException e) {
          /* courtesy to shutdown() to allow it to return  */
          synchronized(PolicyEngineManager.this) {}
          logger.info("{}: finishing a graceful shutdown ", 
              PolicyEngineManager.this, e);
        } finally {
          /* 
           * shut down the Policy Engine owned http servers as the  very last thing
           */
          for (final HttpServletServer httpServer : PolicyEngineManager.this.getHttpServers()) {
            try {
              httpServer.shutdown();
            } catch (final Exception e) {
              logger.error("{}: cannot shutdown http-server {} because of {}", 
                  PolicyEngineManager.this, httpServer, e.getMessage(), e);
            }
          }
          
          logger.info("{}: exit" , PolicyEngineManager.this);
          System.exit(0);
        }
      }
    });
    exitThread.start();

    /* policy-engine dispatch pre shutdown hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.beforeShutdown(this))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-shutdown failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    this.alive = false;

    /* Shutdown Policy Engine owned (unmanaged) sources */
    for (final TopicSource source : this.sources) {
      try {
        source.shutdown();
      } catch (final Exception e) {
        logger.error("{}: cannot shutdown topic-source {} because of {}", this, source,
            e.getMessage(), e);
      }
    }

    /* Shutdown Policy Engine owned (unmanaged) sinks */
    for (final TopicSink sink : this.sinks) {
      try {
        sink.shutdown();
      } catch (final Exception e) {
        logger.error("{}: cannot shutdown topic-sink {} because of {}", this, sink, e.getMessage(),
            e);
      }
    }

    /* Shutdown managed resources */
    PolicyController.factory.shutdown();
    TopicEndpoint.manager.shutdown();
    HttpServletServer.factory.destroy();

    // Stop the JMX listener

    PdpJmxListener.stop();

    /* policy-engine dispatch post shutdown hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.afterShutdown(this))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-shutdown failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }
    
    exitThread.interrupt();
    logger.info("{}: normal termination" , this);
  }

  @Override
  public synchronized boolean isAlive() {
    return this.alive;
  }

  @Override
  public synchronized boolean lock() {

    /* policy-engine dispatch pre lock hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.beforeLock(this))
          return true;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-lock failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    if (this.locked)
      return true;

    this.locked = true;

    boolean success = true;
    final List<PolicyController> controllers = PolicyController.factory.inventory();
    for (final PolicyController controller : controllers) {
      try {
        success = controller.lock() && success;
      } catch (final Exception e) {
        logger.error("{}: cannot lock policy-controller {} because of {}", this, controller,
            e.getMessage(), e);
        success = false;
      }
    }

    success = TopicEndpoint.manager.lock() && success;

    /* policy-engine dispatch post lock hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.afterLock(this))
          return success;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-lock failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    return success;
  }

  @Override
  public synchronized boolean unlock() {

    /* policy-engine dispatch pre unlock hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.beforeUnlock(this))
          return true;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-unlock failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    if (!this.locked)
      return true;

    this.locked = false;

    boolean success = true;
    final List<PolicyController> controllers = PolicyController.factory.inventory();
    for (final PolicyController controller : controllers) {
      try {
        success = controller.unlock() && success;
      } catch (final Exception e) {
        logger.error("{}: cannot unlock policy-controller {} because of {}", this, controller,
            e.getMessage(), e);
        success = false;
      }
    }

    success = TopicEndpoint.manager.unlock() && success;

    /* policy-engine dispatch after unlock hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.afterUnlock(this))
          return success;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-unlock failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    return success;
  }

  @Override
  public synchronized boolean isLocked() {
    return this.locked;
  }

  @Override
  public void removePolicyController(String name) {
    PolicyController.factory.destroy(name);
  }

  @Override
  public void removePolicyController(PolicyController controller) {
    PolicyController.factory.destroy(controller);
  }

  @JsonIgnore
  @Override
  public List<PolicyController> getPolicyControllers() {
    return PolicyController.factory.inventory();
  }

  @JsonProperty("controllers")
  @Override
  public List<String> getPolicyControllerIds() {
    final List<String> controllerNames = new ArrayList<String>();
    for (final PolicyController controller : PolicyController.factory.inventory()) {
      controllerNames.add(controller.getName());
    }
    return controllerNames;
  }

  @Override
  @JsonIgnore
  public Properties getProperties() {
    return this.properties;
  }


  @SuppressWarnings("unchecked")
  @Override
  public List<TopicSource> getSources() {
    return (List<TopicSource>) this.sources;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<TopicSink> getSinks() {
    return (List<TopicSink>) this.sinks;
  }

  @Override
  public List<HttpServletServer> getHttpServers() {
    return this.httpServers;
  }

  @Override
  public List<String> getFeatures() {
    final List<String> features = new ArrayList<String>();
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      features.add(feature.getName());
    }
    return features;
  }

  @JsonIgnore
  @Override
  public List<PolicyEngineFeatureAPI> getFeatureProviders() {
    return PolicyEngineFeatureAPI.providers.getList();
  }

  @Override
  public PolicyEngineFeatureAPI getFeatureProvider(String featureName) {
    if (featureName == null || featureName.isEmpty())
      throw new IllegalArgumentException("A feature name must be provided");

    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      if (feature.getName().equals(featureName))
        return feature;
    }

    throw new IllegalArgumentException("Invalid Feature Name: " + featureName);
  }

  @Override
  public void onTopicEvent(CommInfrastructure commType, String topic, String event) {
    /* configuration request */
    try {
      final PdpdConfiguration configuration = this.decoder.fromJson(event, PdpdConfiguration.class);
      this.configure(configuration);
    } catch (final Exception e) {
      logger.error("{}: configuration-error due to {} because of {}", this, event, e.getMessage(),
          e);
    }
  }

  @Override
  public boolean deliver(String topic, Object event)
      throws IllegalArgumentException, IllegalStateException {

    /*
     * Note this entry point is usually from the DRL
     */

    if (topic == null || topic.isEmpty())
      throw new IllegalArgumentException("Invalid Topic");

    if (event == null)
      throw new IllegalArgumentException("Invalid Event");

    if (!this.isAlive())
      throw new IllegalStateException("Policy Engine is stopped");

    if (this.isLocked())
      throw new IllegalStateException("Policy Engine is locked");

    final List<? extends TopicSink> sinks = TopicEndpoint.manager.getTopicSinks(topic);
    if (sinks == null || sinks.isEmpty() || sinks.size() > 1)
      throw new IllegalStateException(
          "Cannot ensure correct delivery on topic " + topic + ": " + sinks);

    return this.deliver(sinks.get(0).getTopicCommInfrastructure(), topic, event);
  }

  @Override
  public boolean deliver(String busType, String topic, Object event)
      throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException {

    /*
     * Note this entry point is usually from the DRL (one of the reasons busType is String.
     */

    if (busType == null || busType.isEmpty())
      throw new IllegalArgumentException("Invalid Communication Infrastructure");

    if (topic == null || topic.isEmpty())
      throw new IllegalArgumentException("Invalid Topic");

    if (event == null)
      throw new IllegalArgumentException("Invalid Event");

    boolean valid = false;
    for (final Topic.CommInfrastructure comm : Topic.CommInfrastructure.values()) {
      if (comm.name().equals(busType)) {
        valid = true;
      }
    }

    if (!valid)
      throw new IllegalArgumentException("Invalid Communication Infrastructure: " + busType);


    if (!this.isAlive())
      throw new IllegalStateException("Policy Engine is stopped");

    if (this.isLocked())
      throw new IllegalStateException("Policy Engine is locked");


    return this.deliver(Topic.CommInfrastructure.valueOf(busType), topic, event);
  }

  @Override
  public boolean deliver(Topic.CommInfrastructure busType, String topic, Object event)
      throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException {

    if (topic == null || topic.isEmpty())
      throw new IllegalArgumentException("Invalid Topic");

    if (event == null)
      throw new IllegalArgumentException("Invalid Event");

    if (!this.isAlive())
      throw new IllegalStateException("Policy Engine is stopped");

    if (this.isLocked())
      throw new IllegalStateException("Policy Engine is locked");

    /*
     * Try to send through the controller, this is the preferred way, since it may want to apply
     * additional processing
     */
    try {
      final DroolsController droolsController =
          EventProtocolCoder.manager.getDroolsController(topic, event);
      final PolicyController controller = PolicyController.factory.get(droolsController);
      if (controller != null)
        return controller.deliver(busType, topic, event);
    } catch (final Exception e) {
      logger.warn("{}: cannot find policy-controller to deliver {} over {}:{} because of {}", this,
          event, busType, topic, e.getMessage(), e);

      /* continue (try without routing through the controller) */
    }

    /*
     * cannot route through the controller, send directly through the topic sink
     */
    try {
      final String json = EventProtocolCoder.manager.encode(topic, event);
      return this.deliver(busType, topic, json);

    } catch (final Exception e) {
      logger.warn("{}: cannot deliver {} over {}:{} because of {}", this, event, busType, topic,
          e.getMessage(), e);
      throw e;
    }
  }

  @Override
  public boolean deliver(Topic.CommInfrastructure busType, String topic, String event)
      throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException {

    if (topic == null || topic.isEmpty())
      throw new IllegalArgumentException("Invalid Topic");

    if (event == null || event.isEmpty())
      throw new IllegalArgumentException("Invalid Event");

    if (!this.isAlive())
      throw new IllegalStateException("Policy Engine is stopped");

    if (this.isLocked())
      throw new IllegalStateException("Policy Engine is locked");

    try {
      final TopicSink sink = TopicEndpoint.manager.getTopicSink(busType, topic);

      if (sink == null)
        throw new IllegalStateException("Inconsistent State: " + this);

      return sink.send(event);

    } catch (final Exception e) {
      logger.warn("{}: cannot deliver {} over {}:{} because of {}", this, event, busType, topic,
          e.getMessage(), e);
      throw e;
    }
  }

  @Override
  public synchronized void activate() {

    /* policy-engine dispatch pre activate hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.beforeActivate(this))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-activate failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    // activate 'policy-management'
    for (final PolicyController policyController : this.getPolicyControllers()) {
      try {
        policyController.unlock();
        policyController.start();
      } catch (final Exception e) {
        logger.error("{}: cannot activate of policy-controller {} because of {}", this,
            policyController, e.getMessage(), e);
      } catch (final LinkageError e) {
        logger.error(
            "{}: cannot activate (rules compilation) of policy-controller {} because of {}", this,
            policyController, e.getMessage(), e);
      }
    }

    this.unlock();

    /* policy-engine dispatch post activate hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.afterActivate(this))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-activate failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }
  }

  @Override
  public synchronized void deactivate() {

    /* policy-engine dispatch pre deactivate hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.beforeDeactivate(this))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} before-deactivate failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }

    this.lock();

    for (final PolicyController policyController : this.getPolicyControllers()) {
      try {
        policyController.stop();
      } catch (final Exception e) {
        logger.error("{}: cannot deactivate (stop) policy-controller {} because of {}", this,
            policyController, e.getMessage(), e);
      } catch (final LinkageError e) {
        logger.error("{}: cannot deactivate (stop) policy-controller {} because of {}", this,
            policyController, e.getMessage(), e);
      }
    }

    /* policy-engine dispatch post deactivate hook */
    for (final PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
      try {
        if (feature.afterDeactivate(this))
          return;
      } catch (final Exception e) {
        logger.error("{}: feature {} after-deactivate failure because of {}", this,
            feature.getClass().getName(), e.getMessage(), e);
      }
    }
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("PolicyEngineManager [alive=").append(this.alive).append(", locked=")
        .append(this.locked).append("]");
    return builder.toString();
  }

}


