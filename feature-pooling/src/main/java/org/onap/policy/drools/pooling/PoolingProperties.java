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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PoolingProperties {

	/**
	 * Properties beginning with "pooling.controller.<controller-name>" are
	 * copied to the controller's properties before the controller object is
	 * created.
	 */
	public static final String PROP_CONTROLLER_PREFIX = "pooling.controller.";

	/**
	 * Feature properties all begin with this prefix.
	 */
	public static final String PROP_PREFIX = "pooling.feature.";

	/**
	 * Properties specific to a controller's pooling feature start with this
	 * pattern. Group(1) is the controller name found in the feature.
	 */
	public static final Pattern PROP_CONTROLLER_PATTERN = Pattern
			.compile(PROP_PREFIX.replaceAll(".", "[.]") + "controller.([^.])[.]");

	public static final String PROP_FEATURE_ENABLED = PROP_PREFIX + "enabled";
	public static final String PROP_POOLING_TOPIC = PROP_PREFIX + "topic";

	/**
	 * Properties that should not be common across controllers. These should not
	 * appear within the default property file.
	 */
	public static final List<String> UNCOMMON_PROPERTIES = Collections
			.unmodifiableList(Arrays.asList(PROP_POOLING_TOPIC));

	/**
	 * Properties required for each controller.
	 */
	public static final List<String> REQUIRED_PROPERTIES = Collections
			.unmodifiableList(Arrays.asList(PROP_FEATURE_ENABLED, PROP_POOLING_TOPIC));

	private PoolingProperties() {

	}

}
