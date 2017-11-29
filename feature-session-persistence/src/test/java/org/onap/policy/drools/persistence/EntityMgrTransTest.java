/*-
 * ============LICENSE_START=======================================================
 * feature-session-persistence
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

package org.onap.policy.drools.persistence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;

import javax.persistence.EntityManager;
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

	@Before
	public void setUp() throws Exception {
		trans = mock(UserTransaction.class);
		mgr = mock(EntityManager.class);

		EntityMgrTrans.setUserTrans(trans);
	}

	/**
	 * Verifies that the constructor starts a transaction, but does not do
	 * anything extra before being closed.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testEntityMgrTrans() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);

		EntityMgrTrans t = new EntityMgrTrans(mgr);

		// verify that transaction was started
		verify(trans).begin();

		// verify not closed, committed, or rolled back yet
		verify(trans, never()).commit();
		verify(trans, never()).rollback();
		verify(mgr, never()).close();

		t.close();
	}

	@Test(expected = EntityMgrException.class)
	public void testEntityMgrTrans_RtEx() throws Exception {

		doThrow(new IllegalArgumentException("expected exception")).when(trans).begin();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}
	}

	@Test(expected = EntityMgrException.class)
	public void testEntityMgrTrans_NotSuppEx() throws Exception {

		doThrow(new NotSupportedException("expected exception")).when(trans).begin();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}
	}

	@Test(expected = EntityMgrException.class)
	public void testEntityMgrTrans_SysEx() throws Exception {

		doThrow(new SystemException("expected exception")).when(trans).begin();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}
	}

	/**
	 * Verifies that the transaction is rolled back and the manager is closed
	 * when and a transaction is active.
	 */
	@Test
	public void testClose_Active() throws Exception {
		EntityMgrTrans t = new EntityMgrTrans(mgr);

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);

		t.close();

		// closed and rolled back, but not committed
		verify(trans, never()).commit();
		verify(trans).rollback();
		verify(mgr).close();
	}

	/**
	 * Verifies that the manager is closed, but that the transaction is
	 * <i>not</i> rolled back when and no transaction is active.
	 */
	@Test
	public void testClose_Inactive() throws Exception {
		EntityMgrTrans t = new EntityMgrTrans(mgr);

		when(trans.getStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);

		t.close();

		// closed, but not committed or rolled back
		verify(mgr).close();
		verify(trans, never()).commit();
		verify(trans, never()).rollback();
	}

	@Test(expected = EntityMgrException.class)
	public void testClose_IllStateEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new IllegalStateException("expected exception")).when(trans).rollback();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}
	}

	@Test(expected = EntityMgrException.class)
	public void testClose_SecEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new SecurityException("expected exception")).when(trans).rollback();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}
	}

	@Test(expected = EntityMgrException.class)
	public void testClose_SysEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new SystemException("expected exception")).when(trans).rollback();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}
	}

	/**
	 * Verifies that the manager is closed and the transaction rolled back when
	 * "try" block exits normally and a transaction is active.
	 */
	@Test
	public void testClose_TryWithoutExcept_Active() throws Exception {
		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}

		// closed and rolled back, but not committed
		verify(trans, never()).commit();
		verify(trans).rollback();
		verify(mgr).close();
	}

	/**
	 * Verifies that the manager is closed, but that the transaction is
	 * <i>not</i> rolled back when "try" block exits normally and no transaction
	 * is active.
	 */
	@Test
	public void testClose_TryWithoutExcept_Inactive() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}

		// closed, but not rolled back or committed
		verify(trans, never()).commit();
		verify(trans, never()).rollback();
		verify(mgr).close();
	}

	/**
	 * Verifies that the manager is closed and the transaction rolled back when
	 * "try" block throws an exception and a transaction is active.
	 */
	@Test
	public void testClose_TryWithExcept_Active() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);

		try {
			try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
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
	 * Verifies that the manager is closed, but that the transaction is
	 * <i>not</i> rolled back when "try" block throws an exception and no
	 * transaction is active.
	 */
	@Test
	public void testClose_TryWithExcept_Inactive() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);

		try {
			try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
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
	 * Verifies that commit() only commits, and that the subsequent close() does
	 * not re-commit.
	 */
	@Test
	public void testCommit() throws Exception {
		EntityMgrTrans t = new EntityMgrTrans(mgr);
		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);

		t.commit();

		when(trans.getStatus()).thenReturn(Status.STATUS_COMMITTED);

		// committed, but not closed or rolled back
		verify(trans).commit();
		verify(trans, never()).rollback();
		verify(mgr, never()).close();

		// closed, but not re-committed
		t.close();

		verify(trans, times(1)).commit();
		verify(mgr).close();
	}

	@Test(expected = EntityMgrException.class)
	public void testCommit_SecEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new SecurityException("expected exception")).when(trans).commit();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
			t.commit();
		}
	}

	@Test(expected = EntityMgrException.class)
	public void testCommit_IllStateEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new IllegalStateException("expected exception")).when(trans).commit();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
			t.commit();
		}
	}

	@Test(expected = EntityMgrException.class)
	public void testCommit_RbEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new RollbackException("expected exception")).when(trans).commit();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
			t.commit();
		}
	}

	@Test(expected = EntityMgrException.class)
	public void testCommit_HmEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new HeuristicMixedException("expected exception")).when(trans).commit();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
			t.commit();
		}
	}

	@Test(expected = EntityMgrException.class)
	public void testCommit_HrbEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new HeuristicRollbackException("expected exception")).when(trans).commit();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
			t.commit();
		}
	}

	@Test(expected = EntityMgrException.class)
	public void testCommit_SysEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new SystemException("expected exception")).when(trans).commit();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
			t.commit();
		}
	}

	/**
	 * Verifies that rollback() only rolls back, and that the subsequent close()
	 * does not re-roll back.
	 */
	@Test
	public void testRollback() throws Exception {
		EntityMgrTrans t = new EntityMgrTrans(mgr);
		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);

		t.rollback();

		when(trans.getStatus()).thenReturn(Status.STATUS_ROLLEDBACK);

		// rolled back, but not closed or committed
		verify(trans, never()).commit();
		verify(trans).rollback();
		verify(mgr, never()).close();

		// closed, but not re-rolled back
		t.close();

		verify(trans, times(1)).rollback();
		verify(mgr).close();
	}

	@Test(expected = EntityMgrException.class)
	public void testRollback_IllStateEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new IllegalStateException("expected exception")).when(trans).rollback();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
			t.rollback();
		}
	}

	@Test(expected = EntityMgrException.class)
	public void testRollback_SecEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new SecurityException("expected exception")).when(trans).rollback();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
			t.rollback();
		}
	}

	@Test(expected = EntityMgrException.class)
	public void testRollback_SysEx() throws Exception {

		when(trans.getStatus()).thenReturn(Status.STATUS_ACTIVE);
		doThrow(new SystemException("expected exception")).when(trans).rollback();

		try (EntityMgrTrans t = new EntityMgrTrans(mgr)) {
			t.rollback();
		}
	}

	@Test
	public void testEntityMgrException() {
		SecurityException secex = new SecurityException("expected exception");
		EntityMgrException ex = new EntityMgrException(secex);

		assertEquals(secex, ex.getCause());

	}
}
