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
import static org.mockito.Mockito.verify;

import javax.persistence.EntityManager;

import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.persistence.EntityMgrCloser;

public class EntityMgrCloserTest {
	
	private EntityManager mgr;
	

	@Before
	public void setUp() throws Exception {
		mgr = mock(EntityManager.class);
	}


	/**
	 * Verifies that the constructor does not do anything extra before
	 * being closed.
	 */
	@Test
	public void testEntityMgrCloser() {
		EntityMgrCloser c = new EntityMgrCloser(mgr);

		// verify not closed yet
		verify(mgr, never()).close();
		
		c.close();
	}
	
	/**
	 * Verifies that the manager gets closed when close() is invoked.
	 */
	@Test
	public void testClose() {
		EntityMgrCloser c = new EntityMgrCloser(mgr);
		
		c.close();
		
		// should be closed
		verify(mgr).close();
	}

	/**
	 * Ensures that the manager gets closed when "try" block exits normally.
	 */
	@Test
	public void testClose_TryWithoutExcept() {
		try(EntityMgrCloser c = new EntityMgrCloser(mgr)) {
			
		}
		
		verify(mgr).close();
	}

	/**
	 * Ensures that the manager gets closed when "try" block throws an
	 * exception.
	 */
	@Test
	public void testClose_TryWithExcept() {
		try {
			try(EntityMgrCloser c = new EntityMgrCloser(mgr)) {
				throw new Exception("expected exception");
			}
			
		} catch (Exception e) {
		}
		
		verify(mgr).close();
	}

}
