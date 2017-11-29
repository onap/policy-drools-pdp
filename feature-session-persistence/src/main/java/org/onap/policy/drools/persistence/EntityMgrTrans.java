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

import javax.persistence.EntityManager;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

/**
 * Wrapper for an <i>EntityManager</i> that creates a transaction that is
 * auto-rolled back when closed.
 */
public class EntityMgrTrans extends EntityMgrCloser {

	/**
	 * Transaction to be rolled back.
	 */
	private static UserTransaction userTrans = com.arjuna.ats.jta.UserTransaction.userTransaction();

	/**
	 * 
	 * @param em
	 *            entity for which a transaction is to be begun
	 */
	public EntityMgrTrans(EntityManager em) {
		super(em);

		try {
			userTrans.begin();
			em.joinTransaction();

		} catch (RuntimeException e) {
			em.close();
			throw new EntityMgrException(e);

		} catch (NotSupportedException | SystemException e) {
			em.close();
			throw new EntityMgrException(e);
		}
	}

	/**
	 * Gets the user transaction. For use by junit tests.
	 * 
	 * @return the user transaction
	 */
	protected static UserTransaction getUserTrans() {
		return userTrans;
	}

	/**
	 * Sets the user transaction. For use by junit tests.
	 * 
	 * @param userTrans
	 *            the new user transaction
	 */
	protected static void setUserTrans(UserTransaction userTrans) {
		EntityMgrTrans.userTrans = userTrans;
	}

	/**
	 * Commits the transaction.
	 */
	public void commit() {
		try {
			userTrans.commit();

		} catch (SecurityException | IllegalStateException | RollbackException | HeuristicMixedException
				| HeuristicRollbackException | SystemException e) {

			throw new EntityMgrException(e);
		}
	}

	/**
	 * Rolls back the transaction.
	 */
	public void rollback() {
		try {
			userTrans.rollback();

		} catch (IllegalStateException | SecurityException | SystemException e) {
			throw new EntityMgrException(e);
		}
	}

	@Override
	public void close() {
		try {
			if (userTrans.getStatus() == Status.STATUS_ACTIVE) {
				userTrans.rollback();
			}

		} catch (IllegalStateException | SecurityException | SystemException e) {
			throw new EntityMgrException(e);

		} finally {
			super.close();
		}
	}

	/**
	 * Runtime exceptions generated by this class. Wrap exceptions generated by
	 * delegated operations, particularly when they are not, themselves, Runtime
	 * exceptions.
	 */
	public static class EntityMgrException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		/**
		 * 
		 * @param e
		 *            exception to be wrapped
		 */
		public EntityMgrException(Exception e) {
			super(e);
		}
	}
}
