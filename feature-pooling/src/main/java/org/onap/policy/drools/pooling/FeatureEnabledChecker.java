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
import java.lang.reflect.Field;
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
     * @throws IllegalArgumentException if the "enabled" property is not a
     *         boolean value
     */
    public static boolean isFeatureEnabled(Properties props, String specializer, String propName) {

        try {
            return new Config(specializer, props, propName).isEnabled();

        } catch (PropertyException e) {
            throw new IllegalArgumentException("cannot check property " + propName, e);
        }
    }


    /**
     * Configuration used to extract the value.
     */
    private static class Config extends SpecPropertyConfiguration {

        /**
         * There is a bit of trickery here. This annotation is just a
         * place-holder to get the superclass to invoke the
         * {@link #setValue(java.lang.reflect.Field, Properties, Property)
         * setValue()} method. When that's invoked, we'll substitute
         * {@link #propOverride} instead.
         */
        @Property(name = "feature-enabled-property-place-holder")
        private boolean enabled;

        /**
         * Annotation that will actually be used to set the field.
         */
        private Property propOverride;

        /**
         * 
         * @param specializer specializer to be substituted into the property
         *        name when extracting
         * @param props properties from which to extract the "enabled" flag
         * @param propName the name of the "enabled" property
         * @throws PropertyException if an error occurs
         */
        public Config(String specializer, Properties props, String propName) throws PropertyException {
            super(specializer);

            propOverride = new Property() {

                @Override
                public String name() {
                    return propName;
                }

                @Override
                public String defaultValue() {
                    // feature is disabled by default
                    return "false";
                }

                @Override
                public String accept() {
                    return "";
                }

                @Override
                public Class<? extends Annotation> annotationType() {
                    return Property.class;
                }
            };

            setAllFields(props);
        }

        /**
         * Substitutes {@link #propOverride} for "prop".
         */
        @Override
        protected boolean setValue(Field field, Properties props, Property prop) throws PropertyException {
            return super.setValue(field, props, propOverride);
        }

        /**
         * 
         * @return {@code true} if the feature is enabled, {@code false}
         *         otherwise
         */
        public boolean isEnabled() {
            return enabled;
        }
    };
}
