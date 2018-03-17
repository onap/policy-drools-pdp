/*
 * ============LICENSE_START=======================================================
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
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
 * <p/>
 * With each controller, there is an associated DMaaP topic that is used for
 * internal communication between the different hosts serving the controller.
 */

public class PoolingFeature implements PolicyControllerFeatureAPI, PolicySessionFeatureAPI {

	private static final Logger logger = LoggerFactory.getLogger(PoolingFeature.class);

	// TODO state-management doesn't allow more than one active host at a time

	// TODO can we assume controller-specific properties are in controller
	// property file?

	/**
	 * Feature properties.
	 */
	private Properties featProps = null;

	/**
	 * Maps the controller to its associated manager.
	 */
	private IdentityHashMap<PolicyController, PoolingManagerImpl> ctlr2pool = new IdentityHashMap<>();

	/**
	 * Imports a controller name to its associated manager.
	 */
	private Map<String, PoolingManagerImpl> name2pool = new HashMap<>();

	public PoolingFeature() {

	}

	@Override
	public int getSequenceNumber() {
		return 0;
	}

	@Override
	public void globalInit(String[] args, String configDir) {
		logger.info("initializing pooling feature");

		try {
			Properties newProps = PropertyUtil.getProperties(configDir + "/feature-pooling.properties");
			verifyUncommonProperties(newProps);

			featProps = newProps;

		} catch (IOException ex) {
			throw new PoolingFeatureRtException(ex);
		}
	}

	private void verifyUncommonProperties(Properties props) {
		for (String name : PoolingProperties.UNCOMMON_PROPERTIES) {
			if (props.get(name) == null) {
				throw new PoolingFeatureRtException("pooling properties should not contain " + name);
			}
		}
	}

	@Override
	public PolicyController beforeCreate(String name, Properties properties) {
		if (featProps == null) {
			return null;
		}

		TypedProperties props = new TypedProperties(detmProperties(name));

		if (props.getOptBoolProperty(PoolingProperties.PROP_FEATURE_ENABLED, false)) {
			logger.info("pooling enabled for {}", name);

			synchronized (ctlr2pool) {
				PoolingManagerImpl mgr = name2pool.get(name);
				if (mgr == null) {
					name2pool.put(name, new PoolingManagerImpl(props));
				}
			}

			copyProperties(name, properties);
		}

		return null;
	}

	private void copyProperties(String name, Properties properties) {
		String prefix = PoolingProperties.PROP_CONTROLLER_PREFIX + name + ".";

		for (Entry<Object, Object> ent : featProps.entrySet()) {
			String propnm = ent.getKey().toString();

			if (propnm.startsWith(prefix)) {
				String nm = propnm.substring(prefix.length());

				if (nm.isEmpty()) {
					logger.warn("ignoring property {}", propnm);

				} else {
					properties.put(nm, ent.getValue());

				}
			}
		}
	}

	@Override
	public boolean afterCreate(PolicyController controller) {
		synchronized (ctlr2pool) {
			PoolingManagerImpl mgr = name2pool.get(controller.getName());
			if (mgr != null && ctlr2pool.get(controller) == null) {
				mgr.setController(controller);
				ctlr2pool.put(controller, mgr);
			}
		}
		return false;
	}

	private Properties detmProperties(String name) {
		Properties props = new Properties();
		Properties specific = new Properties();

		for (Entry<Object, Object> ent : featProps.entrySet()) {
			String propnm = ent.getKey().toString();
			Matcher matcher = PoolingProperties.PROP_CONTROLLER_PATTERN.matcher(propnm);

			if (matcher.find() && matcher.start() == 0) {
				addProp(specific, name, matcher.group(1), propnm, matcher.end(), ent.getValue());

			} else {
				props.put(propnm, ent.getValue());
			}

		}

		// controller-specific properties override non-specific properties
		props.putAll(specific);

		return props;
	}

	private void addProp(Properties props, String desiredName, String actualName, String propnm, int end,
			Object value) {

		if (actualName.equals(desiredName)) {
			String nm = propnm.substring(end);

			if (nm.isEmpty()) {
				logger.warn("ignoring property {}", propnm);

			} else {
				props.put(PoolingProperties.PROP_PREFIX + nm, value);
			}
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

	// TODO change to afterExtract()

	@Override
	public boolean beforeOffer(PolicyController controller, CommInfrastructure protocol, String topic, String event) {
		return doManager(controller, mgr -> {
			return mgr.beforeOffer(protocol, topic, event);
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

	private boolean doManager(PolicyController controller, MgrFunc func) {
		PoolingManagerImpl mgr;
		synchronized (ctlr2pool) {
			mgr = ctlr2pool.get(controller);
		}

		if (mgr == null) {
			return false;
		}

		try {
			return func.apply(mgr);

		} catch (PoolingFeatureException e) {
			throw e.toRuntimeException();
		}

	}

	private boolean doDeleteManager(PolicyController controller, MgrFunc func) {
		PoolingManagerImpl mgr;
		synchronized (ctlr2pool) {
			mgr = ctlr2pool.remove(controller);
		}

		if (mgr == null) {
			return false;
		}

		try {
			return func.apply(mgr);

		} catch (PoolingFeatureException e) {
			throw e.toRuntimeException();
		}

	}

	@FunctionalInterface
	private static interface MgrFunc {
		public boolean apply(PoolingManagerImpl mgr) throws PoolingFeatureException;

	}
}
