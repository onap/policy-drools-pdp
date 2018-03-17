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

// TODO move to policy-utils

import java.lang.annotation.Annotation;
import java.util.Properties;
import org.onap.policy.common.utils.properties.SpecPropertyConfiguration;
import org.onap.policy.common.utils.properties.exception.PropertyException;

/**
 * Checks whether or not a feature is enabled. The name of the "enable" property
 * is assumed to be of the form accepted by a {@link SpecPropertyConfiguration},
 * which contains a substitution place-holder into which a "specializer" (e.g.,
 * controller or session name) is substituted.
 */
public class FeatureEnabledChecker {

    /**
     * 
     */
    private FeatureEnabledChecker() {
        super();
    }

    /**
     * Determines if a feature is enabled for a particular specializer.
     * 
     * @param props properties from which to extract the "enabled" flag
     * @param specializer specializer to be substituted into the property name
     *        when extracting
     * @param propName the name of the "enabled" property
     * @return {@code true} if the feature is enabled, or {@code false} if it is
     *         not enabled (or if the property doesn't exist)
     * @throws PoolingFeatureRtException if the "enabled" property is not a
     *         boolean value
     */
    public static boolean isFeatureEnabled(Properties props, String specializer, String propName) {

        try {
            return new Config(specializer).isEnabled(props, propName);

        } catch (PropertyException e) {
            throw new PoolingFeatureRtException("cannot check property " + propName, e);
        }
    }


    /**
     * Configuration used to extract the value.
     */
    private static class Config extends SpecPropertyConfiguration {

        /**
         * 
         * @param specializer specializer to be substituted into the property
         *        name when extracting
         * @throws PropertyException if an error occurs
         */
        public Config(String specializer) throws PropertyException {
            super(specializer);
        }

        /**
         * 
         * @param props properties from which to extract the "enabled" flag
         * @param propName the name of the "enabled" property
         * @return {@code true} if the feature is enabled, or {@code false} if
         *         it is not enabled (or if the property doesn't exist)
         * @throws PropertyException if the "enabled" property is not a boolean
         *         value
         */
        public boolean isEnabled(Properties props, String propName) throws PropertyException {

            /**
             * Property we'll use to extract the value.
             */
            Property prop = new Property() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return null;
                }

                @Override
                public String defaultValue() {
                    return "";
                }

                @Override
                public boolean emptyOk() {
                    return false;
                }

                @Override
                public String name() {
                    return propName;
                }
            };

            Object val = this.getValue(null, Boolean.class, props, prop);
            if (val == null) {
                return false;
            }

            return ((Boolean) val).booleanValue();
        }
    };
}
