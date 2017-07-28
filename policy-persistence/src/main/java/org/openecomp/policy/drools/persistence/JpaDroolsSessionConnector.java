/*-
 * ============LICENSE_START=======================================================
 * policy-persistence
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

package org.openecomp.policy.drools.persistence;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;


public class JpaDroolsSessionConnector implements DroolsSessionConnector {

	private static final Logger logger =
				FlexLogger.getLogger(JpaDroolsSessionConnector.class);
	
	private final EntityManagerFactory emf;
	
	
	public JpaDroolsSessionConnector(EntityManagerFactory emf) {
		this.emf = emf;
	}

	@Override
	public DroolsSession get(String sessName) {

		EntityManager em = emf.createEntityManager();
		DroolsSessionEntity s = null;
		
		try(EntityMgrTrans trans = new EntityMgrTrans(em)) {
			
			s = em.find(DroolsSessionEntity.class, sessName);
			if(s != null) {
				em.refresh(s);
			}

			trans.commit();
		}

		return s;
	}

	@Override
	public void replace(DroolsSession sess) {
		String sessName = sess.getSessionName();
		
		logger.info("replace: Entering and manually updating session name=" + sessName);
		
		EntityManager em = emf.createEntityManager();
		
		try(EntityMgrTrans trans = new EntityMgrTrans(em)) {
			
			if( ! update(em, sess)) {
				add(em, sess);
			}
		
			trans.commit();
		}
		
		logger.info("replace: Exiting");
	}

	/**
	 * Adds a session to the persistent store.
	 * @param em	entity manager
	 * @param sess	session to be added
	 */
	private void add(EntityManager em, DroolsSession sess) {
		logger.info("add: Inserting session id=" + sess.getSessionId());

		DroolsSessionEntity ent =
				new DroolsSessionEntity(
						sess.getSessionName(),
						sess.getSessionId());
		
		em.persist(ent);
	}
	
	/**
	 * Updates a session, if it exists within the persistent store.
	 * @param em	entity manager
	 * @param sess	session data to be persisted
	 * @return {@code true} if a record was updated, {@code false} if it
	 * 			was not found
	 */
	private boolean update(EntityManager em, DroolsSession sess) {
		
		DroolsSessionEntity s =
				em.find(DroolsSessionEntity.class, sess.getSessionName());
		if(s == null) {
			return false;
		}

		logger.info("update: Updating session id to " + sess.getSessionId());
		s.setSessionId( sess.getSessionId());
		
		return true;
	}

//	@Override
//	public void deleteIfMissingSessionInfo() {
//		logger.info("deleteIfMissingSessionInfo: Entering");
//		
//		EntityManager em = emf.createEntityManager();
//		
//		try(EntityMgrTrans trans = new EntityMgrTrans(em)) {
//			
//			int ndel = em
//						.createQuery("DELETE FROM DroolsSessionEntity WHERE sessionId not in "
//										+ "(SELECT id from SessionInfo)")
//						.setFlushMode(FlushModeType.COMMIT)
//                        .executeUpdate();
//
//			logger.info("Cleaning up DroolsSessionEntity table -- " + ndel + " records removed");
//		
//			trans.commit();
//		}
//		
//		logger.info("deleteIfMissingSessionInfo: Exiting");
//	}

}
