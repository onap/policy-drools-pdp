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
import javax.persistence.EntityTransaction;

/**
 * Wrapper for an <i>EntityManager</i> that creates a transaction that is
 * auto-rolled back when closed.
 */
public class EntityMgrTrans extends EntityMgrCloser {

	/**
	 * Transaction to be rolled back.
	 */
	private EntityTransaction trans;

	/**
	 * 
	 * @param em
	 *            entity for which a transaction is to be begun
	 */
	public EntityMgrTrans(EntityManager em) {
		super(em);

		try {
			trans = em.getTransaction();
			trans.begin();

		} catch (RuntimeException e) {
			em.close();
			throw e;
		}
	}

	/**
	 * Commits the transaction.
	 */
	public void commit() {
		trans.commit();
	}

	/**
	 * Rolls back the transaction.
	 */
	public void rollback() {
		trans.rollback();
	}

	@Override
	public void close() {
		try {
			if (trans.isActive()) {
				trans.rollback();
			}

		} finally {
			super.close();
		}
	}

}
