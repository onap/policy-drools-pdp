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
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
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
            verifyUncommonProperties(featProps);

        } catch (IOException ex) {
            throw new PoolingFeatureRtException(ex);
        }
    }

    /**
     * Verifies that the "uncommon" properties do not appear without a
     * controller identifier.
     * 
     * @param props
     * @throws PoolingFeatureRtException if an invalid property is found
     */
    private void verifyUncommonProperties(Properties props) {
        for (String name : PoolingProperties.UNCOMMON_PROPERTIES) {
            if (props.get(name) == null) {
                throw new PoolingFeatureRtException("pooling properties should not contain " + name);
            }
        }
    }

    /**
     * Verifies that we have the properties we need.
     */
    @Override
    public PolicyController beforeCreate(String name, Properties properties) {
        if (featProps == null) {
            return null;
        }

        TypedProperties props = new TypedProperties(getProperties(name));

        if (props.getOptBoolProperty(PoolingProperties.FEATURE_ENABLED, false)) {
            logger.info("pooling enabled for {}", name);

            /*
             * Create a pooling manager so that properties can be validated.
             * However, once that's done, we discard the manager - the real one
             * will be created in afterCreate().
             */
            new PoolingManagerImpl(null, props);
        }

        return null;
    }

    /**
     * Adds the controller and a new pooling manager to {@link #ctlr2pool}.
     */
    @Override
    public boolean afterCreate(PolicyController controller) {
        TypedProperties props = new TypedProperties(getProperties(controller.getName()));
        ctlr2pool.computeIfAbsent(controller.getName(), name -> new PoolingManagerImpl(controller, props));

        return false;
    }

    /**
     * Determine the properties for a particular controller.
     * 
     * @param name name of the controller of interest
     * @return the properties for a particular controller
     */
    private Properties getProperties(String name) {
        /*
         * Segregate the properties into general properties and
         * controller-specific properties, discarding those that are associated
         * with a different controller.
         */
        Properties props = new Properties();
        Properties specific = new Properties();

        for (Entry<Object, Object> ent : featProps.entrySet()) {
            String propnm = ent.getKey().toString();
            Matcher matcher = PoolingProperties.CONTROLLER_PATTERN.matcher(propnm);

            if (matcher.matches()) {
                addProp(specific, name, matcher.group(1), matcher.group(2), ent.getValue());

            } else {
                props.put(propnm, ent.getValue());
            }

        }

        // controller-specific properties override general properties
        props.putAll(specific);

        return props;
    }

    /**
     * Adds a property to the set of properties, if it's name matches the
     * desired name.
     * 
     * @param props properties to which to the value should be added
     * @param desiredName
     * @param actualName
     * @param suffix property name suffix
     * @param value property value
     */
    private void addProp(Properties props, String desiredName, String actualName, String suffix, Object value) {
        if (actualName.equals(desiredName)) {
            props.put(PoolingProperties.PREFIX + suffix, value);
        }
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
