/*
 * ============LICENSE_START=======================================================
 * feature-active-standby-management
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

package org.onap.policy.drools.activestandby;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import org.onap.policy.common.im.MonitorTime;
import org.onap.policy.common.utils.time.CurrentTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JpaDroolsPdpsConnector implements DroolsPdpsConnector {

    private static final String SELECT_PDP_BY_ID = "SELECT p FROM DroolsPdpEntity p WHERE p.pdpId=:pdpId";
    private static final String PDP_ID_PARAM = "pdpId";

    // get an instance of logger
    private static final Logger  logger = LoggerFactory.getLogger(JpaDroolsPdpsConnector.class);
    private EntityManagerFactory emf;

    private final CurrentTime currentTime = MonitorTime.getInstance();


    //not sure if we want to use the same entity manager factory
    //for drools session and pass it in here, or create a new one
    public JpaDroolsPdpsConnector(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public Collection<DroolsPdp> getDroolsPdps() {
        //return a list of all the DroolsPdps in the database
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Query droolsPdpsListQuery = em.createQuery("SELECT p FROM DroolsPdpEntity p");
            List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(LockModeType.NONE)
                    .setFlushMode(FlushModeType.COMMIT).getResultList();
            LinkedList<DroolsPdp> droolsPdpsReturnList = new LinkedList<>();
            for (Object o : droolsPdpsList) {
                if (!(o instanceof DroolsPdp)) {
                    continue;
                }
                //Make sure it is not a cached version
                DroolsPdp droolsPdp = (DroolsPdp)o;
                em.refresh(droolsPdp);
                droolsPdpsReturnList.add(droolsPdp);
                if (logger.isDebugEnabled()) {
                    logger.debug("getDroolsPdps: PDP= {}"
                            + ", isDesignated= {}"
                            + ", updatedDate= {}"
                            + ", priority= {}", droolsPdp.getPdpId(), droolsPdp.isDesignated(),
                            droolsPdp.getUpdatedDate(), droolsPdp.getPriority());
                }
            }
            try {
                em.getTransaction().commit();
            } catch (Exception e) {
                logger.error("Cannot commit getDroolsPdps() transaction", e);
            }
            return droolsPdpsReturnList;
        } finally {
            cleanup(em, "getDroolsPdps");
        }
    }

    private boolean nullSafeEquals(Object one, Object two) {
        if (one == null && two == null) {
            return true;
        }
        if (one != null && two != null) {
            return one.equals(two);
        }
        return false;
    }

    @Override
    public void update(DroolsPdp pdp) {

        logger.debug("update: Entering, pdpId={}", pdp.getPdpId());

        //this is to update our own pdp in the database
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Query droolsPdpsListQuery = em.createQuery(SELECT_PDP_BY_ID);
            droolsPdpsListQuery.setParameter(PDP_ID_PARAM, pdp.getPdpId());
            List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(LockModeType.NONE)
                    .setFlushMode(FlushModeType.COMMIT).getResultList();
            DroolsPdpEntity droolsPdpEntity;
            if (droolsPdpsList.size() == 1 && (droolsPdpsList.get(0) instanceof DroolsPdpEntity)) {
                droolsPdpEntity = (DroolsPdpEntity)droolsPdpsList.get(0);
                em.refresh(droolsPdpEntity); //Make sure we have current values
                Date currentDate = currentTime.getDate();
                long difference = currentDate.getTime() - droolsPdpEntity.getUpdatedDate().getTime();
                //just set some kind of default here
                long pdpTimeout = 15000;
                try {
                    pdpTimeout = Long.parseLong(
                            ActiveStandbyProperties.getProperty(ActiveStandbyProperties.PDP_TIMEOUT));
                } catch (Exception e) {
                    logger.error("Could not get PDP timeout property, using default.", e);
                }
                boolean isCurrent = difference < pdpTimeout;
                logger.debug("update: PDP= {}, isCurrent={}"
                                + " difference= {}"
                                + ", pdpTimeout= {}, designated= {}",
                                pdp.getPdpId(), isCurrent, difference, pdpTimeout, droolsPdpEntity.isDesignated());
            } else {
                logger.debug("update: For PDP={}"
                                + ", instantiating new DroolsPdpEntity", pdp.getPdpId());
                droolsPdpEntity = new DroolsPdpEntity();
                em.persist(droolsPdpEntity);
                droolsPdpEntity.setPdpId(pdp.getPdpId());
            }
            if (droolsPdpEntity.getPriority() != pdp.getPriority()) {
                droolsPdpEntity.setPriority(pdp.getPriority());
            }
            if (!droolsPdpEntity.getUpdatedDate().equals(pdp.getUpdatedDate())) {
                droolsPdpEntity.setUpdatedDate(pdp.getUpdatedDate());
            }
            if (!nullSafeEquals(droolsPdpEntity.getSite(),pdp.getSite())) {
                droolsPdpEntity.setSite(pdp.getSite());
            }

            if (droolsPdpEntity.isDesignated() != pdp.isDesignated()) {
                logger.debug("update: pdpId={}"
                                + ", pdp.isDesignated={}"
                                + ", droolsPdpEntity.pdpId= {}"
                                + ", droolsPdpEntity.isDesignated={}",
                                pdp.getPdpId(), pdp.isDesignated(),
                                droolsPdpEntity.getPdpId(), droolsPdpEntity.isDesignated());
                droolsPdpEntity.setDesignated(pdp.isDesignated());
                //The isDesignated value is not the same and the new one == true
                if (pdp.isDesignated()) {
                    droolsPdpEntity.setDesignatedDate(currentTime.getDate());
                }
            }
            em.getTransaction().commit();
        } finally {
            cleanup(em, "update");
        }

        logger.debug("update: Exiting");

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
        try {
            if (!isCurrent && pdp.isDesignated()) {
                em.getTransaction().begin();
                Query droolsPdpsListQuery = em.createQuery(SELECT_PDP_BY_ID);
                droolsPdpsListQuery.setParameter(PDP_ID_PARAM, pdp.getPdpId());
                List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(LockModeType.NONE)
                        .setFlushMode(FlushModeType.COMMIT).getResultList();
                if (droolsPdpsList.size() == 1 && droolsPdpsList.get(0) instanceof DroolsPdpEntity) {
                    logger.debug("isPdpCurrent: PDP={}  designated but not current; setting designated to false",
                                    pdp.getPdpId());
                    DroolsPdpEntity droolsPdpEntity = (DroolsPdpEntity)droolsPdpsList.get(0);
                    droolsPdpEntity.setDesignated(false);
                    em.getTransaction().commit();
                } else {
                    logger.warn("isPdpCurrent: PDP={} is designated but not current; "
                            + "however it does not have a DB entry, so cannot set DESIGNATED to false!",
                            pdp.getPdpId());
                }
            } else {
                logger.debug("isPdpCurrent: For PDP= {}, "
                                + "designated={}, isCurrent={}", pdp.getPdpId(), pdp.isDesignated(), isCurrent);
            }
        } catch (Exception e) {
            logger.error("Could not update expired record marked as designated in the database", e);
        } finally {
            cleanup(em, "isPdpCurrent");
        }
        return isCurrent;

    }

    @Override
    public void setDesignated(DroolsPdp pdp, boolean designated) {

        logger.debug("setDesignated: Entering, pdpId={}"
                + ", designated={}", pdp.getPdpId(), designated);

        EntityManager em = null;
        try {
            em = emf.createEntityManager();
            em.getTransaction().begin();
            Query droolsPdpsListQuery = em
                    .createQuery(SELECT_PDP_BY_ID);
            droolsPdpsListQuery.setParameter(PDP_ID_PARAM, pdp.getPdpId());
            List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(
                    LockModeType.NONE).setFlushMode(FlushModeType.COMMIT).getResultList();
            if (droolsPdpsList.size() == 1
                    && droolsPdpsList.get(0) instanceof DroolsPdpEntity) {
                DroolsPdpEntity droolsPdpEntity = (DroolsPdpEntity) droolsPdpsList
                        .get(0);

                logger.debug("setDesignated: PDP={}"
                        + " found, designated= {}"
                        + ", setting to {}", pdp.getPdpId(), droolsPdpEntity.isDesignated(),
                        designated);
                setPdpDesignation(em, droolsPdpEntity, designated);
                em.getTransaction().commit();
            } else {
                logger.error("setDesignated: PDP={}"
                        + " not in DB; cannot update designation", pdp.getPdpId());
            }
        } catch (Exception e) {
            logger.error("setDesignated: Caught Exception", e);
        } finally {
            cleanup(em, "setDesignated");
        }

        logger.debug("setDesignated: Exiting");

    }

    private void setPdpDesignation(EntityManager em, DroolsPdpEntity droolsPdpEntity, boolean designated) {
        droolsPdpEntity.setDesignated(designated);
        if (designated) {
            em.refresh(droolsPdpEntity); //make sure we get the DB value
            if (!droolsPdpEntity.isDesignated()) {
                droolsPdpEntity.setDesignatedDate(currentTime.getDate());
            }

        }
    }


    @Override
    public void standDownPdp(String pdpId) {
        logger.debug("standDownPdp: Entering, pdpId={}", pdpId);

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
                    .createQuery(SELECT_PDP_BY_ID);
            droolsPdpsListQuery.setParameter(PDP_ID_PARAM, pdpId);
            List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(
                    LockModeType.NONE).setFlushMode(FlushModeType.COMMIT).getResultList();
            DroolsPdpEntity droolsPdpEntity;
            if (droolsPdpsList.size() == 1
                    && (droolsPdpsList.get(0) instanceof DroolsPdpEntity)) {
                droolsPdpEntity = (DroolsPdpEntity) droolsPdpsList.get(0);
                droolsPdpEntity.setDesignated(false);
                em.persist(droolsPdpEntity);
                logger.debug("standDownPdp: PDP={} persisted as non-designated.", pdpId );
            } else {
                logger.error("standDownPdp: Missing record in droolspdpentity for pdpId={}"
                        + "; cannot stand down PDP", pdpId);
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
            logger.error("standDownPdp: Unexpected Exception attempting to mark "
                    + "DESIGNATED as false for droolspdpentity, pdpId={}"
                    + ".  Cannot stand down PDP; message={}", pdpId, e.getMessage(), e);
        } finally {
            cleanup(em, "standDownPdp");
        }
        logger.debug("standDownPdp: Exiting");

    }

    /*
     * Determines whether or not a designated PDP has failed.
     *
     * Note: The update method, which is run periodically by the
     * TimerUpdateClass, will un-designate a PDP that is stale.
     */
    @Override
    public boolean hasDesignatedPdpFailed(Collection<DroolsPdp> pdps) {

        logger.debug("hasDesignatedPdpFailed: Entering, pdps.size()={}", pdps.size());

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
                logger.debug("hasDesignatedPdpFailed: Designated PDP={} is current", pdp.getPdpId());
                failed = false;
                foundDesignatedPdp = true;
            } else if (pdp.isDesignated() && !isCurrent(pdp)) {
                logger.error("hasDesignatedPdpFailed: Designated PDP={} has failed", pdp.getPdpId());
                foundDesignatedPdp = true;
            } else {
                logger.debug("hasDesignatedPdpFailed: PDP={} is not designated", pdp.getPdpId());
            }
        }

        logger.debug("hasDesignatedPdpFailed: Exiting and returning, foundDesignatedPdp={}",
                foundDesignatedPdp);
        return failed;
    }


    private boolean isCurrent(DroolsPdp pdp) {

        logger.debug("isCurrent: Entering, pdpId={}", pdp.getPdpId());

        boolean current = false;

        // Return if the current PDP is considered "current" based on whatever
        // time box that may be.
        // If the the PDP is not current, we should mark it as not primary in
        // the database
        Date currentDate = currentTime.getDate();
        long difference = currentDate.getTime()
                - pdp.getUpdatedDate().getTime();
        // just set some kind of default here
        long pdpTimeout = 15000;
        try {
            pdpTimeout = Long.parseLong(ActiveStandbyProperties
                    .getProperty(ActiveStandbyProperties.PDP_TIMEOUT));
            logger.debug("isCurrent: pdp.timeout={}", pdpTimeout);
        } catch (Exception e) {
            logger.error("isCurrent: Could not get PDP timeout property, using default.", e);
        }
        current = difference < pdpTimeout;

        logger.debug("isCurrent: Exiting, difference={}, pdpTimeout={}"
                + "; returning current={}", difference, pdpTimeout, current);

        return current;
    }


    /*
     * Currently this method is only used in a JUnit test environment. Gets a
     * PDP record from droolspdpentity table.
     */
    @Override
    public DroolsPdpEntity getPdp(String pdpId) {

        logger.debug("getPdp: Entering and getting PDP with pdpId={}", pdpId);

        DroolsPdpEntity droolsPdpEntity = null;

        EntityManager em = null;
        try {
            em = emf.createEntityManager();
            em.getTransaction().begin();
            Query droolsPdpsListQuery = em
                    .createQuery(SELECT_PDP_BY_ID);
            droolsPdpsListQuery.setParameter(PDP_ID_PARAM, pdpId);
            List<?> droolsPdpsList = droolsPdpsListQuery.setLockMode(
                    LockModeType.NONE).setFlushMode(FlushModeType.COMMIT).getResultList();
            if (droolsPdpsList.size() == 1
                    && droolsPdpsList.get(0) instanceof DroolsPdpEntity) {
                droolsPdpEntity = (DroolsPdpEntity) droolsPdpsList.get(0);
                logger.debug("getPdp: PDP={}"
                                + " found, isDesignated={},"
                                + " updatedDate={}, "
                                + "priority={}", pdpId,
                                droolsPdpEntity.isDesignated(), droolsPdpEntity.getUpdatedDate(),
                                droolsPdpEntity.getPriority());

                // Make sure the droolsPdpEntity is not a cached version
                em.refresh(droolsPdpEntity);

                em.getTransaction().commit();
            } else {
                logger.error("getPdp: PDP={} not found!?", pdpId);
            }
        } catch (Exception e) {
            logger.error("getPdp: Caught Exception attempting to get PDP", e);
        } finally {
            cleanup(em, "getPdp");
        }

        logger.debug("getPdp: Returning droolsPdpEntity={}", droolsPdpEntity);
        return droolsPdpEntity;

    }

    /*
     * Normally this method should only be used in a JUnit test environment.
     * Manually inserts a PDP record in droolspdpentity table.
     */
    @Override
    public void insertPdp(DroolsPdp pdp) {
        logger.debug("insertPdp: Entering and manually inserting PDP");

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
            droolsPdpEntity.setSite(pdp.getSite());

            /*
             * End transaction.
             */
            em.getTransaction().commit();
        } finally {
            cleanup(em, "insertPdp");
        }
        logger.debug("insertPdp: Exiting");

    }

    /*
     * Normally this method should only be used in a JUnit test environment.
     * Manually deletes all PDP records in droolspdpentity table.
     */
    @Override
    public void deleteAllPdps() {

        logger.debug("deleteAllPdps: Entering");

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
                    LockModeType.NONE).setFlushMode(FlushModeType.COMMIT).getResultList();
            logger.debug("deleteAllPdps: Deleting {} PDPs", droolsPdpsList.size());
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
        logger.debug("deleteAllPdps: Exiting");

    }

    /*
     * Normally this method should only be used in a JUnit test environment.
     * Manually deletes a PDP record in droolspdpentity table.
     */
    @Override
    public void deletePdp(String pdpId) {
        logger.debug("deletePdp: Entering and manually deleting pdpId={}", pdpId);

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
                logger.debug("deletePdp: Removing PDP");
                em.remove(droolsPdpEntity);
            } else {
                logger.debug("deletePdp: PDP with ID={} not currently in DB", pdpId);
            }

            /*
             * End transaction.
             */
            em.getTransaction().commit();
        } finally {
            cleanup(em, "deletePdp");
        }
        logger.debug("deletePdp: Exiting");

    }

    /*
     * Close the specified EntityManager, rolling back any pending transaction
     *
     * @param em the EntityManager to close ('null' is OK)
     * @param method the invoking Java method (used for log messages)
     */
    private static void cleanup(EntityManager em, String method) {
        if (em != null && em.isOpen()) {
            if (em.getTransaction().isActive()) {
                // there is an active EntityTransaction -- roll it back
                try {
                    em.getTransaction().rollback();
                } catch (Exception e) {
                    logger.error("{}: Caught Exception attempting to rollback EntityTransaction", method, e);
                }
            }

            // now, close the EntityManager
            try {
                em.close();
            } catch (Exception e) {
                logger.error("{}: Caught Exception attempting to close EntityManager", method, e);
            }
        }
    }
}
