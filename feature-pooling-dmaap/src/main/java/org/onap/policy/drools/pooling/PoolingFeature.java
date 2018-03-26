/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.core.PolicySessionFeatureAPI;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.features.DroolsControllerFeatureAPI;
import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller/session pooling. Multiple hosts may be launched, all servicing the
 * same controllers/sessions. When this feature is enabled, the requests are
 * divided across the different hosts, instead of all running on a single,
 * active host.
 * <p>
 * With each controller, there is an associated DMaaP topic that is used for
 * internal communication between the different hosts serving the controller.
 */
public class PoolingFeature implements PolicyControllerFeatureAPI, DroolsControllerFeatureAPI, PolicySessionFeatureAPI {

    private static final Logger logger = LoggerFactory.getLogger(PoolingFeature.class);

    // TODO state-management doesn't allow more than one active host at a time

    /**
     * Factory used to create objects.
     */
    private static Factory factory;

    /**
     * Entire set of feature properties, including those specific to various
     * controllers.
     */
    private Properties featProps = null;

    /**
     * Maps a controller name to its associated manager.
     */
    private ConcurrentHashMap<String, PoolingManagerImpl> ctlr2pool = new ConcurrentHashMap<>(107);

    /**
     * Arguments passed to beforeOffer(), which are saved for when the
     * beforeInsert() is called later. As multiple threads can be active within
     * the methods at the same time, we must keep this in thread local storage.
     */
    private ThreadLocal<OfferArgs> offerArgs = new ThreadLocal<>();

    /**
     * 
     */
    public PoolingFeature() {
        super();
    }

    protected static Factory getFactory() {
        return factory;
    }

    /**
     * Sets the factory to be used to create objects. Used by junit tests.
     * 
     * @param factory the new factory to be used to create objects
     */
    protected static void setFactory(Factory factory) {
        PoolingFeature.factory = factory;
    }

    @Override
    public int getSequenceNumber() {
        return 0;
    }

    /**
     * @throws PoolingFeatureRtException if the properties cannot be read or are
     *         invalid
     */
    @Override
    public void globalInit(String[] args, String configDir) {
        logger.info("initializing pooling feature");

        try {
            featProps = PropertyUtil.getProperties(configDir + "/feature-pooling-dmaap.properties");

        } catch (IOException ex) {
            throw new PoolingFeatureRtException(ex);
        }
    }

    /**
     * Adds the controller and a new pooling manager to {@link #ctlr2pool}.
     * 
     * @throws PoolingFeatureRtException if an error occurs
     */
    @Override
    public boolean afterCreate(PolicyController controller) {

        if (featProps == null) {
            logger.error("pooling feature properties have not been loaded");
            throw new PoolingFeatureRtException(new IllegalStateException("missing pooling feature properties"));
        }

        String name = controller.getName();

        if (FeatureEnabledChecker.isFeatureEnabled(featProps, name, PoolingProperties.FEATURE_ENABLED)) {
            try {
                // get & validate the properties
                PoolingProperties props = new PoolingProperties(name, featProps);

                logger.info("pooling enabled for {}", name);
                ctlr2pool.computeIfAbsent(name, xxx -> factory.makeManager(controller, props));

            } catch (PropertyException e) {
                logger.error("pooling disabled due to exception for {}", name, e);
                throw new PoolingFeatureRtException(e);
            }

        } else {
            logger.info("pooling disabled for {}", name);
        }


        return false;
    }

    @Override
    public boolean beforeStart(PolicyController controller) {
        return doManager(controller, mgr -> {
            mgr.beforeStart();
            return false;
        });
    }

    @Override
    public boolean afterStart(PolicyController controller) {
        return doManager(controller, mgr -> {
            mgr.afterStart();
            return false;
        });
    }

    @Override
    public boolean beforeStop(PolicyController controller) {
        return doManager(controller, mgr -> {
            mgr.beforeStop();
            return false;
        });
    }

    @Override
    public boolean afterStop(PolicyController controller) {

        // NOTE: using doDeleteManager() instead of doManager()

        return doDeleteManager(controller, mgr -> {

            mgr.afterStop();
            return false;
        });
    }

    @Override
    public boolean beforeLock(PolicyController controller) {
        return doManager(controller, mgr -> {
            mgr.beforeLock();
            return false;
        });
    }

    @Override
    public boolean afterUnlock(PolicyController controller) {
        return doManager(controller, mgr -> {
            mgr.afterUnlock();
            return false;
        });
    }

