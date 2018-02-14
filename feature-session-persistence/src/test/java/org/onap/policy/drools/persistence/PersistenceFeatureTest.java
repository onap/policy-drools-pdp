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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.persistence.jpa.KieStoreServices;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.mockito.ArgumentCaptor;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.PolicySession.ThreadModel;
import org.onap.policy.drools.persistence.PersistenceFeature.PersistenceFeatureException;
import org.onap.policy.drools.persistence.PersistenceFeature.PersistentThreadModel;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistenceFeatureTest {

	private static final Logger logger = LoggerFactory.getLogger(PersistenceFeatureTest.class);

	private static final String JDBC_DRIVER = "fake.driver";
	private static final String JDBC_URL = "fake.url";
	private static final String JDBC_USER = "fake.user";
	private static final String JDBC_PASSWD = "fake.password";
	private static final String JTA_OSDIR = "target";
	private static final String SRC_TEST_RESOURCES = "src/test/resources";

	private static Properties stdprops;

	private JpaDroolsSessionConnector jpa;
	private DroolsSession sess;
	private KieSession kiesess;
	private BasicDataSource bds;
	private EntityManagerFactory emf;
	private Connection conn;
	private Properties props;
	private KieServices kiesvc;
	private Environment kieenv;
	private KieSessionConfiguration kiecfg;
	private KieBase kiebase;
	private KieStoreServices kiestore;
	private KieContainer kiecont;
	private TransactionManager transmgr;
	private UserTransaction usertrans;
	private TransactionSynchronizationRegistry transreg;
	private PolicyController polctlr;
	private PolicyContainer polcont;
	private PolicySession polsess;
	private PersistenceFeature.Factory fact;

	private PersistenceFeature feat;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		stdprops = new Properties();

		stdprops.put(DroolsPersistenceProperties.DB_DRIVER, JDBC_DRIVER);
		stdprops.put(DroolsPersistenceProperties.DB_URL, JDBC_URL);
		stdprops.put(DroolsPersistenceProperties.DB_USER, JDBC_USER);
		stdprops.put(DroolsPersistenceProperties.DB_PWD, JDBC_PASSWD);
		stdprops.put(DroolsPersistenceProperties.JTA_OBJECTSTORE_DIR, JTA_OSDIR);
		stdprops.put(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT, "50");

		System.setProperty("com.arjuna.ats.arjuna.objectstore.objectStoreDir", "target/tm");
		System.setProperty("ObjectStoreEnvironmentBean.objectStoreDir", "target/tm");
	}

	@Before
	public void setUp() throws Exception {
		jpa = mock(JpaDroolsSessionConnector.class);
		sess = mock(DroolsSession.class);
		bds = mock(BasicDataSource.class);
		emf = mock(EntityManagerFactory.class);
		kiesess = mock(KieSession.class);
		conn = null;
		props = new Properties();
		kiesvc = mock(KieServices.class);
		kieenv = mock(Environment.class);
		kiecfg = mock(KieSessionConfiguration.class);
		kiebase = mock(KieBase.class);
		kiestore = mock(KieStoreServices.class);
		kiecont = mock(KieContainer.class);
		transmgr = mock(TransactionManager.class);
		usertrans = mock(UserTransaction.class);
		transreg = mock(TransactionSynchronizationRegistry.class);
		polcont = mock(PolicyContainer.class);
		polctlr = mock(PolicyController.class);
		polsess = mock(PolicySession.class);
		fact = mock(PersistenceFeature.Factory.class);

		feat = new PersistenceFeature();
		feat.setFactory(fact);

		props.putAll(stdprops);

		System.setProperty("com.arjuna.ats.arjuna.objectstore.objectStoreDir", "target/tm");
		System.setProperty("ObjectStoreEnvironmentBean.objectStoreDir", "target/tm");

		when(fact.getKieServices()).thenReturn(kiesvc);
		when(fact.getTransMgr()).thenReturn(transmgr);
		when(fact.getUserTrans()).thenReturn(usertrans);
		when(fact.getTransSyncReg()).thenReturn(transreg);
		when(fact.loadProperties(anyString())).thenReturn(props);

		when(kiesvc.newEnvironment()).thenReturn(kieenv);
		when(kiesvc.getStoreServices()).thenReturn(kiestore);
		when(kiesvc.newKieSessionConfiguration()).thenReturn(kiecfg);

		when(polcont.getKieContainer()).thenReturn(kiecont);

		when(polsess.getPolicyContainer()).thenReturn(polcont);

		when(kiecont.getKieBase(anyString())).thenReturn(kiebase);
	}

	@After
	public void tearDown() {
		// this will cause the in-memory test DB to be dropped
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				logger.warn("failed to close connection", e);
			}
		}

		if (emf != null) {
			try {
				emf.close();
			} catch (IllegalArgumentException e) {
				logger.trace("ignored exception", e);
			}
		}
	}

	@Test
	public void testGetContainerAdjunct_New() throws Exception {

		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		// force getContainerAdjunct() to be invoked
		feat.activatePolicySession(polcont, "myname", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());

		assertNotNull(adjcap.getValue());
	}

	@Test
	public void testGetContainerAdjunct_Existing() throws Exception {

		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		// force getContainerAdjunct() to be invoked
		feat.activatePolicySession(polcont, "myname", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());

		// return adjunct on next call
		when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

		// force getContainerAdjunct() to be invoked again
		setUpKie("myname2", 999L, true);
		feat.activatePolicySession(polcont, "myname2", "mybase");

		// ensure it isn't invoked again
		verify(polcont, times(1)).setAdjunct(any(), any());
	}

	@Test
	public void testGetContainerAdjunct_WrongType() throws Exception {

		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		// return false adjunct on next call
		when(polcont.getAdjunct(any())).thenReturn("not-a-real-adjunct");

		// force getContainerAdjunct() to be invoked
		setUpKie("myname2", 999L, true);
		feat.activatePolicySession(polcont, "myname2", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());

		assertNotNull(adjcap.getValue());
	}

	@Test
	public void testGetSequenceNumber() {
		assertEquals(1, feat.getSequenceNumber());
	}

	@Test
	public void testGlobalInit() throws Exception {

		feat.globalInit(null, SRC_TEST_RESOURCES);

		// verify that various factory methods were invoked
		verify(fact).getKieServices();
		verify(fact).loadProperties("src/test/resources/feature-session-persistence.properties");
	}

	@Test(expected = NullPointerException.class)
	public void testGlobalInit_IOEx() throws Exception {
		
		when(fact.loadProperties(anyString())).thenThrow(new IOException("expected exception"));

		feat.globalInit(null, SRC_TEST_RESOURCES);
	}

	@Test
	public void testActivatePolicySession() throws Exception {
		PreparedStatement ps = mockDbConn(5);
		setUpKie("myname", 999L, true);

		feat.globalInit(null, SRC_TEST_RESOURCES);
		feat.beforeActivate(null);

		KieSession s = feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore).loadKieSession(anyLong(), any(), any(), any());
		verify(kiestore, never()).newKieSession(any(), any(), any());

		assertEquals(s, kiesess);

		verify(ps).executeUpdate();

		verify(kieenv, times(4)).set(anyString(), any());

		verify(jpa).get("myname");
		verify(jpa).replace(any());
	}

	@Test
	public void testActivatePolicySession_NoPersistence() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		PreparedStatement ps = mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.remove("persistence.type");

		feat.beforeStart(null);

		assertNull(feat.activatePolicySession(polcont, "myname", "mybase"));

		verify(ps, never()).executeUpdate();
		verify(kiestore, never()).loadKieSession(anyLong(), any(), any(), any());
		verify(kiestore, never()).newKieSession(any(), any(), any());
	}

	/**
	 * Verifies that a new KIE session is created when there is no existing
	 * session entity.
	 */
	@Test
	public void testActivatePolicySession_New() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("noName", 999L, true);

		KieSession s = feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore, never()).loadKieSession(anyLong(), any(), any(), any());
		verify(kiestore).newKieSession(any(), any(), any());

		assertEquals(s, kiesess);

		verify(kieenv, times(4)).set(anyString(), any());

		verify(jpa).get("myname");
		verify(jpa).replace(any());
	}

	/**
	 * Verifies that a new KIE session is created when there KIE fails to load
	 * an existing session.
	 */
	@Test
	public void testActivatePolicySession_LoadFailed() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		KieSession s = feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore).loadKieSession(anyLong(), any(), any(), any());
		verify(kiestore).newKieSession(any(), any(), any());

		assertEquals(s, kiesess);

		verify(kieenv, times(4)).set(anyString(), any());

		verify(jpa).get("myname");

		ArgumentCaptor<DroolsSession> d = ArgumentCaptor.forClass(DroolsSession.class);
		verify(jpa).replace(d.capture());

		assertEquals("myname", d.getValue().getSessionName());
		assertEquals(100L, d.getValue().getSessionId());
	}

	@Test
	public void testLoadDataSource() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(fact).makeEntMgrFact(any());
	}

	@Test
	public void testConfigureSysProps() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		feat.activatePolicySession(polcont, "myname", "mybase");

		assertEquals("60", System.getProperty("com.arjuna.ats.arjuna.coordinator.defaultTimeout"));
		assertEquals(JTA_OSDIR, System.getProperty("com.arjuna.ats.arjuna.objectstore.objectStoreDir"));
		assertEquals(JTA_OSDIR, System.getProperty("ObjectStoreEnvironmentBean.objectStoreDir"));
	}

	@Test
	public void testConfigureKieEnv() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kieenv, times(4)).set(any(), any());

		verify(kieenv).set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
		verify(kieenv).set(EnvironmentName.TRANSACTION, usertrans);
		verify(kieenv).set(EnvironmentName.TRANSACTION_MANAGER, transmgr);
		verify(kieenv).set(EnvironmentName.TRANSACTION_SYNCHRONIZATION_REGISTRY, transreg);
		
		verify(bds, times(1)).close();
	}

	@Test
	public void testConfigureKieEnv_RtEx() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		when(fact.getUserTrans()).thenThrow(new IllegalArgumentException("expected exception"));

		try {
			feat.activatePolicySession(polcont, "myname", "mybase");
			fail("missing exception");
			
		} catch(IllegalArgumentException ex) {
			logger.trace("expected exception", ex);
		}

		verify(bds, times(2)).close();
	}

	@Test
	public void testLoadKieSession() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		KieSession s = feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore).loadKieSession(999L, kiebase, kiecfg, kieenv);
		verify(kiestore, never()).newKieSession(any(), any(), any());

		assertEquals(s, kiesess);
	}

	/*
	 * Verifies that loadKieSession() returns null (thus causing newKieSession()
	 * to be called) when an Exception occurs.
	 */
	@Test
	public void testLoadKieSession_Ex() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		when(kiestore.loadKieSession(anyLong(), any(), any(), any()))
				.thenThrow(new IllegalArgumentException("expected exception"));

		KieSession s = feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore).loadKieSession(anyLong(), any(), any(), any());
		verify(kiestore).newKieSession(any(), any(), any());

		assertEquals(s, kiesess);
	}

	@Test
	public void testNewKieSession() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		KieSession s = feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore).newKieSession(kiebase, null, kieenv);

		assertEquals(s, kiesess);
	}

	@Test
	public void testLoadDataSource_DiffSession() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		feat.activatePolicySession(polcont, "myname", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		verify(polcont).setAdjunct(any(), adjcap.capture());

		when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

		setUpKie("myname2", 999L, false);

		// invoke it again
		feat.activatePolicySession(polcont, "myname2", "mybase");

		verify(fact, times(2)).makeEntMgrFact(any());
	}
	
	@Test
	public void testSelectThreadModel_Persistent() throws Exception {
		setUpKie("myname", 999L, true);
		
		ThreadModel m = feat.selectThreadModel(polsess);
		assertNotNull(m);
		assertTrue(m instanceof PersistentThreadModel);
		
	}
	
	@Test
	public void testSelectThreadModel_NotPersistent() throws Exception {
		when(fact.getPolicyController(any())).thenReturn(polctlr);
		assertNull(feat.selectThreadModel(polsess));
		
	}
	
	@Test
	public void testSelectThreadModel_Start__Run_Update_Stop() throws Exception {
		setUpKie("myname", 999L, true);
		
		ThreadModel m = feat.selectThreadModel(polsess);
		assertNotNull(m);
		assertTrue(m instanceof PersistentThreadModel);
		
		when(polsess.getKieSession()).thenReturn(kiesess);
		
		m.start();
		new CountDownLatch(1).await(10, TimeUnit.MILLISECONDS);
		m.updated();
		m.stop();
	}

	@Test
	public void testDisposeKieSession() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(emf, never()).close();
		verify(polcont).setAdjunct(any(), adjcap.capture());

		when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

		feat.disposeKieSession(polsess);

		// call twice to ensure it isn't re-closed
		feat.disposeKieSession(polsess);

		verify(emf, times(1)).close();
	}

	@Test
	public void testDisposeKieSession_NoAdjunct() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		feat.disposeKieSession(polsess);
	}

	@Test
	public void testDisposeKieSession_NoPersistence() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(emf, never()).close();
		verify(polcont).setAdjunct(any(), adjcap.capture());

		when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

		// specify a session that was never loaded
		when(polsess.getName()).thenReturn("anotherName");

		feat.disposeKieSession(polsess);

		verify(emf, never()).close();
	}

	@Test
	public void testDestroyKieSession() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(emf, never()).close();
		verify(polcont).setAdjunct(any(), adjcap.capture());

		when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

		feat.destroyKieSession(polsess);

		// call twice to ensure it isn't re-closed
		feat.destroyKieSession(polsess);

		verify(emf, times(1)).close();
	}

	@Test
	public void testDestroyKieSession_NoAdjunct() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		feat.destroyKieSession(polsess);
	}

	@Test
	public void testDestroyKieSession_NoPersistence() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(emf, never()).close();
		verify(polcont).setAdjunct(any(), adjcap.capture());

		when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

		// specify a session that was never loaded
		when(polsess.getName()).thenReturn("anotherName");

		feat.destroyKieSession(polsess);

		verify(emf, never()).close();
	}

	@Test
	public void testAfterStart() {
		assertFalse(feat.afterStart(null));
	}

	@Test
	public void testBeforeStart() {
		assertFalse(feat.beforeStart(null));
	}

	@Test
	public void testBeforeShutdown() {
		assertFalse(feat.beforeShutdown(null));
	}

	@Test
	public void testAfterShutdown() {
		assertFalse(feat.afterShutdown(null));
	}

	@Test
	public void testBeforeConfigure() {
		assertFalse(feat.beforeConfigure(null, null));
	}

	@Test
	public void testAfterConfigure() {
		assertFalse(feat.afterConfigure(null));
	}

	@Test
	public void testBeforeActivate() {
		assertFalse(feat.beforeActivate(null));
	}

	@Test
	public void testAfterActivate() {
		assertFalse(feat.afterActivate(null));
	}

	@Test
	public void testBeforeDeactivate() {
		assertFalse(feat.beforeDeactivate(null));
	}

	@Test
	public void testAfterDeactivate() {
		assertFalse(feat.afterDeactivate(null));
	}

	@Test
	public void testBeforeStop() {
		assertFalse(feat.beforeStop(null));
	}

	@Test
	public void testAfterStop() {
		assertFalse(feat.afterStop(null));
	}

	@Test
	public void testBeforeLock() {
		assertFalse(feat.beforeLock(null));
	}

	@Test
	public void testAfterLock() {
		assertFalse(feat.afterLock(null));
	}

	@Test
	public void testBeforeUnlock() {
		assertFalse(feat.beforeUnlock(null));
	}

	@Test
	public void testAfterUnlock() {
		assertFalse(feat.afterUnlock(null));
	}

	@Test
	public void testGetPersistenceTimeout_Valid() throws Exception {
		PreparedStatement s = mockDbConn(5);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(s).executeUpdate();
	}

	@Test
	public void testGetPersistenceTimeout_Missing() throws Exception {

		props.remove(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT);

		PreparedStatement s = mockDbConn(0);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(s, never()).executeUpdate();
	}

	@Test
	public void testGetPersistenceTimeout_Invalid() throws Exception {
		props.setProperty(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT, "abc");
		PreparedStatement s = mockDbConn(0);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(s, never()).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo() throws Exception {
		setUpKie("myname", 999L, true);

		// use a real DB so we can verify that the "delete" works correctly
		fact = new PartialFactory();
		feat.setFactory(fact);

		makeSessionInfoTbl(20000);

		// create mock entity manager for use by JPA connector
		EntityManager em = mock(EntityManager.class);
		when(emf.createEntityManager()).thenReturn(em);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		feat.beforeStart(null);
		feat.activatePolicySession(polcont, "myname", "mybase");

		assertEquals("[1, 4, 5]", getSessions().toString());
	}

	@Test
	public void testCleanUpSessionInfo_WithBeforeStart() throws Exception {
		PreparedStatement s = mockDbConn(0);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		// reset
		feat.beforeStart(null);

		feat.activatePolicySession(polcont, "myname", "mybase");
		verify(s, times(1)).executeUpdate();

		// should not clean-up again
		feat.activatePolicySession(polcont, "myname", "mybase");
		feat.activatePolicySession(polcont, "myname", "mybase");
		verify(s, times(1)).executeUpdate();

		// reset
		feat.beforeStart(null);

		feat.activatePolicySession(polcont, "myname", "mybase");
		verify(s, times(2)).executeUpdate();

		// should not clean-up again
		feat.activatePolicySession(polcont, "myname", "mybase");
		feat.activatePolicySession(polcont, "myname", "mybase");
		verify(s, times(2)).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_WithBeforeActivate() throws Exception {
		PreparedStatement s = mockDbConn(0);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		// reset
		feat.beforeActivate(null);

		feat.activatePolicySession(polcont, "myname", "mybase");
		verify(s, times(1)).executeUpdate();

		// should not clean-up again
		feat.activatePolicySession(polcont, "myname", "mybase");
		feat.activatePolicySession(polcont, "myname", "mybase");
		verify(s, times(1)).executeUpdate();

		// reset
		feat.beforeActivate(null);

		feat.activatePolicySession(polcont, "myname", "mybase");
		verify(s, times(2)).executeUpdate();

		// should not clean-up again
		feat.activatePolicySession(polcont, "myname", "mybase");
		feat.activatePolicySession(polcont, "myname", "mybase");
		verify(s, times(2)).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_NoTimeout() throws Exception {

		props.remove(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT);

		PreparedStatement s = mockDbConn(0);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(s, never()).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_NoUrl() throws Exception {
		PreparedStatement s = mockDbConn(0);

		props.remove(DroolsPersistenceProperties.DB_URL);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		try {
			feat.activatePolicySession(polcont, "myname", "mybase");
			fail("missing exception");
		} catch (RuntimeException e) {
			logger.trace("expected exception", e);
		}

		verify(s, never()).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_NoUser() throws Exception {
		PreparedStatement s = mockDbConn(0);

		props.remove(DroolsPersistenceProperties.DB_USER);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		try {
			feat.activatePolicySession(polcont, "myname", "mybase");
			fail("missing exception");
		} catch (RuntimeException e) {
			logger.trace("expected exception", e);
		}

		verify(s, never()).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_NoPassword() throws Exception {
		PreparedStatement s = mockDbConn(0);

		props.remove(DroolsPersistenceProperties.DB_PWD);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		try {
			feat.activatePolicySession(polcont, "myname", "mybase");
			fail("missing exception");
		} catch (RuntimeException e) {
			logger.trace("expected exception", e);
		}

		verify(s, never()).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_SqlEx() throws Exception {
		PreparedStatement s = mockDbConn(-1);

		feat.globalInit(null, SRC_TEST_RESOURCES);

		setUpKie("myname", 999L, true);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(s).executeUpdate();
	}

	@Test
	public void testGetDroolsSessionConnector() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(fact).makeJpaConnector(emf);
	}

	@Test
	public void testReplaceSession() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		ArgumentCaptor<DroolsSession> sesscap = ArgumentCaptor.forClass(DroolsSession.class);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(jpa).replace(sesscap.capture());

		assertEquals("myname", sesscap.getValue().getSessionName());
		assertEquals(999L, sesscap.getValue().getSessionId());
	}

	@Test
	public void testIsPersistenceEnabled_Auto() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.setProperty("persistence.type", "auto");

		assertNotNull(feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	@Test
	public void testIsPersistenceEnabled_Native() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.setProperty("persistence.type", "native");

		assertNotNull(feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	@Test
	public void testIsPersistenceEnabled_None() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.remove("persistence.type");

		assertNull(feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	@Test
	public void testGetProperties_Ex() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		when(fact.getPolicyController(polcont)).thenThrow(new IllegalArgumentException("expected exception"));

		assertNull(feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	@Test
	public void testGetProperty_Specific() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.remove("persistence.type");
		props.setProperty("persistence.myname.type", "auto");

		assertNotNull(feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	@Test
	public void testGetProperty_Specific_None() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.remove("persistence.type");
		props.setProperty("persistence.xxx.type", "auto");

		assertNull(feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	@Test
	public void testGetProperty_Both_SpecificOn() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.setProperty("persistence.type", "other");
		props.setProperty("persistence.myname.type", "auto");

		assertNotNull(feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	@Test
	public void testGetProperty_Both_SpecificDisabledOff() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.setProperty("persistence.type", "auto");
		props.setProperty("persistence.myname.type", "other");

		assertNull(feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	@Test
	public void testGetProperty_None() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.remove("persistence.type");

		assertNull(feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	@Test
	public void testPersistenceFeatureException() {
		SecurityException secex = new SecurityException("expected exception");
		PersistenceFeatureException ex = new PersistenceFeatureException(secex);

		assertEquals(secex, ex.getCause());

	}

	@Test
	public void testDsEmf_RtEx() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		when(fact.makeEntMgrFact(any())).thenThrow(new IllegalArgumentException("expected exception"));

		try {
			feat.activatePolicySession(polcont, "myname", "mybase");
			fail("missing exception");
			
		} catch(IllegalArgumentException ex) {
			logger.trace("expected exception", ex);
		}

		verify(bds, times(2)).close();
	}

	@Test
	public void testDsEmf_Close_RtEx() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		feat.activatePolicySession(polcont, "myname", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());

		// return adjunct on next call
		when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

		try {
			doThrow(new IllegalArgumentException("expected exception")).when(emf).close();
			
			feat.destroyKieSession(polsess);
			fail("missing exception");
			
		} catch(IllegalArgumentException ex) {
			logger.trace("expected exception", ex);
		}

		verify(bds, times(2)).close();
	}

	@Test
	public void testDsEmf_CloseDataSource_RtEx() throws Exception {
		feat.globalInit(null, SRC_TEST_RESOURCES);

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		feat.activatePolicySession(polcont, "myname", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap = ArgumentCaptor
				.forClass(PersistenceFeature.ContainerAdjunct.class);

		verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());

		// return adjunct on next call
		when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

		try {
			doThrow(new SQLException("expected exception")).when(bds).close();
			
			feat.destroyKieSession(polsess);
			fail("missing exception");
			
		} catch(PersistenceFeatureException ex) {
			logger.trace("expected exception", ex);
		}
	}

	/**
	 * Gets an ordered list of ids of the current SessionInfo records.
	 * 
	 * @return ordered list of SessInfo IDs
	 * @throws SQLException
	 * @throws IOException
	 */
	private List<Integer> getSessions() throws SQLException, IOException {
		attachDb();

		ArrayList<Integer> lst = new ArrayList<>(5);

		try (PreparedStatement stmt = conn.prepareStatement("SELECT id from sessioninfo order by id");
				ResultSet rs = stmt.executeQuery()) {

			while (rs.next()) {
				lst.add(rs.getInt(1));
			}
		}

		return lst;
	}

	/**
	 * Sets up for doing invoking the newKieSession() method.
	 * 
	 * @param sessnm
	 *            name to which JPA should respond with a session
	 * @param sessid
	 *            session id to be returned by the session
	 * @param loadOk
	 *            {@code true} if loadKieSession() should return a value,
	 *            {@code false} to return null
	 * @throws Exception 
	 */
	private void setUpKie(String sessnm, long sessid, boolean loadOk) throws Exception {

		when(fact.makeJpaConnector(emf)).thenReturn(jpa);
		when(fact.makeEntMgrFact(any())).thenReturn(emf);
		when(fact.getPolicyController(polcont)).thenReturn(polctlr);

		props.setProperty("persistence.type", "auto");

		when(polctlr.getProperties()).thenReturn(props);

		when(jpa.get(sessnm)).thenReturn(sess);

		when(sess.getSessionId()).thenReturn(sessid);

		when(polsess.getPolicyContainer()).thenReturn(polcont);
		when(polsess.getName()).thenReturn(sessnm);

		if (loadOk) {
			when(kiesess.getIdentifier()).thenReturn(sessid);
			when(kiestore.loadKieSession(anyLong(), any(), any(), any())).thenReturn(kiesess);

		} else {
			// use an alternate id for the new session
			when(kiesess.getIdentifier()).thenReturn(100L);
			when(kiestore.loadKieSession(anyLong(), any(), any(), any())).thenReturn(null);
		}

		when(kiestore.newKieSession(any(), any(), any())).thenReturn(kiesess);
	}

	/**
	 * Creates the SessionInfo DB table and populates it with some data.
	 * 
	 * @param expMs
	 *            number of milli-seconds for expired sessioninfo records
	 * @throws SQLException
	 * @throws IOException
	 */
	private void makeSessionInfoTbl(int expMs) throws SQLException, IOException {

		attachDb();

		try (PreparedStatement stmt = conn
				.prepareStatement("CREATE TABLE sessioninfo(id int, lastmodificationdate timestamp)")) {

			stmt.executeUpdate();
		}

		try (PreparedStatement stmt = conn
				.prepareStatement("INSERT into sessioninfo(id, lastmodificationdate) values(?, ?)")) {

			Timestamp ts;

			// current data
			ts = new Timestamp(System.currentTimeMillis());
			stmt.setTimestamp(2, ts);

			stmt.setInt(1, 1);
			stmt.executeUpdate();

			stmt.setInt(1, 4);
			stmt.executeUpdate();

			stmt.setInt(1, 5);
			stmt.executeUpdate();

			// expired data
			ts = new Timestamp(System.currentTimeMillis() - expMs);
			stmt.setTimestamp(2, ts);

			stmt.setInt(1, 2);
			stmt.executeUpdate();

			stmt.setInt(1, 3);
			stmt.executeUpdate();
		}
	}

	/**
	 * Attaches {@link #conn} to the DB, if it isn't already attached.
	 * 
	 * @throws SQLException
	 * @throws IOException
	 *             if the property file cannot be read
	 */
	private void attachDb() throws SQLException, IOException {
		if (conn == null) {
			Properties p = loadDbProps();

			conn = DriverManager.getConnection(p.getProperty(DroolsPersistenceProperties.DB_URL),
					p.getProperty(DroolsPersistenceProperties.DB_USER),
					p.getProperty(DroolsPersistenceProperties.DB_PWD));
			conn.setAutoCommit(true);
		}
	}

	/**
	 * Loads the DB properties from the file,
	 * <i>feature-session-persistence.properties</i>.
	 * 
	 * @return the properties that were loaded
	 * @throws IOException
	 *             if the property file cannot be read
	 * @throws FileNotFoundException
	 *             if the property file does not exist
	 */
	private Properties loadDbProps() throws IOException, FileNotFoundException {

		Properties p = new Properties();

		try (FileReader rdr = new FileReader("src/test/resources/feature-session-persistence.properties")) {
			p.load(rdr);
		}

		return p;
	}

	/**
	 * Create a mock DB connection and statement.
	 * 
	 * @param retval
	 *            value to be returned when the statement is executed, or
	 *            negative to throw an exception
	 * @return the statement that will be returned by the connection
	 * @throws SQLException
	 */
	private PreparedStatement mockDbConn(int retval) throws SQLException {
		Connection c = mock(Connection.class);
		PreparedStatement s = mock(PreparedStatement.class);

		when(bds.getConnection()).thenReturn(c);
		when(fact.makeDataSource(any())).thenReturn(bds);
		when(c.prepareStatement(anyString())).thenReturn(s);

		if (retval < 0) {
			// should throw an exception
			when(s.executeUpdate()).thenThrow(new SQLException("expected exception"));

		} else {
			// should return the value
			when(s.executeUpdate()).thenReturn(retval);
		}

		return s;
	}

	/**
	 * A partial factory, which exports a few of the real methods, but overrides
	 * the rest.
	 */
	private class PartialFactory extends PersistenceFeature.Factory {

		@Override
		public TransactionManager getTransMgr() {
			return transmgr;
		}

		@Override
		public UserTransaction getUserTrans() {
			return usertrans;
		}

		@Override
		public TransactionSynchronizationRegistry getTransSyncReg() {
			return transreg;
		}

		@Override
		public KieServices getKieServices() {
			return kiesvc;
		}

		@Override
		public EntityManagerFactory makeEntMgrFact(Map<String, Object> props) {
			return emf;
		}

		@Override
		public PolicyController getPolicyController(PolicyContainer container) {
			return polctlr;
		}

	}
}
