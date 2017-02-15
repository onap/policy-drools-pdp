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

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.LockModeType;
import javax.persistence.Query;

import org.openecomp.policy.drools.core.IntegrityMonitorProperties;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.common.logging.eelf.MessageCodes;


public class JpaDroolsPdpsConnector implements DroolsPdpsConnector {

	// get an instance of logger 
	private static Logger  logger = FlexLogger.getLogger(JpaDroolsPdpsConnector.class);
	private EntityManagerFactory emf;
		
	
	//not sure if we want to use the same entity manager factory for drools session and pass it in here, or create a new one
	public JpaDroolsPdpsConnector(EntityManagerFactory emf){
		this.emf = emf;		
	}
	@Override
	public Collection<DroolsPdp> getDroolsPdps() {
		//return a list of all the DroolsPdps in the database
		EntityManager em = emf.createEntityManager();
		try {
			em.getTransaction().begin();
			Query droolsPdpsListQuery = em.createQuery("SELECT p FROM DroolsPdpEntity p");
			List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(LockModeType.PESSIMISTIC_READ).getResultList();		
			LinkedList<DroolsPdp> droolsPdpsReturnList = new LinkedList<DroolsPdp>();
			for(Object o : droolsPdpsList){
				if(o instanceof DroolsPdp){
					//Make sure it is not a cached version
					em.refresh((DroolsPdpEntity)o);
					droolsPdpsReturnList.add((DroolsPdp)o);
					if (logger.isDebugEnabled()) {
						DroolsPdp droolsPdp = (DroolsPdp)o;
						logger.debug("getDroolsPdps: PDP=" + droolsPdp.getPdpId()
								+ ", isDesignated=" + droolsPdp.isDesignated()
								+ ", updatedDate=" + droolsPdp.getUpdatedDate()
								+ ", priority=" + droolsPdp.getPriority());
					}
				}
			}
			try{
			em.getTransaction().commit();
			}catch(Exception e){
				  logger.error
					(MessageCodes.EXCEPTION_ERROR, e,"Cannot commit getDroolsPdps() transaction");
			}
			return droolsPdpsReturnList;
		} finally {
			cleanup(em, "getDroolsPdps");
		}
	}

	private boolean nullSafeEquals(Object one, Object two){
		if(one == null && two == null){
			return true;
		}
		if(one != null && two != null){
			return one.equals(two);
		}
		return false;
	}
	