    @Override
    public boolean beforeOffer(PolicyController controller, CommInfrastructure protocol, String topic2, String event) {
        /*
         * As this is invoked a lot, we'll directly call the manager's method
         * instead of using the functional interface via doManager().
         */
        PoolingManagerImpl mgr = ctlr2pool.get(controller.getName());
        if (mgr == null) {
            return false;
        }

        if (mgr.beforeOffer(protocol, topic2, event)) {
            return true;
        }

        offerArgs.set(new OfferArgs(protocol, topic2, event));
        return false;
    }

    @Override
    public boolean beforeInsert(DroolsController droolsController, Object fact) {

        OfferArgs args = offerArgs.get();
        if (args == null) {
            return false;
        }

        PolicyController controller;
        try {
            controller = factory.getController(droolsController);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }

        if (controller == null) {
            return false;
        }

        /*
         * As this is invoked a lot, we'll directly call the manager's method
         * instead of using the functional interface via doManager().
         */
        PoolingManagerImpl mgr = ctlr2pool.get(controller.getName());
        if (mgr == null) {
            return false;
        }

        return mgr.beforeInsert(args.protocol, args.topic, args.event, fact);
    }

    @Override
    public boolean afterOffer(PolicyController controller, CommInfrastructure protocol, String topic, String event,
                    boolean success) {

        // clear any stored arguments
        offerArgs.set(null);

        return false;
    }

    /**
     * Executes a function using the manager associated with the controller.
     * Catches any exceptions from the function and re-throws it as a runtime
     * exception.
     * 
     * @param controller
     * @param func function to be executed
     * @return {@code true} if the function handled the request, {@code false}
     *         otherwise
     * @throws PoolingFeatureRtException if an error occurs
     */
    private boolean doManager(PolicyController controller, MgrFunc func) {
        PoolingManagerImpl mgr = ctlr2pool.get(controller.getName());
        if (mgr == null) {
            return false;
        }

        try {
            return func.apply(mgr);

        } catch (PoolingFeatureException e) {
            throw e.toRuntimeException();
        }
    }

    /**
     * Executes a function using the manager associated with the controller and
     * then deletes the manager. Catches any exceptions from the function and
     * re-throws it as a runtime exception.
     * 
     * @param controller
     * @param func function to be executed
     * @return {@code true} if the function handled the request, {@code false}
     *         otherwise
     * @throws PoolingFeatureRtException if an error occurs
     */
    private boolean doDeleteManager(PolicyController controller, Function<PoolingManagerImpl, Boolean> func) {

        // NOTE: using "remove()" instead of "get()"

        PoolingManagerImpl mgr = ctlr2pool.remove(controller.getName());

        if (mgr == null) {
            return false;
        }

        return func.apply(mgr);
    }

    /**
     * Function that operates on a manager.
     */
    @FunctionalInterface
    private static interface MgrFunc {

        /**
         * 
         * @param mgr
         * @return {@code true} if the request was handled by the manager,
         *         {@code false} otherwise
         * @throws PoolingFeatureException
         */
        public boolean apply(PoolingManagerImpl mgr) throws PoolingFeatureException;
    }

    /**
     * Arguments captured from beforeOffer().
     */
    private static class OfferArgs {

        /**
         * Protocol of the receiving topic.
         */
        private CommInfrastructure protocol;

        /**
         * Topic on which the event was received.
         */
        private String topic;

        /**
         * The event text that was received on the topic.
         */
        private String event;

        /**
         * 
         * @param protocol
         * @param topic
         * @param event the actual event data received on the topic
         */
        public OfferArgs(CommInfrastructure protocol, String topic, String event) {
            this.protocol = protocol;
            this.topic = topic;
            this.event = event;
        }
    }

    /**
     * Used to create objects.
     */
    public static class Factory {

        /**
         * Makes a pooling manager for a controller.
         * 
         * @param controller
         * @param props properties to use to configure the manager
         * @return a new pooling manager
         */
        public PoolingManagerImpl makeManager(PolicyController controller, PoolingProperties props) {
            return new PoolingManagerImpl(controller, props);
        }

        /**
         * Gets the policy controller associated with a drools controller.
         * 
         * @param droolsController
         * @return the policy controller associated with a drools controller
         */
        public PolicyController getController(DroolsController droolsController) {
            return PolicyController.factory.get(droolsController);
        }
    }
}
