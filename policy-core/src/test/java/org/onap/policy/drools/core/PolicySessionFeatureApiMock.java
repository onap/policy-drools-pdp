/*-
 * ============LICENSE_START=======================================================
 * policy-core
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

import java.util.ArrayList;
import lombok.Setter;
import org.kie.api.runtime.KieSession;

/**
 * This class supports 'DroolsContainerTest' by implementing
 * 'PolicySessionFeatureAPI', and providing a means to indicate
 * which hooks have been invoked.
 */
public class PolicySessionFeatureApiMock implements PolicySessionFeatureApi {
    // contains the log entries since the most recent 'getLog()' call
    private static final ArrayList<String> log = new ArrayList<>();

    // if 'true', trigger an exception right after doing the log,
    // to verify that exceptions are handled
    @Setter
    private static boolean exceptionTrigger = false;

    /**
     * Get log.
     * 
     * @return the current contents of the log, and clear the log
     */
    public static ArrayList<String> getLog() {
        synchronized (log) {
            ArrayList<String> rval = new ArrayList<>(log);
            log.clear();
            return rval;
        }
    }

    /**
     * This method adds an entry to the log, and possibly triggers an exception.
     *
     * @param arg value to add to the log
     */
    private static void addLog(String arg) {
        if (exceptionTrigger) {
            // the log entry will include a '-exception' appended to the end
            synchronized (log) {
                log.add(arg + "-exception");
            }
            System.out.println("*** " + arg + "-exception invoked ***");

            // throw an exception -- it is up to the invoking code to catch it
            throw(new IllegalStateException("Triggered from " + arg));
        } else {
            // create a log entry, and display to standard output
            synchronized (log) {
                log.add(arg);
            }
            System.out.println("*** " + arg + " invoked ***");
        }
    }

    /**
     * {@inheritDoc}.
     */
    public int getSequenceNumber() {
        return 1;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void globalInit(String[] args, String configDir) {
        addLog("globalInit");
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public KieSession activatePolicySession(PolicyContainer policyContainer, String name, String kieBaseName) {
        addLog("activatePolicySession");
        return null;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void newPolicySession(PolicySession policySession) {
        addLog("newPolicySession");
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public PolicySession.ThreadModel selectThreadModel(PolicySession session) {
        addLog("selectThreadModel");
        return null;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void disposeKieSession(PolicySession policySession) {
        addLog("disposeKieSession");
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void destroyKieSession(PolicySession policySession) {
        addLog("destroyKieSession");
    }
}
