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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Properties used by the pooling feature.
 */
public class PoolingProperties {

    /**
     * Feature properties all begin with this prefix.
     */
    public static final String PREFIX = "pooling.feature.";

    /**
     * Properties specific to a controller's pooling feature start with this
     * pattern. Group(1) is the controller name found in the feature, and
     * group(2) is the property suffix.
     */
    public static final Pattern CONTROLLER_PATTERN =
                    Pattern.compile(PREFIX.replaceAll(".", "[.]") + "controller.([^.])[.](.+)");

    // standard properties
    public static final String FEATURE_ENABLED = PREFIX + "enabled";
    public static final String POOLING_TOPIC = PREFIX + "topic";
    public static final String OFFLINE_LIMIT = PREFIX + "offline.queue.limit";
    public static final String OFFLINE_AGE_MS = PREFIX + "offline.queue.age.milliseconds";

    /**
     * Properties that should not be common across controllers. These should not
     * appear without a controller name.
     */
    public static final List<String> UNCOMMON_PROPERTIES = Collections.unmodifiableList(Arrays.asList(POOLING_TOPIC));

    /**
     * Properties required for each controller.
     */
    public static final List<String> REQUIRED_PROPERTIES =
                    Collections.unmodifiableList(Arrays.asList(FEATURE_ENABLED, POOLING_TOPIC));

    /**
     * 
     */
    private PoolingProperties() {

    }

}
