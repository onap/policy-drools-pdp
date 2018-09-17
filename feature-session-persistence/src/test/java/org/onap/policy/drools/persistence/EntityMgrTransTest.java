/*
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.persistence.EntityMgrTrans.EntityMgrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityMgrTransTest {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceFeatureTest.class);

    private static UserTransaction savetrans;

    private UserTransaction trans;
    private EntityManager mgr;

    /**
     * Configure properties for JTA.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        System.setProperty("com.arjuna.ats.arjuna.objectstore.objectStoreDir", "target/tm");
        System.setProperty("ObjectStoreEnvironmentBean.objectStoreDir", "target/tm");

        savetrans = EntityMgrTrans.getUserTrans();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        EntityMgrTrans.setUserTrans(savetrans);
    }

    /**
     * Creates a mock transaction and entity manager. Resets the "userTrans" field of the
     * class under test.
     *
     * @throws Exception if an error occurs
     */
    @Before
    public void setUp() throws Exception {
        trans = mock(UserTransaction.class);
        mgr = mock(EntityManager.class);

        when(trans.getStatus()).thenReturn(Status.STATUS_NO_TRANSACTION, Status.STATUS_ACTIVE);

        EntityMgrTrans.setUserTrans(trans);
    }

    /**
     * Verifies that the constructor starts a transaction, but does not do anything extra
     * before being closed.
     *
     * @throws Exception
     */
    @Test
    public void testEntityMgrTrans_Inactive() throws Exception {
        EntityMgrTrans newTrans = new EntityMgrTrans(mgr);

        // verify that transaction was started
        verify(trans).begin();
        verify(mgr).joinTransaction();

        // verify not closed, committed, or rolled back yet
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
        verify(mgr, never()).close();

        newTrans.close();
    }

    /**
     * Verifies that the constructor does not start a transaction, because one is already
     * active.
     *
     * @throws Exception
     */
    @Test
    public void testEntityMgrTrans_Active() throws Exception {

        when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);

        EntityMgrTrans newTrans = new EntityMgrTrans(mgr);

        // verify that transaction was not re-started started
        verify(trans, never()).begin();

        // verify that transaction was joined
        verify(mgr).joinTransaction();

        // verify not closed, committed, or rolled back yet
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
        verify(mgr, never()).close();

        newTrans.close();
    }

    @Test(expected = EntityMgrException.class)
    public void testEntityMgrTrans_RtEx() throws Exception {

        doThrow(new IllegalArgumentException("expected exception")).when(trans).begin();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            // Empty
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testEntityMgrTrans_NotSuppEx() throws Exception {

        doThrow(new NotSupportedException("expected exception")).when(trans).begin();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            // Empty
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testEntityMgrTrans_SysEx() throws Exception {

        doThrow(new SystemException("expected exception")).when(trans).begin();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            // Empty
        }
    }

    /**
     * Verifies that the transaction is not rolled back, but the manager is closed when a
     * transaction is already active.
     */
    @Test
    public void testClose_Active() throws Exception {
        when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);

        EntityMgrTrans newTrans = new EntityMgrTrans(mgr);
        newTrans.close();

        // closed and rolled back, but not committed
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
        verify(mgr).close();
    }

    /**
     * Verifies that the transaction is rolled back and the manager is closed when a
     * transaction is begun by the constructor.
     */
    @Test
    public void testClose_Begun() throws Exception {
        EntityMgrTrans newTrans = new EntityMgrTrans(mgr);

        newTrans.close();

        // closed and rolled back, but not committed
        verify(trans, never()).commit();
        verify(trans).rollback();
        verify(mgr).close();
    }

    /**
     * Verifies that the manager is closed, but that the transaction is <i>not</i> rolled
     * back when no transaction is active.
     */
    @Test
    public void testClose_Inactive() throws Exception {
        when(trans.getStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);

        EntityMgrTrans newTrans = new EntityMgrTrans(mgr);

        newTrans.close();

        // closed, but not committed or rolled back
        verify(mgr).close();
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
    }

    @Test(expected = EntityMgrException.class)
    public void testClose_IllStateEx() throws Exception {

        doThrow(new IllegalStateException("expected exception")).when(trans).rollback();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            // Empty
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testClose_SecEx() throws Exception {

        doThrow(new SecurityException("expected exception")).when(trans).rollback();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            // Empty
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testClose_SysEx() throws Exception {

        doThrow(new SystemException("expected exception")).when(trans).rollback();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            // Empty
        }
    }

    /**
     * Verifies that the manager is closed and the transaction rolled back when "try"
     * block exits normally and a transaction is active.
     */
    @Test
    public void testClose_TryWithoutExcept_Active() throws Exception {
        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            // Empty
        }

        // closed and rolled back, but not committed
        verify(trans, never()).commit();
        verify(trans).rollback();
        verify(mgr).close();
    }

    /**
     * Verifies that the manager is closed, but that the transaction is <i>not</i> rolled
     * back when "try" block exits normally and no transaction is active.
     */
    @Test
    public void testClose_TryWithoutExcept_Inactive() throws Exception {

        when(trans.getStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            // Empty
        }

        // closed, but not rolled back or committed
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
        verify(mgr).close();
    }

    /**
     * Verifies that the manager is closed and the transaction rolled back when "try"
     * block throws an exception and a transaction is active.
     */
    @Test
    public void testClose_TryWithExcept_Active() throws Exception {
        try {
            try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
                throw new SystemException("expected exception");
            }

        } catch (Exception e) {
            logger.trace("expected exception", e);
        }

        // closed and rolled back, but not committed
        verify(trans, never()).commit();
        verify(trans).rollback();
        verify(mgr).close();
    }

    /**
     * Verifies that the manager is closed, but that the transaction is <i>not</i> rolled
     * back when "try" block throws an exception and no transaction is active.
     */
    @Test
    public void testClose_TryWithExcept_Inactive() throws Exception {

        when(trans.getStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);

        try {
            try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
                throw new SystemException("expected exception");
            }

        } catch (Exception e) {
            logger.trace("expected exception", e);
        }

        // closed, but not rolled back or committed
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
        verify(mgr).close();
    }

    /**
     * Verifies that commit() only commits, and that the subsequent close() does not
     * re-commit.
     */
    @Test
    public void testCommit() throws Exception {
        EntityMgrTrans newTrans = new EntityMgrTrans(mgr);

        newTrans.commit();

        when(trans.getStatus()).thenReturn(Status.STATUS_COMMITTED);

        // committed, but not closed or rolled back
        verify(trans).commit();
        verify(trans, never()).rollback();
        verify(mgr, never()).close();

        // closed, but not re-committed
        newTrans.close();

        verify(trans, times(1)).commit();
        verify(mgr).close();
    }

    /**
     * Verifies that commit() does nothing, and that the subsequent close() does not
     * re-commit when a transaction is already active.
     */
    @Test
    public void testCommit_Active() throws Exception {
        when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);

        EntityMgrTrans newTrans = new EntityMgrTrans(mgr);

        newTrans.commit();

        // nothing happened yet
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
        verify(mgr, never()).close();

        // closed, but not re-committed
        newTrans.close();

        // still no commit or rollback
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
        verify(mgr).close();
    }

    @Test(expected = EntityMgrException.class)
    public void testCommit_SecEx() throws Exception {

        doThrow(new SecurityException("expected exception")).when(trans).commit();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            newTrans.commit();
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testCommit_IllStateEx() throws Exception {

        doThrow(new IllegalStateException("expected exception")).when(trans).commit();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            newTrans.commit();
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testCommit_RbEx() throws Exception {

        doThrow(new RollbackException("expected exception")).when(trans).commit();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            newTrans.commit();
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testCommit_HmEx() throws Exception {

        doThrow(new HeuristicMixedException("expected exception")).when(trans).commit();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            newTrans.commit();
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testCommit_HrbEx() throws Exception {

        doThrow(new HeuristicRollbackException("expected exception")).when(trans).commit();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            newTrans.commit();
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testCommit_SysEx() throws Exception {

        doThrow(new SystemException("expected exception")).when(trans).commit();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            newTrans.commit();
        }
    }

    /**
     * Verifies that rollback() only rolls back, and that the subsequent close() does not
     * re-roll back.
     */
    @Test
    public void testRollback() throws Exception {
        EntityMgrTrans newTrans = new EntityMgrTrans(mgr);

        newTrans.rollback();

        when(trans.getStatus()).thenReturn(Status.STATUS_ROLLEDBACK);

        // rolled back, but not closed or committed
        verify(trans, never()).commit();
        verify(trans).rollback();
        verify(mgr, never()).close();

        // closed, but not re-rolled back
        newTrans.close();

        // still no commit or rollback
        verify(trans, never()).commit();
        verify(trans).rollback();
        verify(mgr).close();
    }

    /**
     * Verifies that rollback() does nothing, and that the subsequent close() does not
     * re-roll back when a transaction is already active.
     */
    @Test
    public void testRollback_Active() throws Exception {
        when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
        EntityMgrTrans newTrans = new EntityMgrTrans(mgr);

        newTrans.rollback();

        // nothing happens
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
        verify(mgr, never()).close();

        newTrans.close();

        // still no commit or rollback
        verify(trans, never()).commit();
        verify(trans, never()).rollback();
        verify(mgr).close();
    }

    @Test(expected = EntityMgrException.class)
    public void testRollback_IllStateEx() throws Exception {

        doThrow(new IllegalStateException("expected exception")).when(trans).rollback();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            newTrans.rollback();
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testRollback_SecEx() throws Exception {

        doThrow(new SecurityException("expected exception")).when(trans).rollback();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            newTrans.rollback();
        }
    }

    @Test(expected = EntityMgrException.class)
    public void testRollback_SysEx() throws Exception {

        doThrow(new SystemException("expected exception")).when(trans).rollback();

        try (EntityMgrTrans newTrans = new EntityMgrTrans(mgr)) {
            newTrans.rollback();
        }
    }

    @Test
    public void testEntityMgrException() {
        SecurityException secex = new SecurityException("expected exception");
        EntityMgrException ex = new EntityMgrException(secex);

        assertEquals(secex, ex.getCause());

    }

    /**
     * Tests using real (i.e., not mocked) Persistence classes.
     */
    @Test
    public void testReal() {
        EntityMgrTrans.setUserTrans(savetrans);

        Map<String, Object> propMap = new HashMap<>();

        propMap.put("javax.persistence.jdbc.driver", "org.h2.Driver");
        propMap.put("javax.persistence.jdbc.url", "jdbc:h2:mem:EntityMgrTransTest");

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("junitDroolsSessionEntityPU", propMap);

        try (EntityMgrTrans trans = new EntityMgrTrans(emf.createEntityManager())) {

            // nest a transaction - should still be OK
            
            try (EntityMgrTrans trans2 = new EntityMgrTrans(emf.createEntityManager())) {                
                // Empty
            }

        } catch (Exception e) {
            logger.info("persistence error", e);
            emf.close();
            fail("persistence error");
        }

        emf.close();
    }
}
