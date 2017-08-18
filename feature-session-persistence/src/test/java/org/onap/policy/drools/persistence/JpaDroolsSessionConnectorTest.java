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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.persistence.DroolsSessionEntity;
import org.onap.policy.drools.persistence.EntityMgrTrans;
import org.onap.policy.drools.persistence.JpaDroolsSessionConnector;

public class JpaDroolsSessionConnectorTest {
	
	private EntityManagerFactory emf;
	private JpaDroolsSessionConnector conn;
	

	@Before
	public void setUp() throws Exception {
		Map<String, Object> propMap = new HashMap<String, Object>();

		propMap.put("javax.persistence.jdbc.driver", "org.h2.Driver");
		propMap.put("javax.persistence.jdbc.url",
						"jdbc:h2:mem:JpaDroolsSessionConnectorTest");
		
		emf = Persistence.createEntityManagerFactory(
								"junitDroolsSessionEntityPU", propMap);
		
		conn = new JpaDroolsSessionConnector(emf);
	}
	
	@After
	public void tearDown() {
		// this will cause the memory db to be dropped
		emf.close();
	}

	@Test
	public void testGet() {
		/*
		 * Load up the DB with some data.
		 */
		
		addSession("nameA", 10);
		addSession("nameY", 20);
		
		
		/*
		 * Now test the functionality.
		 */
		
		// not found
		assertNull( conn.get("unknown"));
		
		assertEquals("{name=nameA, id=10}",
						conn.get("nameA").toString());
		
		assertEquals("{name=nameY, id=20}",
						conn.get("nameY").toString());
	}

	@Test
	public void testReplace_Existing() {
		addSession("nameA", 10);
		
		DroolsSessionEntity sess =
				new DroolsSessionEntity("nameA", 30);
		
		conn.replace(sess);

		// id should be changed
		assertEquals(sess.toString(),
						conn.get("nameA").toString());
	}

	@Test
	public void testReplace_New() {
		DroolsSessionEntity sess =
				new DroolsSessionEntity("nameA", 30);
		
		conn.replace(sess);

		assertEquals(sess.toString(),
						conn.get("nameA").toString());
	}

	@Test
	public void testAdd() {
		DroolsSessionEntity sess =
				new DroolsSessionEntity("nameA", 30);
		
		conn.replace(sess);

		assertEquals(sess.toString(),
						conn.get("nameA").toString());
	}

	@Test
	public void testUpdate() {
		addSession("nameA", 10);
		
		DroolsSessionEntity sess =
				new DroolsSessionEntity("nameA", 30);
		
		conn.replace(sess);

		// id should be changed
		assertEquals("{name=nameA, id=30}",
						conn.get("nameA").toString());
	}


	/**
	 * Adds a session to the DB.
	 * @param sessnm	session name
	 * @param sessid	session id
	 */
	private void addSession(String sessnm, int sessid) {
		EntityManager em = emf.createEntityManager();
		
		try(EntityMgrTrans trans = new EntityMgrTrans(em)) {
			DroolsSessionEntity ent = new DroolsSessionEntity();
			
			ent.setSessionName(sessnm);
			ent.setSessionId(sessid);
			
			em.persist(ent);
		
			trans.commit();
		}
	}
}
