/*
 * ============LICENSE_START=======================================================
 * policy-core
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2018 Samsung Electronics Co., Ltd.
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

import java.util.concurrent.ConcurrentHashMap;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.AgendaGroupPoppedEvent;
import org.kie.api.event.rule.AgendaGroupPushedEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.KieSession;
import org.onap.policy.drools.core.jmx.PdpJmx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class is a wrapper around 'KieSession', which adds the following:
 *
 * <p>1) A thread running 'KieSession.fireUntilHalt()'
 * 2) Access to UEB
 * 3) Logging of events
 */
public class PolicySession
        implements AgendaEventListener, RuleRuntimeEventListener {
    // get an instance of logger
    private static Logger logger = LoggerFactory.getLogger(PolicySession.class);
    // name of the 'PolicySession' and associated 'KieSession'
    private String name;

    // the associated 'PolicyContainer', which may have additional
    // 'PolicySession' instances in addition to this one
    private PolicyContainer container;

    // maps feature objects to per-PolicyContainer data
    private ConcurrentHashMap<Object, Object> adjuncts =
            new ConcurrentHashMap<>();

    // associated 'KieSession' instance
    private KieSession kieSession;

    // if not 'null', this is the thread model processing the 'KieSession'
    private ThreadModel threadModel = null;

    // supports 'getCurrentSession()' method
    private static ThreadLocal<PolicySession> policySess =
            new ThreadLocal<>();

    /**
     * Internal constructor - create a 'PolicySession' instance.
     *
     * @param name       the name of this 'PolicySession' (and 'kieSession')
     * @param container  the 'PolicyContainer' instance containing this session
     * @param kieSession the associated 'KieSession' instance
     */
    protected PolicySession(String name,
                            PolicyContainer container, KieSession kieSession) {
        this.name = name;
        this.container = container;
        this.kieSession = kieSession;
        kieSession.addEventListener((AgendaEventListener) this);
        kieSession.addEventListener((RuleRuntimeEventListener) this);
    }

    /**
     * Get policy container.
     *
     * @return the 'PolicyContainer' object containing this session
     */
    public PolicyContainer getPolicyContainer() {
        return container;
    }

    /**
     * Get Kie Session.
     *
     * @return the associated 'KieSession' instance
     */
    public KieSession getKieSession() {
        return kieSession;
    }

    /**
     * Get name.
     *
     * @return the local name of this session, which should either match the
     *     name specified in 'kmodule.xml' file associated with this session, or the
     *     name passed on the 'PolicyContainer.adoptKieSession' method.
     */
    public String getName() {
        return name;
    }

    /**
     * Get full name.
     *
     * @return the 'PolicyContainer' name, followed by ':', followed by the
     *     local name of the session. It should be useful in log messages.
     */
    public String getFullName() {
        return container.getName() + ":" + name;
    }

    /**
     * If no 'ThreadModel' is currently running, this method will create one,
     * and invoke it's 'start()' method. Features implementing
     * 'PolicySessionFeatureAPI.selectThreadModel(...)' get a chance to create
     * the ThreadModel instance.
     */
    public synchronized void startThread() {
        if (threadModel != null) {
            return;
        }

        // loop through all of the features, and give each one
        // a chance to create the 'ThreadModel'
        for (PolicySessionFeatureApi feature :
                PolicySessionFeatureApiConstants.getImpl().getList()) {
            try {
                if ((threadModel = feature.selectThreadModel(this)) != null) {
                    break;
                }
            } catch (Exception e) {
                logger.error("ERROR: Feature API: {}", feature.getClass().getName(), e);
            }
        }
        if (threadModel == null) {
            // no feature created a ThreadModel -- select the default
            threadModel = new DefaultThreadModel(this);
        }
        logger.info("starting ThreadModel for session {}", getFullName());
        threadModel.start();
    }

    /**
     * If a 'ThreadModel' is currently running, this calls the 'stop()' method,
     * and sets the 'threadModel' reference to 'null'.
     */
    public synchronized void stopThread() {
        if (threadModel != null) {
            threadModel.stop();
            threadModel = null;
        }
    }

    /**
     * Notification that 'updateToVersion' was called on the container.
     */
    void updated() {
        if (threadModel != null) {
            // notify the 'ThreadModel', which may change one or more Thread names
            threadModel.updated();
        }
    }

    /**
     * Set this 'PolicySession' instance as the one associated with the
     * currently-running thread.
     */
    public void setPolicySession() {
        // this sets a 'ThreadLocal' variable
        policySess.set(this);
    }

    /**
     * Get current session.
     *
     * @return the 'PolicySession' instance associated with the current thread
     *     (Note that this only works if the current thread is the one running
     *     'kieSession.fireUntilHalt()'.)
     */
    public static PolicySession getCurrentSession() {
        return policySess.get();
    }

    /**
     * Fetch the adjunct object associated with a given feature.
     *
     * @param object this is typically the singleton feature object that is
     *               used as a key, but it might also be useful to use nested objects
     *               within the feature as keys.
     * @return a feature-specific object associated with the key, or 'null'
     *     if it is not found.
     */
    public Object getAdjunct(Object object) {
        return adjuncts.get(object);
    }

    /**
     * Store the adjunct object associated with a given feature.
     *
     * @param object this is typically the singleton feature object that is
     *               used as a key, but it might also be useful to use nested objects
     *               within the feature as keys.
     * @param value  a feature-specific object associated with the key, or 'null'
     *               if the feature-specific object should be removed
     */
    public void setAdjunct(Object object, Object value) {
        if (value == null) {
            adjuncts.remove(object);
        } else {
            adjuncts.put(object, value);
        }
    }

    /**
     * This method will insert an object into the Drools memory associated
     * with this 'PolicySession' instance. Features are given the opportunity
     * to handle the insert, and a distributed host feature could use this to
     * send the object to another host, and insert it in the corresponding
     * Drools session.
     *
     * @param object the object to insert in Drools memory
     */
    public void insertDrools(Object object) {
        for (PolicySessionFeatureApi feature :
                PolicySessionFeatureApiConstants.getImpl().getList()) {
            if (feature.insertDrools(this, object)) {
                // feature is performing the insert
                return;
            }
        }
        // no feature has intervened -- do the insert locally
        if (kieSession != null) {
            kieSession.insert(object);
        }
    }

    /*=================================*/
    /* 'AgendaEventListener' interface */
    /*=================================*/

    /**
     * {@inheritDoc}.
     */
    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        logger.debug("afterMatchFired: {}: AgendaEventListener.afterMatchFired({})", getFullName(), event);
        PdpJmx.getInstance().ruleFired();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
        logger.debug("afterRuleFlowGroupActivated: {}: AgendaEventListener.afterRuleFlowGroupActivated({})",
                        getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
        logger.debug("afterRuleFlowGroupDeactivated: {}: AgendaEventListener.afterRuleFlowGroupDeactivated({})",
                        getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void agendaGroupPopped(AgendaGroupPoppedEvent event) {
        logger.debug("agendaGroupPopped: {}: AgendaEventListener.agendaGroupPopped({})", getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void agendaGroupPushed(AgendaGroupPushedEvent event) {
        logger.debug("agendaGroupPushed: {}: AgendaEventListener.agendaGroupPushed({})", getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        logger.debug("beforeMatchFired: {}: AgendaEventListener.beforeMatchFired({})", getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
        logger.debug("beforeRuleFlowGroupActivated: {}: AgendaEventListener.beforeRuleFlowGroupActivated({})",
                        getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
        logger.debug("beforeRuleFlowGroupDeactivated: {}: AgendaEventListener.beforeRuleFlowGroupDeactivated({})",
                        getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void matchCancelled(MatchCancelledEvent event) {
        logger.debug("matchCancelled: {}: AgendaEventListener.matchCancelled({})", getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void matchCreated(MatchCreatedEvent event) {
        logger.debug("matchCreated: {}: AgendaEventListener.matchCreated({})", getFullName(), event);
    }

    /* ====================================== */
    /* 'RuleRuntimeEventListener' interface */
    /* ====================================== */

    /**
     * {@inheritDoc}.
     */
    @Override
    public void objectDeleted(ObjectDeletedEvent event) {
        logger.debug("objectDeleted: {}: AgendaEventListener.objectDeleted({})", getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void objectInserted(ObjectInsertedEvent event) {
        logger.debug("objectInserted: {}: AgendaEventListener.objectInserted({})", getFullName(), event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void objectUpdated(ObjectUpdatedEvent event) {
        logger.debug("objectUpdated: {}: AgendaEventListener.objectUpdated({})", getFullName(), event);
    }

    /* ============================================================ */

    /**
     * This interface helps support the ability for features to choose the
     * thread or threads that processes the 'KieSession'.
     */
    public interface ThreadModel {
        /**
         * Start the thread or threads that do the 'KieSession' processing.
         */
        void start();

        /**
         * Stop the thread or threads that do the 'KieSession' processing.
         */
        void stop();

        /**
         * This method is called to notify the running session that
         * 'KieContainer.updateToVersion(...)' has been called (meaning the
         * full name of this session has changed).
         */
        default void updated() {
        }
    }

    /* ============================================================ */

    /**
     * This 'ThreadModel' variant uses 'KieSession.fireUntilHalt()'.
     */
    public static class DefaultThreadModel implements Runnable, ThreadModel {
        // session associated with this persistent thread
        PolicySession session;

        // the session thread
        Thread thread;

        // controls whether the thread loops or terminates
        volatile boolean repeat = true;

        /**
         * Constructor - initialize 'session' and create thread.
         *
         * @param session the 'PolicySession' instance
         */
        public DefaultThreadModel(PolicySession session) {
            this.session = session;
            thread = new Thread(this, getThreadName());
        }

        /**
         * Get thread name.
         *
         * @return the String to use as the thread name
         */
        private String getThreadName() {
            return "Session " + session.getFullName();
        }

        /*=========================*/
        /* 'ThreadModel' interface */
        /*=========================*/

        /**
         * {@inheritDoc}.
         */
        @Override
        public void start() {
            repeat = true;
            thread.start();
        }

        /**
         * {@inheritDoc}.
         */
        @Override
        public void stop() {
            repeat = false;

            // this should cause the thread to exit
            session.getKieSession().halt();
            try {
                // wait up to 10 seconds for the thread to stop
                thread.join(10000);

                // one more interrupt, just in case the 'kieSession.halt()'
                // didn't work for some reason
                thread.interrupt();
            } catch (Exception e) {
                logger.error("stopThread in thread.join error", e);
            }
        }

        /**
         * {@inheritDoc}.
         */
        @Override
        public void updated() {
            // the container artifact has been updated -- adjust the thread name
            thread.setName(getThreadName());
        }

        /*======================*/
        /* 'Runnable' interface */
        /*======================*/

        /**
         * {@inheritDoc}.
         */
        @Override
        public void run() {
            // set thread local variable
            session.setPolicySession();

            // We want to continue looping, despite any exceptions that occur
            // while rules are fired.
            KieSession kieSession1 = session.getKieSession();
            while (repeat) {
                try {
                    kieSession1.fireUntilHalt();

                    // if we fall through, it means 'kieSession1.halt()' was called,
                    // but this may be a result of 'KieScanner' doing an update
                } catch (Exception | LinkageError e) {
                    logger.error("startThread error in kieSession1.fireUntilHalt", e);
                }
            }
            logger.info("fireUntilHalt() returned");
        }
    }
}
