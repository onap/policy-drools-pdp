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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.onap.policy.drools.core.DroolsRunnable;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to generate and restore backup Drools data.
 */
public class DroolsSessionRestore implements Restore, Serializable {
    private static final long serialVersionUID = 7990797023342194129L;
    private static Logger logger =
            LoggerFactory.getLogger(DroolsSessionRestore.class);
    // backup data for all Drools sessions on this host
    private final List<SingleSession> sessions = new LinkedList<>();
    private long droolsTimeoutMillis;
    
    /** 
     * Constructor: initialize droolsTimeoutMillis value.
     */
    public DroolsSessionRestore(long droolsTimeoutMillis) {
        this.droolsTimeoutMillis = droolsTimeoutMillis;
    }

    /**
     * {@inheritDoc}
     */
    public boolean backup(int bucketNumber) {
        /**
         * There may be multiple Drools sessions being backed up at the same
         * time. There is one 'Pair' in the list for each session being
         * backed up.
         */
        List<Pair<CompletableFuture<List<Object>>, PolicySession>>
            pendingData = new LinkedList<>();
        for (PolicyContainer pc : PolicyContainer.getPolicyContainers()) {
            for (PolicySession session : pc.getPolicySessions()) {
                // Wraps list of objects, to be populated in the session
                final CompletableFuture<List<Object>> droolsObjectsWrapper =
                    new CompletableFuture<>();

                // 'KieSessionObject'
                final KieSession kieSession = session.getKieSession();

                logger.info("{}: about to fetch data for session {}",
                            this, session.getFullName());
                kieSession.insert(new DroolsRunnable() {
                    @Override
                    public void run() {
                        List<Object> droolsObjects = new ArrayList<>();
                        for (Object o : kieSession.getObjects()) {
                            String keyword = Keyword.lookupKeyword(o);
                            if (keyword != null
                                    && Bucket.bucketNumber(keyword) == bucketNumber) {
                                // bucket matches -- include this object
                                droolsObjects.add(o);

                                FactHandle fh = kieSession.getFactHandle(o);
                                if (fh != null) {
                                    /**
                                     * delete this object from Drools memory
                                     * this classes are used in bucket migration, 
                                     * so the delete is intentional.
                                     */
                                    kieSession.delete(fh);
                                } else {
                                    // ERROR: no 'FactHandle' for object
                                    logger.error("{}: no FactHandle for object of {}",
                                        this, o.getClass());
                                }
                            }
                        }

                        // send notification that object list is complete
                        droolsObjectsWrapper.complete(droolsObjects);
                    }
                });
                // add pending operation to the list
                pendingData.add(new Pair<>(droolsObjectsWrapper, session));
            }
        }

        /**
         * data copying can start as soon as we receive results
         * from pending sessions (there may not be any)
         */
        copyDataFromSession(pendingData);
        return !sessions.isEmpty();
    }

    /**
     * Copy data from pending sessions.
     * @param pendingData a list of policy sessions
     */
    private void copyDataFromSession(List<Pair<CompletableFuture<List<Object>>, PolicySession>>
        pendingData) {
        long endTime = System.currentTimeMillis() + droolsTimeoutMillis;

        for (Pair<CompletableFuture<List<Object>>, PolicySession> pair :
                pendingData) {
            PolicySession session = pair.second();
            long delay = endTime - System.currentTimeMillis();
            if (delay < 0) {
                /**
                 * we have already reached the time limit, so we will
                 * only process data that has already been received
                 */
                delay = 0;
            }
            try {
                List<Object> droolsObjects =
                    pair.first().get(delay, TimeUnit.MILLISECONDS);

                // if we reach this point, session data read has completed
                logger.info("{}: session={}, got {} object(s)",
                            this, session.getFullName(),
                            droolsObjects.size());
                if (!droolsObjects.isEmpty()) {
                    sessions.add(new SingleSession(session, droolsObjects));
                }
            } catch (TimeoutException e) {
                logger.error("{}: Timeout waiting for data from session {}",
                    this, session.getFullName());
            } catch (Exception e) {
                logger.error("{}: Exception writing output data", this, e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restore(int bucketNumber) {
        /**
         * There may be multiple Drools sessions being restored at the same
         * time. There is one entry in 'sessionLatches' for each session
         * being restored.
         */
        List<CountDownLatch> sessionLatches = new LinkedList<>();
        for (SingleSession session : sessions) {
            try {
                CountDownLatch sessionLatch = session.restore();
                if (sessionLatch != null) {
                    // there is a restore in progress -- add it to the list
                    sessionLatches.add(sessionLatch);
                }
            } catch (IOException | ClassNotFoundException e) {
                logger.error("Exception in {}", this, e);
            }
        }

        // wait for all sessions to be updated
        try {
            for (CountDownLatch sessionLatch : sessionLatches) {
                if (!sessionLatch.await(droolsTimeoutMillis, TimeUnit.MILLISECONDS)) {
                    logger.error("{}: timed out waiting for session latch", this);
                }
            }
        } catch (InterruptedException e) {
            logger.error("Exception in {}", this, e);
        }
    }
}
