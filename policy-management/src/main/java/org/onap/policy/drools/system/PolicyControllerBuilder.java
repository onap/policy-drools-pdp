/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

import java.util.Properties;

/**
 * Implementers of this interface are located by using 'ServiceLoader'.
 * They provide the ability to specialize the behavior of
 * 'PolicyControllerFactory' by using the 'controller.type' property to select
 * a specific builder to construct the 'PolicyController'.
 */
public interface PolicyControllerBuilder {
    /**
     * Return the 'controller.type' value supported by this particular builder
     * (multiple types may be specified by separating them by commas).
     *
     * @return a comma-separated list of types supported by this
     *     'PolicyControllerBuilder' instance
     */
    String getType();

    /**
     * Build a controller from a properties file.
     *
     * @param name the global name of this controller
     * @param properties input parameters in form of properties for controller
     *     initialization.
     *
     * @return a Policy Controller
     *
     * @throws IllegalArgumentException invalid values provided in properties
     */
    PolicyController build(String name, Properties properties);
}
