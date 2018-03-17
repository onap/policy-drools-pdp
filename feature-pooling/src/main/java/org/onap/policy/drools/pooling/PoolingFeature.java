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
import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.onap.policy.drools.core.PolicySessionFeatureAPI;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
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
public class PoolingFeature implements PolicyControllerFeatureAPI, PolicySessionFeatureAPI {

    private static final Logger logger = LoggerFactory.getLogger(PoolingFeature.class);

    // TODO state-management doesn't allow more than one active host at a time

    /**
     * Entire set of feature properties, including those specific to various
     * controllers.
     */
    private Properties featProps = null;

    /**
     * Maps a controller name to its associated manager.
     */
    private ConcurrentHashMap<String, PoolingManagerImpl> ctlr2pool = new ConcurrentHashMap<>();

    /**
     * 
     */
    public PoolingFeature() {
        super();
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
            featProps = PropertyUtil.getProperties(configDir + "/feature-pooling.properties");

        } catch (IOException ex) {
            throw new PoolingFeatureRtException(ex);
        }
    }

    /**
     * Verifies that we have the properties we need.
     */
    @Override
    public PolicyController beforeCreate(String name, Properties properties) {

        if (isFeatureEnabled(name)) {

            // validate the properties
            try {
                new PoolingProperties(name, featProps);

            } catch (PropertyException e) {
                logger.error("failed to load properties for controller {}", name);
                throw new PoolingFeatureRtException(e);
            }
        }

        return null;
    }

    /**
     * Adds the controller and a new pooling manager to {@link #ctlr2pool}.
     */
    @Override
    public boolean afterCreate(PolicyController controller) {

        String name = controller.getName();

        if (isFeatureEnabled(name)) {
            logger.info("pooling enabled for {}", name);
            ctlr2pool.computeIfAbsent(name, xxx -> new PoolingManagerImpl(controller, featProps));

        } else {
            logger.info("pooling disabled for {}", name);
        }

        return false;
    }

    /**
     * Determines if the pooling feature is enabled by looking for the
     * feature-enabled property.
     * 
     * @param name
     * @return {@code true} if the feature is enabled, {@code false} otherwise
     * @throws IllegalStateException if the feature properties are {@code null}
     *         (i.e., not yet initialized)
     * @throws PoolingFeatureRtException if the "enabled" property is not a
     *         boolean value
     */
    public boolean isFeatureEnabled(String name) {
        if (featProps == null) {
            throw new IllegalStateException("missing pooling feature properties");
        }

        return FeatureEnabledChecker.isFeatureEnabled(featProps, name, PoolingProperties.FEATURE_ENABLED);
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
        return doDeleteManager(controller, mgr -> {
            mgr.afterStop();
            return false;
        });
    }

    @Override
    public boolean beforeOffer(PolicyController controller, CommInfrastructure protocol, String topic2, String event) {
        /*
         * As beforeOffer() is invoked a lot, we'll directly call
         * mgr.beforeOffer() instead of using the functional interface via
         * doManager().
         */
        PoolingManagerImpl mgr = ctlr2pool.get(controller);
        if (mgr == null) {
            return false;
        }

        return mgr.beforeOffer(protocol, topic2, event);
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
     */
    private boolean doManager(PolicyController controller, MgrFunc func) {
        PoolingManagerImpl mgr = ctlr2pool.get(controller);
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
     */
    private boolean doDeleteManager(PolicyController controller, MgrFunc func) {
        // NOTE: using "remove" instead of "get"
        PoolingManagerImpl mgr = ctlr2pool.remove(controller);
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
}