	@Override
	public void update(DroolsPdp pdp) {
		
		if (logger.isDebugEnabled()) {
			logger.debug("update: Entering, pdpId=" + pdp.getPdpId());
		}
		
		//this is to update our own pdp in the database
		EntityManager em = emf.createEntityManager();
		try {
			em.getTransaction().begin();
			Query droolsPdpsListQuery = em.createQuery("SELECT p FROM DroolsPdpEntity p WHERE p.pdpId=:pdpId");
			droolsPdpsListQuery.setParameter("pdpId", pdp.getPdpId());
			List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
			//em.getTransaction().begin();
			DroolsPdpEntity droolsPdpEntity;
			if(droolsPdpsList.size() == 1 && (droolsPdpsList.get(0) instanceof DroolsPdpEntity)){						
				droolsPdpEntity = (DroolsPdpEntity)droolsPdpsList.get(0);			
				//if(pdp.getSessionId() < 0){
					//if its less than 0, then we know it is not a real session number so we want to save the one that the database has for us, to avoid information loss
					//pdp.setSessionId(droolsPdpEntity.getSessionId());
				//}
				Date currentDate = new Date();
				long difference = currentDate.getTime()-droolsPdpEntity.getUpdatedDate().getTime();
				//just set some kind of default here
				long pdpTimeout = 15000;
				try{
					pdpTimeout = Long.parseLong(IntegrityMonitorProperties.getProperty(IntegrityMonitorProperties.PDP_TIMEOUT));
				}catch(Exception e){
					  logger.error
						(MessageCodes.EXCEPTION_ERROR, e,"Could not get PDP timeout property, using default.");
				}
				boolean isCurrent = difference<pdpTimeout;
				if (logger.isDebugEnabled()) {
					logger.debug("update: PDP=" + pdp.getPdpId() + ", isCurrent="
							+ isCurrent + ", difference=" + difference
							+ ", pdpTimeout=" + pdpTimeout + ", designated="
							+ droolsPdpEntity.isDesignated());
				}
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("update: For PDP=" + pdp.getPdpId()
							+ ", instantiating new DroolsPdpEntity");
				}
				droolsPdpEntity = new DroolsPdpEntity();
				em.persist(droolsPdpEntity);
				droolsPdpEntity.setPdpId(pdp.getPdpId());				
			}
			if(droolsPdpEntity.getPriority() != pdp.getPriority()){
				droolsPdpEntity.setPriority(pdp.getPriority());
			}
			if(!droolsPdpEntity.getUpdatedDate().equals(pdp.getUpdatedDate())){
				droolsPdpEntity.setUpdatedDate(pdp.getUpdatedDate());
			}
			if(!droolsPdpEntity.getDesignatedDate().equals(pdp.getDesignatedDate())){
				droolsPdpEntity.setDesignatedDate(pdp.getDesignatedDate());
			}
			if(!nullSafeEquals(droolsPdpEntity.getSiteName(),pdp.getSiteName())){
				droolsPdpEntity.setSiteName(pdp.getSiteName());
			}
			List<DroolsSessionEntity> sessionsToAdd = new LinkedList<DroolsSessionEntity>();
			for(DroolsSessionEntity localSession : pdp.getSessions()){
				boolean found = false;
				for(DroolsSessionEntity dbSession : droolsPdpEntity.getSessions()){
					if(localSession.equals(dbSession)){
						found = true;
						dbSession.setSessionId(localSession.getSessionId());
					}
				}
				if(!found){
					sessionsToAdd.add(localSession);
				}

			}
			for(DroolsSessionEntity sessionToAdd : sessionsToAdd){
				em.persist(sessionToAdd);
				droolsPdpEntity.getSessions().add(sessionToAdd);
			}		

			
			if(droolsPdpEntity.isDesignated() != pdp.isDesignated()){
				if (logger.isDebugEnabled()) {
					logger.debug("update: pdpId=" + pdp.getPdpId()
							+ ", pdp.isDesignated=" + pdp.isDesignated()
							+ ", droolsPdpEntity.pdpId="
							+ droolsPdpEntity.getPdpId()
							+ ", droolsPdpEntity.isDesignated="
							+ droolsPdpEntity.isDesignated());
				}
				droolsPdpEntity.setDesignated(pdp.isDesignated());
			}
			em.getTransaction().commit();
		} finally {
			cleanup(em, "update");
		}
		
