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

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.persistence.EntityMgrTrans;

public class EntityMgrTransTest {

	private EntityTransaction trans;
	private EntityManager mgr;

	@Before
	public void setUp() throws Exception {
		trans = mock(EntityTransaction.class);
		mgr = mock(EntityManager.class);

		when(mgr.getTransaction()).thenReturn(trans);
	}



	/**
	 * Verifies that the constructor starts a transaction, but does not do
	 * anything extra before being closed.
	 */
	@Test
	public void testEntityMgrTrans() {
		EntityMgrTrans t = new EntityMgrTrans(mgr);

		// verify that transaction was started
		verify(trans).begin();

		// verify not closed, committed, or rolled back yet
		verify(trans, never()).commit();
		verify(trans, never()).rollback();
		verify(mgr, never()).close();

		t.close();
	}

	/**
	 * Verifies that the transaction is rolled back and the manager is
	 * closed when and a transaction is active.
	 */
	@Test
	public void testClose_Active() {
		EntityMgrTrans t = new EntityMgrTrans(mgr);

		when(trans.isActive()).thenReturn(true);

		t.close();

		// closed and rolled back, but not committed
		verify(trans, never()).commit();
		verify(trans).rollback();
		verify(mgr).close();
	}

	/**
	 * Verifies that the manager is closed, but that the transaction is
	 * <i>not</i> rolled back and when and no transaction is active.
	 */
	@Test
	public void testClose_Inactive() {
		EntityMgrTrans t = new EntityMgrTrans(mgr);

		when(trans.isActive()).thenReturn(false);

		t.close();

		// closed, but not committed or rolled back
		verify(mgr).close();
		verify(trans, never()).commit();
		verify(trans, never()).rollback();
	}

	/**
	 * Verifies that the manager is closed and the transaction rolled back
	 * when "try" block exits normally and a transaction is active.
	 */
	@Test
	public void testClose_TryWithoutExcept_Active() {
		when(trans.isActive()).thenReturn(true);

		try(EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}

		// closed and rolled back, but not committed
		verify(trans, never()).commit();
		verify(trans).rollback();
		verify(mgr).close();
	}

	/**
	 * Verifies that the manager is closed, but that the transaction is
	 * <i>not</i> rolled back when "try" block exits normally and no
	 * transaction is active.
	 */
	@Test
	public void testClose_TryWithoutExcept_Inactive() {
		when(trans.isActive()).thenReturn(false);

		try(EntityMgrTrans t = new EntityMgrTrans(mgr)) {

		}

		// closed, but not rolled back or committed
		verify(trans, never()).commit();
		verify(trans, never()).rollback();
		verify(mgr).close();
	}

	/**
	 * Verifies that the manager is closed and the transaction rolled back
	 * when "try" block throws an exception and a transaction is active.
	 */
	@Test
	public void testClose_TryWithExcept_Active() {
		when(trans.isActive()).thenReturn(true);

		try {
			try(EntityMgrTrans t = new EntityMgrTrans(mgr)) {
				throw new Exception("expected exception");
			}

		} catch (Exception e) {
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
	public void testClose_TryWithExcept_Inactive() {
		when(trans.isActive()).thenReturn(false);

		try {
			try(EntityMgrTrans t = new EntityMgrTrans(mgr)) {
				throw new Exception("expected exception");
			}

		} catch (Exception e) {
		}

		// closed, but not rolled back or committed
		verify(trans, never()).commit();
		verify(trans, never()).rollback();
		verify(mgr).close();
	}

	/**
	 * Verifies that commit() only commits, and that the subsequent close()
	 * does not re-commit.
	 */
	@Test
	public void testCommit() {
		EntityMgrTrans t = new EntityMgrTrans(mgr);

		t.commit();

		// committed, but not closed or rolled back
		verify(trans).commit();
		verify(trans, never()).rollback();
		verify(mgr, never()).close();

		// closed, but not re-committed
		t.close();

		verify(trans, times(1)).commit();
		verify(mgr).close();
	}

	/**
	 * Verifies that rollback() only rolls back, and that the subsequent
	 * close() does not re-roll back.
	 */
	@Test
	public void testRollback() {
		EntityMgrTrans t = new EntityMgrTrans(mgr);

		t.rollback();

		// rolled back, but not closed or committed
		verify(trans, never()).commit();
		verify(trans).rollback();
		verify(mgr, never()).close();

		// closed, but not re-rolled back
		t.close();

		verify(trans, times(1)).rollback();
		verify(mgr).close();
	}

}
