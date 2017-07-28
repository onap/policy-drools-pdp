package org.openecomp.policy.drools.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.AfterClass;
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
import org.openecomp.policy.drools.core.PolicyContainer;
import org.openecomp.policy.drools.core.PolicySession;
import org.openecomp.policy.drools.system.PolicyController;

import bitronix.tm.BitronixTransactionManager;
import bitronix.tm.Configuration;
import bitronix.tm.resource.jdbc.PoolingDataSource;

public class TestPersistenceFeature {

	private static final String JDBC_DATASRC = "fake.datasource";
	private static final String JDBC_DRIVER = "fake.driver";
	private static final String JDBC_URL = "fake.url";
	private static final String JDBC_USER = "fake.user";
	private static final String JDBC_PASSWD = "fake.password";

	private static Properties stdprops;

	private DroolsSessionConnector jpa;
	private DroolsSession sess;
	private PoolingDataSource pds;
	private KieSession kiesess;
	private Properties dsprops;
	private EntityManagerFactory emf;
	private Connection conn;
	private Properties props;
	private KieServices kiesvc;
	private Environment kieenv;
	private KieSessionConfiguration kiecfg;
	private KieBase kiebase;
	private KieStoreServices kiestore;
	private KieContainer kiecont;
	private Configuration bitcfg;
	private BitronixTransactionManager bittrans;
	private PolicyController polctlr;
	private PolicyContainer polcont;
	private PolicySession polsess;
	private PersistenceFeature.Factory fact;
	
	private PersistenceFeature feat;
	

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		stdprops = new Properties();

		stdprops.put(DroolsPersistenceProperties.DB_DATA_SOURCE, JDBC_DATASRC);
		stdprops.put(DroolsPersistenceProperties.DB_DRIVER, JDBC_DRIVER);
		stdprops.put(DroolsPersistenceProperties.DB_URL, JDBC_URL);
		stdprops.put(DroolsPersistenceProperties.DB_USER, JDBC_USER);
		stdprops.put(DroolsPersistenceProperties.DB_PWD, JDBC_PASSWD);
		stdprops.put(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT, "50");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		jpa = mock(DroolsSessionConnector.class);
		sess = mock(DroolsSession.class);
		pds = mock(PoolingDataSource.class);
		kiesess = mock(KieSession.class);
		dsprops = new Properties();
		emf = null;
		conn = null;
		props = new Properties();
		kiesvc = mock(KieServices.class);
		kieenv = mock(Environment.class);
		kiecfg = mock(KieSessionConfiguration.class);
		kiebase = mock(KieBase.class);
		kiestore = mock(KieStoreServices.class);
		kiecont = mock(KieContainer.class);
		bitcfg = mock(Configuration.class);
		bittrans = mock(BitronixTransactionManager.class);
		polcont = mock(PolicyContainer.class);
		polctlr = mock(PolicyController.class);
		polsess = mock(PolicySession.class);
		fact = mock(PersistenceFeature.Factory.class);
		
		feat = new PersistenceFeature();
		feat.setFactory(fact);
		
		props.putAll(stdprops);
		
		when(pds.getUniqueName()).thenReturn("myds");
		
		when(fact.getKieServices()).thenReturn(kiesvc);
		when(fact.getTransMgrConfig()).thenReturn(bitcfg);
		when(fact.getTransMgr()).thenReturn(bittrans);
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
		if(conn != null) {
			try { conn.close(); } catch (SQLException e) { }
		}

