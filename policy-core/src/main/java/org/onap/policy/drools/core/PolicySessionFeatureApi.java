/*-
 * ============LICENSE_START=======================================================
 * policy-core
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.core;

import org.kie.api.runtime.KieSession;
import org.onap.policy.common.utils.services.OrderedService;

/**
 * This interface provides a way to invoke optional features at various
 * points in the code. At appropriate points in the
 * application, the code iterates through this list, invoking these optional
 * methods. Most of the methods here are notification only -- these tend to
 * return a 'void' value. In other cases, such as 'activatePolicySession',
 * may
 */
public interface PolicySessionFeatureApi extends OrderedService {

    /**
     * This method is called during initialization at a point right after
     * 'PolicyContainer' initialization has completed.
     *
     * @param args standard 'main' arguments, which are currently ignored
     * @param configDir the relative directory containing configuration files
     */
    default void globalInit(String[] args, String configDir) {
    }

    /**
     * This method is used to create a 'KieSession' as part of a
     * 'PolicyContainer'. The caller of this method will iterate over the
     * implementers of this interface until one returns a non-null value.
     *
     * @param policyContainer the 'PolicyContainer' instance containing this
     *     session
     * @param name the name of the KieSession (which is also the name of
     *     the associated PolicySession)
     * @param kieBaseName the name of the 'KieBase' instance containing
     *     this session
     * @return a new KieSession, if one was created, or 'null' if not
     *     (this depends on the capabilities and state of the object implementing
     *     this interface)
     */
    default KieSession activatePolicySession(PolicyContainer policyContainer, String name, String kieBaseName) {
        return null;
    }

    /**
     * This method is called after a new 'PolicySession' has been initialized,
     * and linked to the 'PolicyContainer'.
     *
     * @param policySession the new 'PolicySession' instance
     */
    default void newPolicySession(PolicySession policySession) {
    }

    /**
     * This method is called to select the 'ThreadModel' instance associated
     * with a 'PolicySession' instance.
     */
    default PolicySession.ThreadModel selectThreadModel(PolicySession session) {
        return null;
    }

    /**
     * This method is called when 'PolicySession.insertDrools' is called.
     * In a distributed host environment, features have the ability to send
     * the object do a different host, and do the insert.
     *
     * @param session the 'PolicySession' object associated with the
     *     Drools session
     * @param object the object to insert in Drools memory
     * @return 'true' if this feature is handling the operation,
     *     and 'false' if not.
     */
    default boolean insertDrools(PolicySession session, Object object) {
        return false;
    }

    /**
     * This method is called after 'KieSession.dispose()' is called.
     *
     * @param policySession the 'PolicySession' object that wrapped the
     *     'KieSession'
     */
    default void disposeKieSession(PolicySession policySession) {
    }

    /**
     * This method is called after 'KieSession.destroy()' is called.
     *
     * @param policySession the 'PolicySession' object that wrapped the
     *     'KieSession'
     */
    default void destroyKieSession(PolicySession policySession) {
    }
}
