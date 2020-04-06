/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2020 AT&T Intellectual Property. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Semaphore;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.KieSession;
import org.onap.policy.drools.core.PolicySession.ThreadModel;

public class PolicySessionTest {

    private static final String MY_NAME = "my-name";
    private static final String CONTAINER = "my-container";
    private static final String EXPECTED = null;

    private PolicySession session;
    private PolicyContainer container;
    private KieSession kie;

    /**
     * Initialize test objects.
     */
    @Before
    public void setUp() {
        container = mock(PolicyContainer.class);
        kie = mock(KieSession.class);

        when(container.getName()).thenReturn(CONTAINER);

        session = new PolicySession(MY_NAME, container, kie);
    }

    @Test
    public void test_Simple() {
        // verify constructor operations
        AgendaEventListener agenda = session;
        verify(kie).addEventListener(agenda);

        RuleRuntimeEventListener rule = session;
        verify(kie).addEventListener(rule);

        // test other simple methods
        assertEquals(container, session.getPolicyContainer());
        assertEquals(kie, session.getKieSession());
        assertEquals(MY_NAME, session.getName());
        assertEquals(CONTAINER + ":" + MY_NAME, session.getFullName());

        session.stopThread();
        session.updated();

        session.afterRuleFlowGroupActivated(null);
        session.afterRuleFlowGroupDeactivated(null);
        session.agendaGroupPopped(null);
        session.agendaGroupPushed(null);
        session.beforeMatchFired(null);
        session.beforeRuleFlowGroupActivated(null);
        session.beforeRuleFlowGroupDeactivated(null);
        session.matchCancelled(null);
        session.matchCreated(null);
        session.objectDeleted(null);
        session.objectInserted(null);
        session.objectUpdated(null);
    }

    @Test
    public void testStartThread() {
        session.startThread();

        // re-start
        session.startThread();

        assertThatCode(() -> session.stopThread()).doesNotThrowAnyException();
    }

    @Test
    public void testSetPolicySession_testGetCurrentSession() {
        PolicySession sess2 = new PolicySession(MY_NAME + "-b", container, kie);

        session.setPolicySession();
        assertEquals(session, PolicySession.getCurrentSession());

        sess2.setPolicySession();
        assertEquals(sess2, PolicySession.getCurrentSession());
    }

    @Test
    public void testGetAdjunct_testSetAdjunct() {
        Object adjnm1 = "adjunct-a";
        Object adjval1 = "value-a";
        session.setAdjunct(adjnm1, adjval1);

        Object adjnm2 = "adjunct-b";
        Object adjval2 = "value-b";
        session.setAdjunct(adjnm2, adjval2);

        assertEquals(adjval1, session.getAdjunct(adjnm1));
        assertEquals(adjval2, session.getAdjunct(adjnm2));
        assertNull(session.getAdjunct("unknown-adjunct"));
    }

    @Test
    public void testThreadModel() {
        ThreadModel model = new PolicySession.ThreadModel() {
            @Override
            public void stop() {
                // do nothing
            }

            @Override
            public void start() {
                // do nothing
            }
        };

        assertThatCode(() -> model.updated()).doesNotThrowAnyException();
    }

    @Test
    public void testDefaultThreadModelRun() throws Exception {
        testDefaultThreadModelRun_Ex(() -> {
            throw new RuntimeException(EXPECTED);
        });
        testDefaultThreadModelRun_Ex(() -> {
            throw new LinkageError(EXPECTED);
        });
    }

    /**
     * Starts a thread and then invokes a function to generate an exception within the
     * fireUntilHalt() method.
     *
     * @param genEx function to generate an exception
     * @throws Exception if an error occurs
     */
    private void testDefaultThreadModelRun_Ex(Runnable genEx) throws Exception {
        Semaphore me = new Semaphore(0);
        Semaphore thread = new Semaphore(0);

        doAnswer(args -> {
            // let me know the thread has started
            me.release(1);

            // wait until I tell it to continue
            thread.acquire();

            // generate the exception
            genEx.run();

            // never reaches here
            return null;

        }).when(kie).fireUntilHalt();

        session.startThread();

        me.acquire();
        thread.release();

        session.stopThread();
    }

}
