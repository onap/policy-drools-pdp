/*-
 * ============LICENSE_START=======================================================
 * feature-session-persistence
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.persistence;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JpaDroolsSessionConnector implements DroolsSessionConnector {

    private static Logger logger = LoggerFactory.getLogger(JpaDroolsSessionConnector.class);

    private final EntityManagerFactory emf;

    public JpaDroolsSessionConnector(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public DroolsSession get(String sessName) {

        EntityManager em = emf.createEntityManager();
        DroolsSessionEntity entity = null;

        try (EntityMgrTrans trans = new EntityMgrTrans(em)) {

            entity = em.find(DroolsSessionEntity.class, sessName);
            if (entity != null) {
                em.refresh(entity);
            }

            trans.commit();
        }

        return entity;
    }

    @Override
    public void replace(DroolsSession sess) {
        String sessName = sess.getSessionName();

        logger.info("replace: Entering and manually updating session name= {}", sessName);

        EntityManager em = emf.createEntityManager();

        try (EntityMgrTrans trans = new EntityMgrTrans(em)) {

            if (!update(em, sess)) {
                add(em, sess);
            }

            trans.commit();
        }

        logger.info("replace: Exiting");
    }

    /**
     * Adds a session to the persistent store.
     *
     * @param em entity manager
     * @param sess session to be added
     */
    private void add(EntityManager em, DroolsSession sess) {
        logger.info("add: Inserting session id={}", sess.getSessionId());

        DroolsSessionEntity ent = new DroolsSessionEntity(sess.getSessionName(), sess.getSessionId());

        em.persist(ent);
    }

    /**
     * Updates a session, if it exists within the persistent store.
     *
     * @param em entity manager
     * @param sess session data to be persisted
     * @return {@code true} if a record was updated, {@code false} if it was not found
     */
    private boolean update(EntityManager em, DroolsSession sess) {

        DroolsSessionEntity session = em.find(DroolsSessionEntity.class, sess.getSessionName());
        if (session == null) {
            return false;
        }

        logger.info("update: Updating session id to {}", sess.getSessionId());
        session.setSessionId(sess.getSessionId());

        return true;
    }
}