		if (logger.isDebugEnabled()) {
			logger.debug("update: Exiting");
		}

	}

	/*
	 * Note: A side effect of this boolean method is that if the PDP is designated but not current, the 
	 * droolspdpentity.DESIGNATED column will be set to false (the PDP will be un-designated, i.e. marked as
	 * being in standby mode)
	 */
	@Override
	public boolean isPdpCurrent(DroolsPdp pdp) {
		
		boolean isCurrent = isCurrent(pdp);
		
		EntityManager em = emf.createEntityManager();
		try{
		if(!isCurrent && pdp.isDesignated()){
			em.getTransaction().begin();
			Query droolsPdpsListQuery = em.createQuery("SELECT p FROM DroolsPdpEntity p WHERE p.pdpId=:pdpId");
			droolsPdpsListQuery.setParameter("pdpId", pdp.getPdpId());
			List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
			if(droolsPdpsList.size() == 1 && droolsPdpsList.get(0) instanceof DroolsPdpEntity){			
				if (logger.isDebugEnabled()) {
					logger.debug("isPdpCurrent: PDP=" + pdp.getPdpId() + " designated but not current; setting designated to false");
				}
				DroolsPdpEntity droolsPdpEntity = (DroolsPdpEntity)droolsPdpsList.get(0);
				droolsPdpEntity.setDesignated(false);
				em.getTransaction().commit();
			} else {
				logger.warn("isPdpCurrent: PDP=" + pdp.getPdpId() + " is designated but not current; however it does not have a DB entry, so cannot set DESIGNATED to false!");
			}
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("isPdpCurrent: For PDP=" + pdp.getPdpId()
						+ ", designated="
						+ pdp.isDesignated() + ", isCurrent=" + isCurrent);
			}
		}
		}catch(Exception e){
			  logger.error
				(MessageCodes.EXCEPTION_ERROR, e,"Could not update expired record marked as designated in the database");
		} finally {
			cleanup(em, "isPdpCurrent");
		}
		return isCurrent;
		
	}
	
	@Override
	public void setDesignated(DroolsPdp pdp, boolean designated) {

		if (logger.isDebugEnabled()) {
			logger.debug("setDesignated: Entering, pdpId='" + pdp.getPdpId()
					+ "', designated=" + designated);
		}

		EntityManager em = null;
		try {
			em = emf.createEntityManager();
			em.getTransaction().begin();
			Query droolsPdpsListQuery = em
					.createQuery("SELECT p FROM DroolsPdpEntity p WHERE p.pdpId=:pdpId");
			droolsPdpsListQuery.setParameter("pdpId", pdp.getPdpId());
			List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(
					LockModeType.PESSIMISTIC_WRITE).getResultList();
			if (droolsPdpsList.size() == 1
					&& droolsPdpsList.get(0) instanceof DroolsPdpEntity) {
				DroolsPdpEntity droolsPdpEntity = (DroolsPdpEntity) droolsPdpsList
						.get(0);
				if (logger.isDebugEnabled()) {
					logger.debug("setDesignated: PDP=" + pdp.getPdpId()
							+ " found, designated="
							+ droolsPdpEntity.isDesignated() + ", setting to "
							+ designated);
				}
				droolsPdpEntity.setDesignated(designated);
				em.getTransaction().commit();
			} else {
				logger.error("setDesignated: PDP=" + pdp.getPdpId()
						+ " not in DB; cannot update designation");
			}
		} catch (Exception e) {
			logger.error("setDesignated: Caught Exception, message='"
					+ e.getMessage() + "'");
		} finally {
			cleanup(em, "setDesignated");
		}

		if (logger.isDebugEnabled()) {
			logger.debug("setDesignated: Exiting");
		}

	}
	
	
	@Override
	public void standDownPdp(String pdpId) {
		
		logger.info("standDownPdp: Entering, pdpId='" + pdpId + "'");

		EntityManager em = null;
		try {
			/*
			 * Start transaction.
			 */
			em = emf.createEntityManager();
			em.getTransaction().begin();

			/*
			 * Get droolspdpentity record for this PDP and mark DESIGNATED as
			 * false.
			 */
			Query droolsPdpsListQuery = em
					.createQuery("SELECT p FROM DroolsPdpEntity p WHERE p.pdpId=:pdpId");
			droolsPdpsListQuery.setParameter("pdpId", pdpId);
			List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(
					LockModeType.PESSIMISTIC_WRITE).getResultList();
			DroolsPdpEntity droolsPdpEntity;
			if (droolsPdpsList.size() == 1
					&& (droolsPdpsList.get(0) instanceof DroolsPdpEntity)) {
				droolsPdpEntity = (DroolsPdpEntity) droolsPdpsList.get(0);
				droolsPdpEntity.setDesignated(false);
				em.persist(droolsPdpEntity);
				logger.info("standDownPdp: PDP=" + pdpId + " persisted as non-designated.");
			} else {
				logger.error("standDownPdp: Missing record in droolspdpentity for pdpId="
						+ pdpId + "; cannot stand down PDP");
			}

			/*
			 * End transaction.
			 */
			em.getTransaction().commit();
			cleanup(em, "standDownPdp");
			em = null;
			
			// Keep the election handler in sync with the DB
			DroolsPdpsElectionHandler.setMyPdpDesignated(false);

		} catch (Exception e) {
			logger.error("standDownPdp: Unexpected Exception attempting to mark DESIGNATED as false for droolspdpentity, pdpId="
					+ pdpId
					+ ".  Cannot stand down PDP; message="
					+ e.getMessage());
		} finally {
			cleanup(em, "standDownPdp");
		}
		
		logger.info("standDownPdp: Exiting");

	}
	
	/*
	 * Determines whether or not a designated PDP has failed.
	 * 
	 * Note: The update method, which is run periodically by the
	 * TimerUpdateClass, will un-designate a PDP that is stale.
	 */
	@Override
	public boolean hasDesignatedPdpFailed(Collection<DroolsPdp> pdps) {

		if (logger.isDebugEnabled()) {
			logger.debug("hasDesignatedPdpFailed: Entering, pdps.size()="
					+ pdps.size());
		}

		boolean failed = true;
		boolean foundDesignatedPdp = false;

		for (DroolsPdp pdp : pdps) {

			/*
			 * Normally, the update method will un-designate any stale PDP, but
			 * we check here to see if the PDP has gone stale since the update
			 * method was run.
			 * 
			 * Even if we determine that the designated PDP is current, we keep
			 * going (we don't break), so we can get visibility into the other
			 * PDPs, when in DEBUG mode.
			 */
			if (pdp.isDesignated() && isCurrent(pdp)) {
				if (logger.isDebugEnabled()) {
					logger.debug("hasDesignatedPdpFailed: Designated PDP="
							+ pdp.getPdpId() + " is current");
				}
				failed = false;
				foundDesignatedPdp = true;
			} else if (pdp.isDesignated() && !isCurrent(pdp)) {
				logger.error("hasDesignatedPdpFailed: Designated PDP="
						+ pdp.getPdpId() + " has failed");
				foundDesignatedPdp = true;
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("hasDesignatedPdpFailed: PDP="
							+ pdp.getPdpId() + " is not designated");
				}
			}
		}

		if (logger.isDebugEnabled()) {
			logger.debug("hasDesignatedPdpFailed: Exiting and returning, foundDesignatedPdp="
					+ foundDesignatedPdp);
		}
		return failed;
	}
	
	
	private boolean isCurrent(DroolsPdp pdp) {
	
		if (logger.isDebugEnabled()) {
			logger.debug("isCurrent: Entering, pdpId="
					+ pdp.getPdpId());
		}
	
		boolean current = false;
	
		// Return if the current PDP is considered "current" based on whatever
		// time box that may be.
		// If the the PDP is not current, we should mark it as not primary in
		// the database
		Date currentDate = new Date();
		long difference = currentDate.getTime()
				- pdp.getUpdatedDate().getTime();
		// just set some kind of default here
		long pdpTimeout = 15000;
		try {
			pdpTimeout = Long.parseLong(IntegrityMonitorProperties
					.getProperty(IntegrityMonitorProperties.PDP_TIMEOUT));
			if (logger.isDebugEnabled()) {
				logger.debug("isCurrent: pdp.timeout=" + pdpTimeout);
			}		
		} catch (Exception e) {
			  logger.error
				(MessageCodes.EXCEPTION_ERROR, e,
					"isCurrent: Could not get PDP timeout property, using default.");
		}
		current = difference < pdpTimeout;
	
		if (logger.isDebugEnabled()) {
			logger.debug("isCurrent: Exiting, difference="
					+ difference + ", pdpTimeout=" + pdpTimeout
					+ "; returning current=" + current);
		}
	
		return current;
	}
	
	
	/*
	 * Currently this method is only used in a JUnit test environment. Gets a
	 * PDP record from droolspdpentity table.
	 */
	@Override
	public DroolsPdpEntity getPdp(String pdpId) {
	
		if (logger.isDebugEnabled()) {
			logger.debug("getPdp: Entering and getting PDP with pdpId='" + pdpId
					+ "'");
		}
	
		DroolsPdpEntity droolsPdpEntity = null;
	
		EntityManager em = null;
		try {
			em = emf.createEntityManager();
			em.getTransaction().begin();
			Query droolsPdpsListQuery = em
					.createQuery("SELECT p FROM DroolsPdpEntity p WHERE p.pdpId=:pdpId");
			droolsPdpsListQuery.setParameter("pdpId", pdpId);
			List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(
					LockModeType.PESSIMISTIC_WRITE).getResultList();
			if (droolsPdpsList.size() == 1
					&& droolsPdpsList.get(0) instanceof DroolsPdpEntity) {
				droolsPdpEntity = (DroolsPdpEntity) droolsPdpsList.get(0);
				if (logger.isDebugEnabled()) {
					logger.debug("getPdp: PDP=" + pdpId
							+ " found, isDesignated="
							+ droolsPdpEntity.isDesignated() + ", updatedDate="
							+ droolsPdpEntity.getUpdatedDate() + ", priority="
							+ droolsPdpEntity.getPriority());							
				}
				
				// Make sure the droolsPdpEntity is not a cached version
				em.refresh(droolsPdpEntity);
				
				em.getTransaction().commit();
			} else {
				logger.error("getPdp: PDP=" + pdpId + " not found!?");
			}
		} catch (Exception e) {
			  logger.error
				(MessageCodes.EXCEPTION_ERROR, e,"getPdp: Caught Exception attempting to get PDP, message='"
					+ e.getMessage() + "'");
		} finally {
			cleanup(em, "getPdp");
		}
	
		if (logger.isDebugEnabled()) {
			logger.debug("getPdp: Returning droolsPdpEntity=" + droolsPdpEntity);
		}
		return droolsPdpEntity;
	
	}
	
	/*
	 * Normally this method should only be used in a JUnit test environment.
	 * Manually inserts a PDP record in droolspdpentity table.
	 */
	@Override
	public void insertPdp(DroolsPdp pdp) {

		logger.info("insertPdp: Entering and manually inserting PDP");

		/*
		 * Start transaction
		 */
		EntityManager em = emf.createEntityManager();
		try {
			em.getTransaction().begin();

			/*
			 * Insert record.
			 */
			DroolsPdpEntity droolsPdpEntity = new DroolsPdpEntity();
			em.persist(droolsPdpEntity);
			droolsPdpEntity.setPdpId(pdp.getPdpId());
			droolsPdpEntity.setDesignated(pdp.isDesignated());
			droolsPdpEntity.setPriority(pdp.getPriority());
			droolsPdpEntity.setUpdatedDate(pdp.getUpdatedDate());
			droolsPdpEntity.setSiteName(pdp.getSiteName());

			/*
			 * End transaction.
			 */
			em.getTransaction().commit();
		} finally {
			cleanup(em, "insertPdp");
		}

		logger.info("insertPdp: Exiting");

	}
	
	/*
	 * Normally this method should only be used in a JUnit test environment.
	 * Manually deletes all PDP records in droolspdpentity table.
	 */
	@Override
	public void deleteAllPdps() {
	
		logger.info("deleteAllPdps: Entering");
	
		/*
		 * Start transaction
		 */
		EntityManager em = emf.createEntityManager();
		try {
			em.getTransaction().begin();
	
			Query droolsPdpsListQuery = em
					.createQuery("SELECT p FROM DroolsPdpEntity p");
			@SuppressWarnings("unchecked")
			List<DroolsPdp> droolsPdpsList = droolsPdpsListQuery.setLockMode(
					LockModeType.NONE).getResultList();
			logger.info("deleteAllPdps: Deleting " + droolsPdpsList.size() + " PDPs");
			for (DroolsPdp droolsPdp : droolsPdpsList) {
				String pdpId = droolsPdp.getPdpId();
				deletePdp(pdpId);
			}
	
			/*
			 * End transaction.
			 */
			em.getTransaction().commit();
		} finally {
			cleanup(em, "deleteAllPdps");
		}
		
		logger.info("deleteAllPdps: Exiting");
	
	}
	
	/*
	 * Normally this method should only be used in a JUnit test environment.
	 * Manually deletes a PDP record in droolspdpentity table.
	 */
	@Override
	public void deletePdp(String pdpId) {
	
		logger.info("deletePdp: Entering and manually deleting pdpId='" + pdpId
				+ "'");
	
		/*
		 * Start transaction
		 */
		EntityManager em = emf.createEntityManager();
		try {
			em.getTransaction().begin();
		
			/*
			 * Delete record.
			 */
			DroolsPdpEntity droolsPdpEntity = em.find(DroolsPdpEntity.class, pdpId);
			if (droolsPdpEntity != null) {
				logger.info("deletePdp: Removing PDP");
				em.remove(droolsPdpEntity);
			} else {
				logger.info("deletePdp: PDP with ID='" + pdpId
						+ "' not currently in DB");
			}

			/*
			 * End transaction.
			 */
			em.getTransaction().commit();
		} finally {
			cleanup(em, "deletePdp");
		}
	
		logger.info("deletePdp: Exiting");
	
	}
	
	/*
	 * Normally this method should only be used in a JUnit test environment.
	 * Manually deletes all records in droolsessionentity table.
	 */
	@Override
	public void deleteAllSessions() {

		logger.info("deleteAllSessions: Entering");

		/*
		 * Start transaction
		 */
		EntityManager em = emf.createEntityManager();

		try {
			em.getTransaction().begin();

			Query droolsSessionListQuery = em
					.createQuery("SELECT p FROM DroolsSessionEntity p");
			@SuppressWarnings("unchecked")
			List<DroolsSession> droolsSessionsList = droolsSessionListQuery.setLockMode(
					LockModeType.NONE).getResultList();
			logger.info("deleteAllSessions: Deleting " + droolsSessionsList.size() + " Sessions");
			for (DroolsSession droolsSession : droolsSessionsList) {
				logger.info("deleteAllSessions: Deleting droolsSession with pdpId="
						+ droolsSession.getPdpId() + " and sessionId="
						+ droolsSession.getSessionId());
				em.remove(droolsSession);
			}

			/*
			 * End transaction.
			 */
			em.getTransaction().commit();
		} finally {
			cleanup(em, "deleteAllSessions");
		}		
		logger.info("deleteAllSessions: Exiting");

	}
	
	
	/*
	 * Close the specified EntityManager, rolling back any pending transaction
	 *
	 * @param em the EntityManager to close ('null' is OK)
	 * @param method the invoking Java method (used for log messages)
	 */
	private static void cleanup(EntityManager em, String method)
	{
		if (em != null) {
			if (em.isOpen()) {
				if (em.getTransaction().isActive()) {
					// there is an active EntityTransaction -- roll it back
					try {
						em.getTransaction().rollback();
					} catch (Exception e) {
						logger.error(method + ": Caught Exception attempting to rollback EntityTransaction, message='"
										   + e.getMessage() + "'");
					}
				}

				// now, close the EntityManager
				try {
					em.close();
				} catch (Exception e) {
					logger.error(method + ": Caught Exception attempting to close EntityManager, message='"
									   + e.getMessage() + "'");
				}
			}
		}
	}
}
