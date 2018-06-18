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

package org.onap.policy.drools.util;

import java.util.Properties;

/**
 * Checks whether or not a feature is enabled.
 */
public class FeatureEnabledChecker {

    /**
     * 
     */
    private FeatureEnabledChecker() {
        super();
    }

    /**
     * Determines if a feature is enabled.
     * 
     * @param props properties from which to extract the "enabled" flag
     * @param propName the name of the "enabled" property
     * @return {@code true} if the feature is enabled, or {@code false} if it is not
     *         enabled (or if the property doesn't exist)
     */
    public static boolean isFeatureEnabled(Properties props, String propName) {
        String val = props.getProperty(propName);
        return (val != null ? Boolean.valueOf(val) : false);
    }
}
