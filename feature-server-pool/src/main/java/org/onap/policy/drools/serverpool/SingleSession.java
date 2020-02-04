/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
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

package org.onap.policy.drools.serverpool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.kie.api.runtime.KieSession;
import org.onap.policy.drools.core.DroolsRunnable;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each instance of this class corresponds to a Drools session that has
 * been backed up, or is being restored.
 */
public class SingleSession implements Serializable {
    private static final long serialVersionUID = 1899990835198765434L;
    private static Logger logger =
            LoggerFactory.getLogger(SingleSession.class);
    // the group id associated with the Drools container
    String groupId;

    // the artifact id associated with the Drools container
    String artifactId;

    // the session name within the Drools container
    String sessionName;

    // serialized data associated with this session (and bucket)
    byte[] data;

    /**
     * Constructor - initialize the 'SingleSession' instance, so it can
     * be serialized.
     *
     * @param session the Drools session being backed up
     * @param droolsObjects the Drools objects from this session associated
     *     with the bucket currently being backed up
     */
    SingleSession(PolicySession session, List<Object> droolsObjects) throws IOException {
        // 'groupId' and 'artifactId' are set from the 'PolicyContainer'
        PolicyContainer pc = session.getPolicyContainer();
        groupId = pc.getGroupId();
        artifactId = pc.getArtifactId();

        // 'sessionName' is set from the 'PolicySession'
        sessionName = session.getName();

        /**
         * serialize the Drools objects -- we serialize them here, because they
         * need to be deserialized within the scope of the Drools session
         */
        data = Util.serialize(droolsObjects);
    }

    CountDownLatch restore() throws IOException, ClassNotFoundException {
        PolicySession session = null;

        // locate the 'PolicyContainer', and 'PolicySession'
        for (PolicyContainer pc : PolicyContainer.getPolicyContainers()) {
            if (artifactId.equals(pc.getArtifactId())
                    && groupId.equals(pc.getGroupId())) {
                session = pc.getPolicySession(sessionName);
                if (session != null) {
                    return insertSessionData(session, new ByteArrayInputStream(data));
                }
                logger.error("Policy session is null in FetureServerPool.restore");
                return null;
            }
        }
        logger.error("{}: unable to locate session name {}", this, sessionName);
        return null;
    }

    /**
     * Deserialize session data, and insert the objects into the session
     * from within the Drools session thread.
     *
     * @param session the associated PolicySession instance
     * @param bis the data to be deserialized
     * @return a CountDownLatch, which will indicate when the operation has
     *     completed (null in case of failure)
     * @throws IOException IO errors while creating or reading from
     *     the object stream
     * @throws ClassNotFoundException class not found during deserialization
     */
    private CountDownLatch insertSessionData(PolicySession session, ByteArrayInputStream bis)
        throws IOException, ClassNotFoundException {
        ClassLoader classLoader = session.getPolicyContainer().getClassLoader();
        ExtendedObjectInputStream ois =
            new ExtendedObjectInputStream(bis, classLoader);

        /**
         * associate the current thread with the session,
         * and deserialize
         */
        session.setPolicySession();
        Object obj = ois.readObject();

        if (obj instanceof List) {
            final List<?> droolsObjects = (List<?>)obj;
            logger.info("{}: session={}, got {} object(s)",
                        this, session.getFullName(), droolsObjects.size());

            // signal when session update is complete
            final CountDownLatch sessionLatch = new CountDownLatch(1);

            // 'KieSession' object
            final KieSession kieSession = session.getKieSession();

            // run the following within the Drools session thread
            kieSession.insert(new DroolsRunnable() {
                @Override
                public void run() {
                    try {
                        /**
                         * Insert all of the objects -- note that this is running
                         * in the session thread, so no other rules can fire
                         * until all of the objects are inserted.
                         */
                        for (Object obj : droolsObjects) {
                            kieSession.insert(obj);
                        }
                    } finally {
                        // send notification that the inserts have completed
                        sessionLatch.countDown();
                    }
                }
            });
            return sessionLatch;
        } else {
            logger.error("{}: Invalid session data for session={}, type={}",
                         this, session.getFullName(), obj.getClass().getName());
        }
        return null;
    }
}
