/*-
 * ============LICENSE_START=======================================================
 * policy-core
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

package org.onap.policy.drools.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.util.KieUtils;

/**
 * These tests focus on the following classes:
 *		PolicyContainer
 *		PolicySession
 *		PolicySessionFeatureAPI
 */
public class DroolsContainerTest
{
	/**
	 * This test is centered around the creation of a 'PolicyContainer'
	 * and 'PolicySession', and the updating of that container to a new
	 * version.
	 */
   @BeforeClass
	public static void setUp() throws Exception
   {
	 KieUtils.installArtifact
		  (Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/META-INF/kmodule.xml").toFile(),
	       Paths.get("src/test/resources/drools-artifact-1.1/pom.xml").toFile(),
		   "src/main/resources/rules/org/onap/policy/drools/core/test/rules.drl",
		   Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/rules.drl").toFile());

	 KieUtils.installArtifact
		   (Paths.get("src/test/resources/drools-artifact-1.2/src/main/resources/META-INF/kmodule.xml").toFile(),
			Paths.get("src/test/resources/drools-artifact-1.2/pom.xml").toFile(),
			"src/main/resources/rules/org/onap/policy/drools/core/test/rules.drl",
			Paths.get("src/test/resources/drools-artifact-1.2/src/main/resources/rules.drl").toFile());
   }

  /**
   * This test is centered around the creation of a 'PolicyContainer'
   * and 'PolicySession', and the updating of that container to a new
   * version.
   */
  @Test
	public void createAndUpdate() throws Exception
  {
	// make sure feature log starts out clean
	TestPolicySessionFeatureAPI.getLog();

	// run 'globalInit', and verify expected feature hook fired
	PolicyContainer.globalInit(new String[0]);
	assertEquals(buildArrayList("globalInit"),
				 TestPolicySessionFeatureAPI.getLog());

	// initial conditions -- there should be no containers
	assertEquals(0, PolicyContainer.getPolicyContainers().size());

	// create the container, and start it
	PolicyContainer container =
	  new PolicyContainer("org.onap.policy.drools-pdp",
						  "drools-artifact1", "17.1.0-SNAPSHOT");
	container.start();
	assertTrue(container.isAlive());

	// verify expected feature hooks fired
	assertEquals(buildArrayList("activatePolicySession",
								"newPolicySession",
								"selectThreadModel"),
				 TestPolicySessionFeatureAPI.getLog());

	// this container should be on the list
	{
	  Collection<PolicyContainer> containers =
		PolicyContainer.getPolicyContainers();
	  assertEquals(1, containers.size());
	  assertTrue(containers.contains(container));
	}

	// verify initial container attributes
	assertEquals("org.onap.policy.drools-pdp:drools-artifact1:17.1.0-SNAPSHOT",
				 container.getName());
	assertEquals("org.onap.policy.drools-pdp", container.getGroupId());
	assertEquals("drools-artifact1", container.getArtifactId());
	assertEquals("17.1.0-SNAPSHOT", container.getVersion());

	try
	  {
		// fetch the session, and verify that it exists
		PolicySession session = container.getPolicySession("session1");
		assertTrue(session != null);

		// get all sessions, and verify that this one is the only one
		{
		  Collection<PolicySession> sessions = container.getPolicySessions();
		  assertEquals(1, sessions.size());
		  assertTrue(sessions.contains(session));
		}

		// verify session attributes
		assertEquals(container, session.getPolicyContainer());
		assertEquals("session1", session.getName());
		assertEquals("org.onap.policy.drools-pdp:drools-artifact1:17.1.0-SNAPSHOT:session1",
					 session.getFullName());

		// insert a new fact
		int[] a = new int[]{0, 3, 8, 2};
		session.getKieSession().insert(a);

		// the Drools rules should add 3 + 8 + 2, and store 13 in a[0]
		assertTrue(waitForChange(a) == 13);

		// update the container to a new version --
		// the rules will then multiply values rather than add them
		assertEquals("[]",
					 container.updateToVersion("17.2.0-SNAPSHOT").toString());

		// verify expected feature hooks fired
		assertEquals(buildArrayList("selectThreadModel"),
					 TestPolicySessionFeatureAPI.getLog());

		// verify new container attributes
		assertEquals
		  ("org.onap.policy.drools-pdp:drools-artifact1:17.2.0-SNAPSHOT",
		   container.getName());
		assertEquals("org.onap.policy.drools-pdp", container.getGroupId());
		assertEquals("drools-artifact1", container.getArtifactId());
		assertEquals("17.2.0-SNAPSHOT", container.getVersion());

		// verify new session attributes
		assertEquals(container, session.getPolicyContainer());
		assertEquals("session1", session.getName());
		assertEquals("org.onap.policy.drools-pdp:drools-artifact1:17.2.0-SNAPSHOT:session1",
					 session.getFullName());

		// the updated rules should now multiply 3 * 8 * 2, and return 48

		a[0] = 0;
		container.insert("session1", a);
		assertTrue(waitForChange(a) == 48);
	  }
	finally
	  {
		container.shutdown();
		assertFalse(container.isAlive());

		// verify expected feature hooks fired
		assertEquals(buildArrayList("disposeKieSession"),
					 TestPolicySessionFeatureAPI.getLog());
	  }

	// final conditions -- there should be no containers
	assertEquals(0, PolicyContainer.getPolicyContainers().size());
  }

  /**
   * This test create a 'PolicyContainer' and 'PolicySession', and verifies
   * their behavior, but uses alternate interfaces to increase code coverage.
   * In addition, feature hook invocations will trigger exceptions in this
   * test, also to increase code coverage.
   */
  @Test
	public void versionList() throws Exception
  {
	// make sure feature log starts out clean
	TestPolicySessionFeatureAPI.getLog();

	// trigger exceptions in all feature hooks
	TestPolicySessionFeatureAPI.setExceptionTrigger(true);

	// run 'globalInit', and verify expected feature hook fired
	PolicyContainer.globalInit(new String[0]);
	assertEquals(buildArrayList("globalInit-exception"),
				 TestPolicySessionFeatureAPI.getLog());

	// initial conditions -- there should be no containers
	assertEquals(0, PolicyContainer.getPolicyContainers().size());

	String versionList =
	  "17.3.0-SNAPSHOT,17.1.0-SNAPSHOT,17.2.0-SNAPSHOT";

	// versions should be tried in order -- the 17.1.0-SNAPSHOT should "win",
	// given the fact that '17.3.0-SNAPSHOT' doesn't exist
	PolicyContainer container =
	  new PolicyContainer("org.onap.policy.drools-pdp",
						  "drools-artifact1", versionList);
	// the following should be equivalent to 'container.start()'
	PolicyContainer.activate();
	assertTrue(container.isAlive());

	// verify expected feature hooks fired
	assertEquals(buildArrayList("activatePolicySession-exception",
								"newPolicySession-exception",
								"selectThreadModel-exception"),
				 TestPolicySessionFeatureAPI.getLog());

	// this container should be on the list
	{
	  Collection<PolicyContainer> containers =
		PolicyContainer.getPolicyContainers();
	  assertEquals(1, containers.size());
	  assertTrue(containers.contains(container));
	}

	// verify initial container attributes
	assertEquals("org.onap.policy.drools-pdp:drools-artifact1:17.1.0-SNAPSHOT",
				 container.getName());
	assertEquals("org.onap.policy.drools-pdp", container.getGroupId());
	assertEquals("drools-artifact1", container.getArtifactId());
	assertEquals("17.1.0-SNAPSHOT", container.getVersion());

	// some container adjunct tests
	{
	  Object bogusAdjunct = new Object();

	  // initially, no adjunct
	  assertSame(null, container.getAdjunct(this));

	  // set and verify adjunct
	  container.setAdjunct(this, bogusAdjunct);
	  assertSame(bogusAdjunct, container.getAdjunct(this));

	  // clear and verify adjunct
	  container.setAdjunct(this, null);
	  assertSame(null, container.getAdjunct(this));
	}

	try
	  {
		// fetch the session, and verify that it exists
		PolicySession session = container.getPolicySession("session1");
		assertTrue(session != null);

		// get all sessions, and verify that this one is the only one
		{
		  Collection<PolicySession> sessions = container.getPolicySessions();
		  assertEquals(1, sessions.size());
		  assertTrue(sessions.contains(session));
		}

		// verify session attributes
		assertEquals(container, session.getPolicyContainer());
		assertEquals("session1", session.getName());
		assertEquals("org.onap.policy.drools-pdp:drools-artifact1:17.1.0-SNAPSHOT:session1",
					 session.getFullName());

		// some session adjunct tests
		{
		  Object bogusAdjunct = new Object();

		  // initially, no adjunct
		  assertSame(null, session.getAdjunct(this));

		  // set and verify adjunct
		  session.setAdjunct(this, bogusAdjunct);
		  assertSame(bogusAdjunct, session.getAdjunct(this));

		  // clear and verify adjunct
		  session.setAdjunct(this, null);
		  assertSame(null, session.getAdjunct(this));
		}

		// insert a new fact (using 'insertAll')
		int[] a = new int[]{0, 7, 3, 4};
		container.insertAll(a);

		// the Drools rules should add 7 + 3 + 4, and store 14 in a[0]
		assertTrue(waitForChange(a) == 14);

		// exercise some more API methods
		assertEquals(container.getClassLoader(),
					 container.getKieContainer().getClassLoader());
	  }
	finally
	  {
		// should be equivalent to 'shutdown' without persistence
		container.destroy();
		assertFalse(container.isAlive());

		// verify expected feature hooks fired
		assertEquals(buildArrayList("destroyKieSession-exception"),
					 TestPolicySessionFeatureAPI.getLog());

		// clear exception trigger
		TestPolicySessionFeatureAPI.setExceptionTrigger(false);
	  }

	// final conditions -- there should be no containers
	assertEquals(0, PolicyContainer.getPolicyContainers().size());
  }

  /**
   * This method is tied to the expected behavior of the drools sessions.
   * Initially, the value of 'array[0]' should be 0. The Drools rules
   * will either add or multiply 'array[1]' through 'array[n-1]', depending
   * upon the version. It waits up to 30 seconds for a non-zero value
   * to appear.
   */
  private int waitForChange(int[] array) throws InterruptedException
  {
	int rval = -1;

	// the value is tested every 1/100 of a second, and it waits up to
	// 3000 iterations (= 30 seconds) for a non-zero value
	for (int i = 0 ; i < 3000 ; i += 1)
	  {
		// wait for 10 milliseconds = 1/100 of a second
		Thread.sleep(10);
		if ((rval = array[0]) != 0)
		  {
			// a non-zero value has been stored
			break;
		  }
	  }
	return(rval);
  }

  /**
   * @param args an array of string arguments
   * @return an ArrayList constructed from the provided arguments
   */
  private ArrayList<String> buildArrayList(String... args)
  {
	ArrayList<String> rval = new ArrayList<>();
	for (String arg : args)
	  {
		rval.add(arg);
	  }
	return(rval);
  }
}