		if(emf != null) {
			try { emf.close(); } catch (Exception e) { }
		}
	}

	@Test
	public void testGetContainerAdjunct_New() throws Exception {

		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		// force getContainerAdjunct() to be invoked
		feat.activatePolicySession(polcont, "myname", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
				ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

		verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());
		
		assertNotNull( adjcap.getValue());
	}

	@Test
	public void testGetContainerAdjunct_Existing() throws Exception {

		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		// force getContainerAdjunct() to be invoked
		feat.activatePolicySession(polcont, "myname", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
				ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

		verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());
		
		// return adjunct on next call
		when(polcont.getAdjunct(any())).thenReturn( adjcap.getValue());

		// force getContainerAdjunct() to be invoked again
		setUpKie("myname2", 999L, true);
		feat.activatePolicySession(polcont, "myname2", "mybase");
		
		// ensure it isn't invoked again
		verify(polcont, times(1)).setAdjunct(any(), any());
	}

	@Test
	public void testGetSequenceNumber() {
		assertEquals(1, feat.getSequenceNumber());
	}

	@Test
	public void testGlobalInit() throws Exception {
		when(fact.getHostName()).thenReturn("myhost");
		
		feat.globalInit(null, "src/test/resources");
		
		// verify that various factory methods were invoked
//		verify(fact).getEngineMgr();
		verify(fact).getHostName();
		verify(fact).getKieServices();
		verify(fact).getTransMgrConfig();
		verify(fact).loadProperties("src/test/resources/droolsPersistence.properties");
		
		verify(bitcfg).setJournal(null);
		verify(bitcfg).setServerId("myhost");
	}

	@Test
	public void testActivatePolicySession() throws Exception {
		PreparedStatement ps = mockDbConn(5);
		setUpKie("myname", 999L, true);

		feat.globalInit(null, "src/test/resources");
		feat.beforeActivate(null);
		
		KieSession s =
				feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore).loadKieSession(anyLong(), any(), any(), any());
		verify(kiestore, never()).newKieSession(any(), any(), any());
		
		assertEquals(s, kiesess);
		
		verify(ps).executeUpdate();

		verify(kieenv, times(2)).set(anyString(), any());
		verify(pds).init();
		assertFalse( dsprops.isEmpty());
		
		verify(jpa).get("myname");
		verify(jpa).replace(any());
	}

	@Test
	public void testActivatePolicySession_NoPersistence() throws Exception {
		feat.globalInit(null, "src/test/resources");

		PreparedStatement ps = mockDbConn(5);
		setUpKie("myname", 999L, true);
		
		props.remove("persistence.type");
		
		feat.beforeStart(null);
		
		assertNull( feat.activatePolicySession(polcont, "myname", "mybase"));

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
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("noName", 999L, true);
		
		
		KieSession s =
				feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore, never()).loadKieSession(anyLong(), any(), any(), any());
		verify(kiestore).newKieSession(any(), any(), any());
		
		assertEquals(s, kiesess);

		verify(kieenv, times(2)).set(anyString(), any());
		verify(pds).init();
		assertFalse( dsprops.isEmpty());

		verify(jpa).get("myname");
		verify(jpa).replace(any());
	}

	/**
	 * Verifies that a new KIE session is created when there KIE fails
	 * to load an existing session.
	 */
	@Test
	public void testActivatePolicySession_LoadFailed() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		
		KieSession s =
				feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore).loadKieSession(anyLong(), any(), any(), any());
		verify(kiestore).newKieSession(any(), any(), any());
		
		assertEquals(s, kiesess);

		verify(kieenv, times(2)).set(anyString(), any());
		verify(pds).init();
		assertFalse( dsprops.isEmpty());

		verify(jpa).get("myname");
		
		ArgumentCaptor<DroolsSession> d =
				ArgumentCaptor.forClass(DroolsSession.class);
		verify(jpa).replace( d.capture());
		
		assertEquals("myname", d.getValue().getSessionName());
		assertEquals(100L, d.getValue().getSessionId());
	}

	@Test
	public void testConfigureKieEnv() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kieenv, times(2)).set(any(), any());
		
		verify(kieenv).set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
		verify(kieenv).set(EnvironmentName.TRANSACTION_MANAGER, bittrans);
	}

	@Test
	public void testInitDataSource() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		assertEquals(JDBC_URL, dsprops.getProperty("URL"));
		assertEquals(JDBC_USER, dsprops.getProperty("user"));
		assertEquals(JDBC_PASSWD, dsprops.getProperty("password"));
		
		verify(pds).setUniqueName("jdbc/BitronixJTADataSource/myname");
		verify(pds).setClassName(JDBC_DATASRC);
		verify(pds).setMaxPoolSize(anyInt());
		verify(pds).setIsolationLevel("SERIALIZABLE");
		verify(pds).setAllowLocalTransactions(true);
		verify(pds).init();
	}

	@Test
	public void testLoadKieSession() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);
		
		
		KieSession s =
				feat.activatePolicySession(polcont, "myname", "mybase");

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
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		when(kiestore.loadKieSession(anyLong(), any(), any(), any()))
				.thenThrow( new RuntimeException("expected exception"));
		
		
		KieSession s =
				feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore).loadKieSession(anyLong(), any(), any(), any());
		verify(kiestore).newKieSession(any(), any(), any());
		
		assertEquals(s, kiesess);
	}

	@Test
	public void testNewKieSession() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		
		KieSession s =
				feat.activatePolicySession(polcont, "myname", "mybase");

		verify(kiestore).newKieSession(kiebase, null, kieenv);
		
		assertEquals(s, kiesess);
	}

	@Test
	public void testLoadDataSource_RepeatSameSession() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, false);

		feat.activatePolicySession(polcont, "myname", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
				ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);
		
		verify(polcont).setAdjunct(any(), adjcap.capture());
		
		when(polcont.getAdjunct(any())).thenReturn( adjcap.getValue());
		
		// invoke it again
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(fact, times(1)).makePoolingDataSource();
	}

	@Test
	public void testLoadDataSource_DiffSession() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		feat.activatePolicySession(polcont, "myname", "mybase");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
				ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);
		
		verify(polcont).setAdjunct(any(), adjcap.capture());
		
		when(polcont.getAdjunct(any())).thenReturn( adjcap.getValue());

		setUpKie("myname2", 999L, false);
		
		// invoke it again
		feat.activatePolicySession(polcont, "myname2", "mybase");
		
		verify(fact, times(2)).makePoolingDataSource();
	}

	@Test
	public void testDisposeKieSession() throws Exception {
		feat.globalInit(null, "src/test/resources");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
				ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(pds, never()).close();
		verify(polcont).setAdjunct(any(), adjcap.capture());
		
		when(polcont.getAdjunct(any())).thenReturn( adjcap.getValue());
		
		feat.disposeKieSession(polsess);

		// call twice to ensure it isn't re-closed
		feat.disposeKieSession(polsess);
		
		verify(pds, times(1)).close();
	}

	@Test
	public void testDisposeKieSession_NoAdjunct() throws Exception {
		feat.globalInit(null, "src/test/resources");
		
		feat.disposeKieSession(polsess);
	}

	@Test
	public void testDisposeKieSession_NoPersistence() throws Exception {
		feat.globalInit(null, "src/test/resources");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
				ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(pds, never()).close();
		verify(polcont).setAdjunct(any(), adjcap.capture());
		
		when(polcont.getAdjunct(any())).thenReturn( adjcap.getValue());

		// specify a session that was never loaded
		when(polsess.getName()).thenReturn("anotherName");
		
		feat.disposeKieSession(polsess);
		
		verify(pds, never()).close();
	}

	@Test
	public void testDestroyKieSession() throws Exception {
		feat.globalInit(null, "src/test/resources");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
				ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(pds, never()).close();
		verify(polcont).setAdjunct(any(), adjcap.capture());
		
		when(polcont.getAdjunct(any())).thenReturn( adjcap.getValue());
		
		feat.destroyKieSession(polsess);

		// call twice to ensure it isn't re-closed
		feat.destroyKieSession(polsess);
		
		verify(pds, times(1)).close();
	}

	@Test
	public void testDestroyKieSession_NoAdjunct() throws Exception {
		feat.globalInit(null, "src/test/resources");
		
		feat.destroyKieSession(polsess);
	}

	@Test
	public void testDestroyKieSession_NoPersistence() throws Exception {
		feat.globalInit(null, "src/test/resources");

		ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
				ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

		mockDbConn(5);
		setUpKie("myname", 999L, false);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(pds, never()).close();
		verify(polcont).setAdjunct(any(), adjcap.capture());
		
		when(polcont.getAdjunct(any())).thenReturn( adjcap.getValue());

		// specify a session that was never loaded
		when(polsess.getName()).thenReturn("anotherName");
		
		feat.destroyKieSession(polsess);

		verify(pds, never()).close();
	}

	@Test
	public void testAfterStart() {
		assertFalse( feat.afterStart(null));
	}

	@Test
	public void testBeforeStart() {
		assertFalse( feat.beforeStart(null));
	}

	@Test
	public void testBeforeShutdown() {
		assertFalse( feat.beforeShutdown(null));
	}

	@Test
	public void testAfterShutdown() {
		assertFalse( feat.afterShutdown(null));
	}

	@Test
	public void testBeforeConfigure() {
		assertFalse( feat.beforeConfigure(null, null));
	}

	@Test
	public void testAfterConfigure() {
		assertFalse( feat.afterConfigure(null));
	}

	@Test
	public void testBeforeActivate() {
		assertFalse( feat.beforeActivate(null));
	}

	@Test
	public void testAfterActivate() {
		assertFalse( feat.afterActivate(null));
	}

	@Test
	public void testBeforeDeactivate() {
		assertFalse( feat.beforeDeactivate(null));
	}

	@Test
	public void testAfterDeactivate() {
		assertFalse( feat.afterDeactivate(null));
	}

	@Test
	public void testBeforeStop() {
		assertFalse( feat.beforeStop(null));
	}

	@Test
	public void testAfterStop() {
		assertFalse( feat.afterStop(null));
	}

	@Test
	public void testBeforeLock() {
		assertFalse( feat.beforeLock(null));
	}

	@Test
	public void testAfterLock() {
		assertFalse( feat.afterLock(null));
	}

	@Test
	public void testBeforeUnlock() {
		assertFalse( feat.beforeUnlock(null));
	}

	@Test
	public void testAfterUnlock() {
		assertFalse( feat.afterUnlock(null));
	}

	@Test
	public void testGetPersistenceTimeout_Valid() throws Exception {
		PreparedStatement s = mockDbConn(5);

		feat.globalInit(null, "src/test/resources");

		setUpKie("myname", 999L, true);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(s).executeUpdate();
	}

	@Test
	public void testGetPersistenceTimeout_Missing() throws Exception {
		
		props.remove(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT);
		
		PreparedStatement s = mockDbConn(0);

		feat.globalInit(null, "src/test/resources");

		setUpKie("myname", 999L, true);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(s, never()).executeUpdate();
	}

	@Test
	public void testGetPersistenceTimeout_Invalid() throws Exception {
		props.setProperty(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT, "abc");
		PreparedStatement s = mockDbConn(0);

		feat.globalInit(null, "src/test/resources");

		setUpKie("myname", 999L, true);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(s, never()).executeUpdate();
	}

	@Test
	public void testInitHostName() throws Exception {
		when(fact.getHostName()).thenReturn("myhost");
		
		feat.globalInit(null, "src/test/resources");
		
		verify(bitcfg).setServerId("myhost");
	}

	@Test(expected = RuntimeException.class)
	public void testInitHostName_Ex() throws Exception {
		when(fact.getHostName())
				.thenThrow(
						new UnknownHostException("expected exception"));
		
		feat.globalInit(null, "src/test/resources");
	}

	@Test
	public void testCleanUpSessionInfo() throws Exception {
		setUpKie("myname", 999L, true);
		
		// use a real DB so we can verify that the "delete" works correctly
		fact = new PartialFactory();
		feat.setFactory(fact);
		
		makeSessionInfoTbl(20000);

		feat.globalInit(null, "src/test/resources");
		
		feat.beforeStart(null);
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		assertEquals("[1, 4, 5]", getSessions().toString());
	}

	@Test
	public void testCleanUpSessionInfo_WithBeforeStart() throws Exception {
		PreparedStatement s = mockDbConn(0);

		feat.globalInit(null, "src/test/resources");

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

		feat.globalInit(null, "src/test/resources");

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

		feat.globalInit(null, "src/test/resources");

		setUpKie("myname", 999L, true);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(s, never()).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_NoUrl() throws Exception {
		PreparedStatement s = mockDbConn(0);
		
		props.remove(DroolsPersistenceProperties.DB_URL);

		feat.globalInit(null, "src/test/resources");

		setUpKie("myname", 999L, true);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(s, never()).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_NoUser() throws Exception {
		PreparedStatement s = mockDbConn(0);
		
		props.remove(DroolsPersistenceProperties.DB_USER);

		feat.globalInit(null, "src/test/resources");

		setUpKie("myname", 999L, true);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(s, never()).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_NoPassword() throws Exception {
		PreparedStatement s = mockDbConn(0);
		
		props.remove(DroolsPersistenceProperties.DB_PWD);

		feat.globalInit(null, "src/test/resources");

		setUpKie("myname", 999L, true);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(s, never()).executeUpdate();
	}

	@Test
	public void testCleanUpSessionInfo_SqlEx() throws Exception {
		PreparedStatement s = mockDbConn(-1);

		feat.globalInit(null, "src/test/resources");

		setUpKie("myname", 999L, true);
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(s).executeUpdate();
	}

	@Test
	public void testGetDroolsSessionConnector() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);
		
		
		feat.activatePolicySession(polcont, "myname", "mybase");


		ArgumentCaptor<Properties> propcap =
				ArgumentCaptor.forClass(Properties.class);
		
		verify(fact).makeJpaConnector(anyString(), propcap.capture());
		
		Properties p = propcap.getValue();
		assertNotNull(p);
		
		assertEquals(JDBC_DRIVER,
				p.getProperty("javax.persistence.jdbc.driver"));
		
		assertEquals(JDBC_URL,
				p.getProperty("javax.persistence.jdbc.url"));
		
		assertEquals(JDBC_USER,
				p.getProperty("javax.persistence.jdbc.user"));
		
		assertEquals(JDBC_PASSWD,
				p.getProperty("javax.persistence.jdbc.password"));
	}

	@Test
	public void testReplaceSession() throws Exception {
		feat.globalInit(null, "src/test/resources");

		ArgumentCaptor<DroolsSession> sesscap =
				ArgumentCaptor.forClass(DroolsSession.class);

		mockDbConn(5);
		setUpKie("myname", 999L, true);
		
		
		feat.activatePolicySession(polcont, "myname", "mybase");
		
		verify(jpa).replace( sesscap.capture());
		
		assertEquals("myname", sesscap.getValue().getSessionName());
		assertEquals(999L, sesscap.getValue().getSessionId());
	}
	
	@Test
	public void testIsPersistenceEnabled_Auto() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.setProperty("persistence.type", "auto");
		
		assertNotNull( feat.activatePolicySession(polcont, "myname", "mybase"));
	}
	
	@Test
	public void testIsPersistenceEnabled_Native() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.setProperty("persistence.type", "native");
		
		assertNotNull( feat.activatePolicySession(polcont, "myname", "mybase"));
	}
	
	@Test
	public void testIsPersistenceEnabled_None() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.remove("persistence.type");
		
		assertNull( feat.activatePolicySession(polcont, "myname", "mybase"));
	}
	
	@Test
	public void testGetProperties_Ex() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);
		
		when(fact.getPolicyContainer(polcont))
			.thenThrow( new IllegalArgumentException("expected exception"));

		assertNull( feat.activatePolicySession(polcont, "myname", "mybase"));
	}
	
	@Test
	public void testGetProperty_Specific() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.remove("persistence.type");
		props.setProperty("persistence.myname.type", "auto");
		
		assertNotNull( feat.activatePolicySession(polcont, "myname", "mybase"));
	}
	
	@Test
	public void testGetProperty_Specific_None() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.remove("persistence.type");
		props.setProperty("persistence.xxx.type", "auto");
		
		assertNull( feat.activatePolicySession(polcont, "myname", "mybase"));
	}
	
	@Test
	public void testGetProperty_Both_SpecificOn() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.setProperty("persistence.type", "other");
		props.setProperty("persistence.myname.type", "auto");
		
		assertNotNull( feat.activatePolicySession(polcont, "myname", "mybase"));
	}
	
	@Test
	public void testGetProperty_Both_SpecificDisabledOff() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.setProperty("persistence.type", "auto");
		props.setProperty("persistence.myname.type", "other");
		
		assertNull( feat.activatePolicySession(polcont, "myname", "mybase"));
	}
	
	@Test
	public void testGetProperty_None() throws Exception {
		feat.globalInit(null, "src/test/resources");

		mockDbConn(5);
		setUpKie("myname", 999L, true);

		props.remove("persistence.type");
		
		assertNull( feat.activatePolicySession(polcont, "myname", "mybase"));
	}

	
	/**
	 * Gets an ordered list of ids of the current SessionInfo records.
	 * @return ordered list of SessInfo IDs
	 * @throws SQLException 
	 * @throws IOException 
	 */
	private List<Integer> getSessions() throws SQLException, IOException {
		attachDb();
		
		ArrayList<Integer> lst = new ArrayList<>(5);

		try(
				PreparedStatement stmt = conn.prepareStatement("SELECT id from sessioninfo order by id");
				ResultSet rs = stmt.executeQuery()) {
			
			while(rs.next()) {
				lst.add( rs.getInt(1));
			}
		}
		
		return lst;
	}
	
	/**
	 * Sets up for doing invoking the newKieSession() method.
	 * @param sessnm	name to which JPA should respond with a session
	 * @param sessid	session id to be returned by the session
	 * @param loadOk 	{@code true} if loadKieSession() should return a
	 * 					value, {@code false} to return null
	 */
	private void setUpKie(String sessnm, long sessid, boolean loadOk) {
		
		when(fact.makeJpaConnector(any(), any())).thenReturn(jpa);
		when(fact.makePoolingDataSource()).thenReturn(pds);
		when(fact.getPolicyContainer(polcont)).thenReturn(polctlr);
		
		props.setProperty("persistence.type", "auto");
		
		when(polctlr.getProperties()).thenReturn(props);
		
		when(jpa.get(sessnm)).thenReturn(sess);
		
		when(pds.getDriverProperties()).thenReturn(dsprops);
		
		when(sess.getSessionId()).thenReturn(sessid);
		
		when(polsess.getPolicyContainer()).thenReturn(polcont);
		when(polsess.getName()).thenReturn(sessnm);
		
		if(loadOk) {
			when(kiesess.getIdentifier()).thenReturn(sessid);
			when(kiestore.loadKieSession(anyLong(), any(), any(), any()))
					.thenReturn(kiesess);
			
		} else {
			// use an alternate id for the new session
			when(kiesess.getIdentifier()).thenReturn(100L);
			when(kiestore.loadKieSession(anyLong(), any(), any(), any()))
					.thenReturn(null);
		}

		when(kiestore.newKieSession(any(), any(), any())).thenReturn(kiesess);
	}

	/**
	 * Creates the SessionInfo DB table and populates it with some data.
	 * @param expMs 	number of milli-seconds for expired sessioninfo records
	 * @throws SQLException 
	 * @throws IOException 
	 */
	private void makeSessionInfoTbl(int expMs)
				throws SQLException, IOException {
		
		attachDb();

		try(
				PreparedStatement stmt = conn.prepareStatement(
						"CREATE TABLE sessioninfo(id int, lastmodificationdate timestamp)")) {

			stmt.executeUpdate();	
		}

		try(
				PreparedStatement stmt = conn.prepareStatement(
						"INSERT into sessioninfo(id, lastmodificationdate) values(?, ?)")) {

			Timestamp ts;
			
			// current data
			ts = new Timestamp( System.currentTimeMillis());
			stmt.setTimestamp(2, ts);
	
			stmt.setInt(1, 1);
			stmt.executeUpdate();
	
			stmt.setInt(1, 4);
			stmt.executeUpdate();
	
			stmt.setInt(1, 5);
			stmt.executeUpdate();
			
			// expired data
			ts = new Timestamp( System.currentTimeMillis() - expMs);
			stmt.setTimestamp(2, ts);
	
			stmt.setInt(1, 2);
			stmt.executeUpdate();
	
			stmt.setInt(1, 3);
			stmt.executeUpdate();
		}
	}

	/**
	 * Attaches {@link #conn} to the DB, if it isn't already attached.
	 * @throws SQLException
	 * @throws IOException if the property file cannot be read
	 */
	private void attachDb() throws SQLException, IOException {
		if(conn == null) {
			Properties p = loadDbProps();
			
			conn = DriverManager.getConnection(
						p.getProperty(DroolsPersistenceProperties.DB_URL),
						p.getProperty(DroolsPersistenceProperties.DB_USER),
						p.getProperty(DroolsPersistenceProperties.DB_PWD));
			conn.setAutoCommit(true);
		}
	}

	/**
	 * Loads the DB properties from the file,
	 * <i>droolsPersistence.properties</i>.
	 * @return the properties that were loaded
	 * @throws IOException if the property file cannot be read
	 * @throws FileNotFoundException if the property file does not exist
	 */
	private Properties loadDbProps()
				throws IOException, FileNotFoundException {
		
		Properties p = new Properties();
		
		try(FileReader rdr = new FileReader(
				"src/test/resources/droolsPersistence.properties")) {
			p.load(rdr);
		}
		
		return p;
	}

	/**
	 * Create a mock DB connection and statement.
	 * @param retval	value to be returned when the statement is executed,
	 * 					or negative to throw an exception
	 * @return the statement that will be returned by the connection
	 * @throws SQLException
	 */
	private PreparedStatement mockDbConn(int retval) throws SQLException {
		Connection c = mock(Connection.class);
		PreparedStatement s = mock(PreparedStatement.class);

		when(fact.makeDbConnection(anyString(), anyString(), anyString()))
				.thenReturn(c);
		when(c.prepareStatement(anyString())).thenReturn(s);
		
		if(retval < 0) {
			// should throw an exception
			when(s.executeUpdate())
				.thenThrow( new SQLException("expected exception"));
			
		} else {
			// should return the value
			when(s.executeUpdate()).thenReturn(retval);
		}
		
		return s;
	}
	
	/**
	 * A partial factory, which exports a few of the real methods, but
	 * overrides the rest.
	 */
	private class PartialFactory extends PersistenceFeature.Factory {
		
		@Override
		public PoolingDataSource makePoolingDataSource() {
			return pds;
		}

		@Override
		public KieServices getKieServices() {
			return kiesvc;
		}

		@Override
		public BitronixTransactionManager getTransMgr() {
			return null;
		}

		@Override
		public EntityManagerFactory makeEntMgrFact(String pu,
													Properties propMap) {
			if(pu.equals("ncompsessionsPU")) {
				return null;
			}
			
			return super.makeEntMgrFact("junitPersistenceFeaturePU", propMap);
		}

		@Override
		public PolicyController getPolicyContainer(PolicyContainer container) {
			return polctlr;
		}
		
	}
}
