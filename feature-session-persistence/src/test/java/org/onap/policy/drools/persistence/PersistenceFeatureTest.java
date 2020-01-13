/*-
 * ============LICENSE_START=======================================================
 * feature-session-persistence
 * ================================================================================
 * Copyright (C) 2017-2018, 2020 AT&T Intellectual Property. All rights reserved.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private static final String MY_KIE_BASE = "mybase";

    private static final String MY_SESS_NAME = "myname";

    private static final String MISSING_EXCEPTION = "missing exception";

    private static final String EXPECTED = "expected exception";

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
    private TransactionManager transmgr;
    private UserTransaction usertrans;
    private TransactionSynchronizationRegistry transreg;
    private PolicyController polctlr;
    private PolicyContainer polcont;
    private PolicySession polsess;
    private int emfCount;
    private int jpaCount;
    private String propName;

    private PersistenceFeature feat;

    /**
     * Setup before class.
     * 
     * @throws Exception exception
     */
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

    /**
     * Setup.
     * 
     * @throws Exception exception
     */
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
        transmgr = mock(TransactionManager.class);
        usertrans = mock(UserTransaction.class);
        transreg = mock(TransactionSynchronizationRegistry.class);
        polcont = mock(PolicyContainer.class);
        polctlr = mock(PolicyController.class);
        polsess = mock(PolicySession.class);
        emfCount = 0;
        jpaCount = 0;
        propName = null;

        feat = new PersistenceFeatureImpl();

        props.putAll(stdprops);

        System.setProperty("com.arjuna.ats.arjuna.objectstore.objectStoreDir", "target/tm");
        System.setProperty("ObjectStoreEnvironmentBean.objectStoreDir", "target/tm");

        when(kiesvc.newEnvironment()).thenReturn(kieenv);
        when(kiesvc.getStoreServices()).thenReturn(kiestore);
        when(kiesvc.newKieSessionConfiguration()).thenReturn(kiecfg);

        KieContainer kiecont = mock(KieContainer.class);
        when(polcont.getKieContainer()).thenReturn(kiecont);

        when(polsess.getPolicyContainer()).thenReturn(polcont);

        when(kiecont.getKieBase(anyString())).thenReturn(kiebase);
    }

    /**
     * Tear down.
     */
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
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        // force getContainerAdjunct() to be invoked
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

        verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());

        assertNotNull(adjcap.getValue());
    }

    @Test
    public void testGetContainerAdjunct_Existing() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        // force getContainerAdjunct() to be invoked
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

        verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());

        // return adjunct on next call
        when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

        // force getContainerAdjunct() to be invoked again
        setUpKie("myname2", 999L, true);
        mockDbConn(5);
        feat.activatePolicySession(polcont, "myname2", MY_KIE_BASE);

        // ensure it isn't invoked again
        verify(polcont, times(1)).setAdjunct(any(), any());
    }

    @Test
    public void testGetContainerAdjunct_WrongType() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        // return false adjunct on next call
        when(polcont.getAdjunct(any())).thenReturn("not-a-real-adjunct");

        // force getContainerAdjunct() to be invoked
        setUpKie("myname2", 999L, true);
        mockDbConn(5);
        feat.activatePolicySession(polcont, "myname2", MY_KIE_BASE);

        ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

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
        assertEquals("src/test/resources/feature-session-persistence.properties", propName);
    }

    @Test(expected = NullPointerException.class)
    public void testGlobalInitIoEx() throws Exception {

        feat = new PersistenceFeatureImpl() {
            @Override
            protected Properties loadProperties(String filenm) throws IOException {
                throw new IOException(EXPECTED);
            }
        };

        feat.globalInit(null, SRC_TEST_RESOURCES);
    }

    @Test
    public void testActivatePolicySession() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement ps = mockDbConn(5);
        
        feat.beforeActivate(null);

        KieSession session = feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(kiestore).loadKieSession(anyLong(), any(), any(), any());
        verify(kiestore, never()).newKieSession(any(), any(), any());

        assertEquals(session, kiesess);

        verify(ps).executeUpdate();

        verify(kieenv, times(4)).set(anyString(), any());

        verify(jpa).get(MY_SESS_NAME);
        verify(jpa).replace(any());
    }

    @Test
    public void testActivatePolicySession_NoPersistence() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement ps = mockDbConn(5);

        props.remove("persistence.type");

        feat.beforeStart(null);

        assertNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));

        verify(ps, never()).executeUpdate();
        verify(kiestore, never()).loadKieSession(anyLong(), any(), any(), any());
        verify(kiestore, never()).newKieSession(any(), any(), any());
    }

    /** Verifies that a new KIE session is created when there is no existing session entity. */
    @Test
    public void testActivatePolicySession_New() throws Exception {
        setUpKie("noName", 999L, true);
        mockDbConn(5);

        KieSession session = feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(kiestore, never()).loadKieSession(anyLong(), any(), any(), any());
        verify(kiestore).newKieSession(any(), any(), any());

        assertEquals(session, kiesess);

        verify(kieenv, times(4)).set(anyString(), any());

        verify(jpa).get(MY_SESS_NAME);
        verify(jpa).replace(any());
    }

    /**
     * Verifies that a new KIE session is created when there KIE fails to load an existing session.
     */
    @Test
    public void testActivatePolicySession_LoadFailed() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        KieSession session = feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(kiestore).loadKieSession(anyLong(), any(), any(), any());
        verify(kiestore).newKieSession(any(), any(), any());

        assertEquals(session, kiesess);

        verify(kieenv, times(4)).set(anyString(), any());

        verify(jpa).get(MY_SESS_NAME);

        ArgumentCaptor<DroolsSession> drools = ArgumentCaptor.forClass(DroolsSession.class);
        verify(jpa).replace(drools.capture());

        assertEquals(MY_SESS_NAME, drools.getValue().getSessionName());
        assertEquals(100L, drools.getValue().getSessionId());
    }

    @Test
    public void testLoadDataSource() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        assertEquals(1, emfCount);
    }

    @Test
    public void testConfigureSysProps() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        assertEquals("60", System.getProperty("com.arjuna.ats.arjuna.coordinator.defaultTimeout"));
        assertEquals(JTA_OSDIR, System.getProperty("com.arjuna.ats.arjuna.objectstore.objectStoreDir"));
        assertEquals(JTA_OSDIR, System.getProperty("ObjectStoreEnvironmentBean.objectStoreDir"));
    }

    @Test
    public void testConfigureKieEnv() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(kieenv, times(4)).set(any(), any());

        verify(kieenv).set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
        verify(kieenv).set(EnvironmentName.TRANSACTION, usertrans);
        verify(kieenv).set(EnvironmentName.TRANSACTION_MANAGER, transmgr);
        verify(kieenv).set(EnvironmentName.TRANSACTION_SYNCHRONIZATION_REGISTRY, transreg);

        verify(bds, times(1)).close();
    }

    @Test
    public void testConfigureKieEnv_RtEx() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);
        
        feat = new PersistenceFeatureMockDb() {
            @Override
            protected UserTransaction getUserTrans() {
                throw new IllegalArgumentException(EXPECTED);
            }
        };
        
        feat.globalInit(null, SRC_TEST_RESOURCES);

        try {
            feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
            fail(MISSING_EXCEPTION);

        } catch (IllegalArgumentException ex) {
            logger.trace(EXPECTED, ex);
        }

        verify(bds, times(2)).close();
    }

    @Test
    public void testLoadKieSession() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);
        
        KieSession session = feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(kiestore).loadKieSession(999L, kiebase, kiecfg, kieenv);
        verify(kiestore, never()).newKieSession(any(), any(), any());

        assertEquals(session, kiesess);
    }

    /*
     * Verifies that loadKieSession() returns null (thus causing newKieSession()
     * to be called) when an Exception occurs.
     */
    @Test
    public void testLoadKieSession_Ex() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        when(kiestore.loadKieSession(anyLong(), any(), any(), any()))
            .thenThrow(new IllegalArgumentException(EXPECTED));

        KieSession session = feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(kiestore).loadKieSession(anyLong(), any(), any(), any());
        verify(kiestore).newKieSession(any(), any(), any());

        assertEquals(session, kiesess);
    }

    @Test
    public void testNewKieSession() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        KieSession session = feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(kiestore).newKieSession(kiebase, null, kieenv);

        assertEquals(session, kiesess);
    }

    @Test
    public void testLoadDataSource_DiffSession() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

        verify(polcont).setAdjunct(any(), adjcap.capture());

        when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

        setUpKie("myname2", 999L, false);
        mockDbConn(5);

        // invoke it again
        feat.activatePolicySession(polcont, "myname2", MY_KIE_BASE);

        assertEquals(2, emfCount);
    }

    @Test
    public void testSelectThreadModel_Persistent() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);

        ThreadModel model = feat.selectThreadModel(polsess);
        assertNotNull(model);
        assertTrue(model instanceof PersistentThreadModel);
    }

    @Test
    public void testSelectThreadModel_NotPersistent() throws Exception {
        assertNull(feat.selectThreadModel(polsess));
    }

    @Test
    public void testSelectThreadModel_Start__Run_Update_Stop() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);

        ThreadModel model = feat.selectThreadModel(polsess);
        assertNotNull(model);
        assertTrue(model instanceof PersistentThreadModel);

        when(polsess.getKieSession()).thenReturn(kiesess);

        model.start();
        new CountDownLatch(1).await(10, TimeUnit.MILLISECONDS);
        model.updated();
        model.stop();
    }

    @Test
    public void testDisposeKieSession() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(emf, never()).close();

        final ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);
        
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
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(emf, never()).close();

        final ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);
        
        verify(polcont).setAdjunct(any(), adjcap.capture());

        when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

        // specify a session that was never loaded
        when(polsess.getName()).thenReturn("anotherName");

        feat.disposeKieSession(polsess);

        verify(emf, never()).close();
    }

    @Test
    public void testDestroyKieSession() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(emf, never()).close();

        final ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);
        
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
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(emf, never()).close();

        final ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);
        
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
        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement statement = mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(statement).executeUpdate();
    }

    @Test
    public void testGetPersistenceTimeout_Missing() throws Exception {

        props.remove(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT);

        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement statement = mockDbConn(0);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(statement, never()).executeUpdate();
    }

    @Test
    public void testGetPersistenceTimeout_Invalid() throws Exception {
        props.setProperty(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT, "abc");
        
        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement s = mockDbConn(0);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(s, never()).executeUpdate();
    }

    @Test
    public void testCleanUpSessionInfo() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);

        // use a real DB so we can verify that the "delete" works correctly
        feat = new PartialFeature();

        makeSessionInfoTbl(20000);

        // create mock entity manager for use by JPA connector
        EntityManager em = mock(EntityManager.class);
        when(emf.createEntityManager()).thenReturn(em);

        feat.globalInit(null, SRC_TEST_RESOURCES);

        feat.beforeStart(null);
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        assertEquals("[1, 4, 5]", getSessions().toString());
    }

    @Test
    public void testCleanUpSessionInfo_WithBeforeStart() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement statement = mockDbConn(0);

        // reset
        feat.beforeStart(null);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        verify(statement, times(1)).executeUpdate();

        // should not clean-up again
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        verify(statement, times(1)).executeUpdate();

        // reset
        feat.beforeStart(null);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        verify(statement, times(2)).executeUpdate();

        // should not clean-up again
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        verify(statement, times(2)).executeUpdate();
    }

    @Test
    public void testCleanUpSessionInfo_WithBeforeActivate() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement statement = mockDbConn(0);

        // reset
        feat.beforeActivate(null);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        verify(statement, times(1)).executeUpdate();

        // should not clean-up again
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        verify(statement, times(1)).executeUpdate();

        // reset
        feat.beforeActivate(null);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        verify(statement, times(2)).executeUpdate();

        // should not clean-up again
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
        verify(statement, times(2)).executeUpdate();
    }

    @Test
    public void testCleanUpSessionInfo_NoTimeout() throws Exception {

        props.remove(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT);

        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement statement = mockDbConn(0);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(statement, never()).executeUpdate();
    }

    @Test
    public void testCleanUpSessionInfo_NoUrl() throws Exception {
        props.remove(DroolsPersistenceProperties.DB_URL);
        
        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement statement = mockDbConn(0);

        try {
            feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
            fail(MISSING_EXCEPTION);
        } catch (RuntimeException e) {
            logger.trace(EXPECTED, e);
        }

        verify(statement, never()).executeUpdate();
    }

    @Test
    public void testCleanUpSessionInfo_NoUser() throws Exception {
        props.remove(DroolsPersistenceProperties.DB_USER);
        
        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement statement = mockDbConn(0);

        try {
            feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
            fail(MISSING_EXCEPTION);
        } catch (RuntimeException e) {
            logger.trace(EXPECTED, e);
        }

        verify(statement, never()).executeUpdate();
    }

    @Test
    public void testCleanUpSessionInfo_NoPassword() throws Exception {
        props.remove(DroolsPersistenceProperties.DB_PWD);

        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement statement = mockDbConn(0);

        try {
            feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
            fail(MISSING_EXCEPTION);
        } catch (RuntimeException e) {
            logger.trace(EXPECTED, e);
        }

        verify(statement, never()).executeUpdate();
    }

    @Test
    public void testCleanUpSessionInfo_SqlEx() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        final PreparedStatement statement = mockDbConn(-1);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        verify(statement).executeUpdate();
    }

    @Test
    public void testGetDroolsSessionConnector() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        assertEquals(1, jpaCount);
    }

    @Test
    public void testReplaceSession() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        final ArgumentCaptor<DroolsSession> sesscap = ArgumentCaptor.forClass(DroolsSession.class);
        
        verify(jpa).replace(sesscap.capture());

        assertEquals(MY_SESS_NAME, sesscap.getValue().getSessionName());
        assertEquals(999L, sesscap.getValue().getSessionId());
    }

    @Test
    public void testIsPersistenceEnabled_Auto() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        props.setProperty("persistence.type", "auto");

        assertNotNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));
    }

    @Test
    public void testIsPersistenceEnabled_Native() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        props.setProperty("persistence.type", "native");

        assertNotNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));
    }

    @Test
    public void testIsPersistenceEnabled_None() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        props.remove("persistence.type");

        assertNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));
    }

    @Test
    public void testGetProperties_Ex() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        feat = new PersistenceFeatureMockDb() {
            @Override
            protected PolicyController getPolicyController(PolicyContainer container) {
                throw new IllegalArgumentException(EXPECTED);
            }
        };

        assertNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));
    }

    @Test
    public void testGetProperty_Specific() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        props.remove("persistence.type");
        props.setProperty("persistence.myname.type", "auto");

        assertNotNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));
    }

    @Test
    public void testGetProperty_Specific_None() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        props.remove("persistence.type");
        props.setProperty("persistence.xxx.type", "auto");

        assertNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));
    }

    @Test
    public void testGetProperty_Both_SpecificOn() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        props.setProperty("persistence.type", "other");
        props.setProperty("persistence.myname.type", "auto");

        assertNotNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));
    }

    @Test
    public void testGetProperty_Both_SpecificDisabledOff() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        props.setProperty("persistence.type", "auto");
        props.setProperty("persistence.myname.type", "other");

        assertNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));
    }

    @Test
    public void testGetProperty_None() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, true);
        mockDbConn(5);

        props.remove("persistence.type");

        assertNull(feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE));
    }

    @Test
    public void testPersistenceFeatureException() {
        SecurityException secex = new SecurityException(EXPECTED);
        PersistenceFeatureException ex = new PersistenceFeatureException(secex);

        assertEquals(secex, ex.getCause());
    }

    @Test
    public void testDsEmf_RtEx() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat = new PersistenceFeatureMockDb() {
            @Override
            protected EntityManagerFactory makeEntMgrFact(Map<String, Object> props) {
                throw new IllegalArgumentException(EXPECTED);
            }
        };

        feat.globalInit(null, SRC_TEST_RESOURCES);

        try {
            feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);
            fail(MISSING_EXCEPTION);

        } catch (IllegalArgumentException ex) {
            logger.trace(EXPECTED, ex);
        }

        verify(bds, times(2)).close();
    }

    @Test
    public void testDsEmf_Close_RtEx() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

        verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());

        // return adjunct on next call
        when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

        try {
            doThrow(new IllegalArgumentException(EXPECTED)).when(emf).close();

            feat.destroyKieSession(polsess);
            fail(MISSING_EXCEPTION);

        } catch (IllegalArgumentException ex) {
            logger.trace(EXPECTED, ex);
        }

        verify(bds, times(2)).close();
    }

    @Test
    public void testDsEmf_CloseDataSource_RtEx() throws Exception {
        setUpKie(MY_SESS_NAME, 999L, false);
        mockDbConn(5);

        feat.activatePolicySession(polcont, MY_SESS_NAME, MY_KIE_BASE);

        ArgumentCaptor<PersistenceFeature.ContainerAdjunct> adjcap =
                ArgumentCaptor.forClass(PersistenceFeature.ContainerAdjunct.class);

        verify(polcont, times(1)).setAdjunct(any(), adjcap.capture());

        // return adjunct on next call
        when(polcont.getAdjunct(any())).thenReturn(adjcap.getValue());

        try {
            doThrow(new SQLException(EXPECTED)).when(bds).close();

            feat.destroyKieSession(polsess);
            fail(MISSING_EXCEPTION);

        } catch (PersistenceFeatureException ex) {
            logger.trace(EXPECTED, ex);
        }
    }

    /**
     * Gets an ordered list of ids of the current SessionInfo records.
     *
     * @return ordered list of SessInfo IDs
     * @throws SQLException sql exception
     * @throws IOException io exception
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
     * @param sessnm name to which JPA should respond with a session
     * @param sessid session id to be returned by the session
     * @param loadOk {@code true} if loadKieSession() should return a value, {@code false} to return
     *     null
     * @throws Exception exception
     */
    private void setUpKie(String sessnm, long sessid, boolean loadOk) throws Exception {
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

        feat = new PersistenceFeatureKie();
        feat.globalInit(null, SRC_TEST_RESOURCES);
    }

    /**
     * Creates the SessionInfo DB table and populates it with some data.
     *
     * @param expMs number of milli-seconds for expired sessioninfo records
     * @throws SQLException exception
     * @throws IOException exception
     */
    private void makeSessionInfoTbl(int expMs) throws SQLException, IOException {

        attachDb();

        try (PreparedStatement stmt =
                conn.prepareStatement("CREATE TABLE sessioninfo(id int, lastmodificationdate timestamp)")) {

            stmt.executeUpdate();
        }

        try (PreparedStatement stmt =
                conn.prepareStatement("INSERT into sessioninfo(id, lastmodificationdate) values(?, ?)")) {

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
     * @throws SQLException sql exception
     * @throws IOException if the property file cannot be read
     */
    private void attachDb() throws SQLException, IOException {
        if (conn == null) {
            Properties props = loadDbProps();

            conn =
                    DriverManager.getConnection(
                            props.getProperty(DroolsPersistenceProperties.DB_URL),
                            props.getProperty(DroolsPersistenceProperties.DB_USER),
                            props.getProperty(DroolsPersistenceProperties.DB_PWD));
            conn.setAutoCommit(true);
        }
    }

    /**
     * Loads the DB properties from the file, <i>feature-session-persistence.properties</i>.
     *
     * @return the properties that were loaded
     * @throws IOException if the property file cannot be read
     * @throws FileNotFoundException if the property file does not exist
     */
    private Properties loadDbProps() throws IOException, FileNotFoundException {

        Properties props = new Properties();

        try (FileReader rdr =
                new FileReader("src/test/resources/feature-session-persistence.properties")) {
            props.load(rdr);
        }

        return props;
    }

    /**
     * Create a mock DB connection and statement.
     *
     * @param retval value to be returned when the statement is executed, or negative to throw an
     *     exception
     * @return the statement that will be returned by the connection
     * @throws SQLException sql exception
     */
    private PreparedStatement mockDbConn(int retval) throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(bds.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        if (retval < 0) {
            // should throw an exception
            when(statement.executeUpdate()).thenThrow(new SQLException(EXPECTED));

        } else {
            // should return the value
            when(statement.executeUpdate()).thenReturn(retval);
        }

        feat = new PersistenceFeatureMockDb();
        feat.globalInit(null, SRC_TEST_RESOURCES);

        return statement;
    }
    
    /**
     * Feature with a mock DB.
     */
    private class PersistenceFeatureMockDb extends PersistenceFeatureKie {

        @Override
        protected BasicDataSource makeDataSource(Properties dsProps) {
            return bds;
        }
    }
    
    /**
     * Feature supporting newKieSession.
     */
    private class PersistenceFeatureKie extends PersistenceFeatureImpl {

        @Override
        protected EntityManagerFactory makeEntMgrFact(Map<String, Object> props) {
            ++emfCount;
            return emf;
        }

        @Override
        protected DroolsSessionConnector makeJpaConnector(EntityManagerFactory emf) {
            ++jpaCount;
            return jpa;
        }
    }
    
    /**
     * Feature with overrides.
     */
    private class PersistenceFeatureImpl extends PartialFeature {

        @Override
        protected Properties loadProperties(String filenm) throws IOException {
            propName = filenm;
            return props;
        }

        @Override
        protected BasicDataSource makeDataSource(Properties dsProps) {
            return null;
        }

        @Override
        protected DroolsSessionConnector makeJpaConnector(EntityManagerFactory emf) {
            ++jpaCount;
            return null;
        }
    }
    
    /**
     * Feature with <i>some</i> overrides.
     */
    private class PartialFeature extends PersistenceFeature {

        @Override
        protected TransactionManager getTransMgr() {
            return transmgr;
        }

        @Override
        protected UserTransaction getUserTrans() {
            return usertrans;
        }

        @Override
        protected TransactionSynchronizationRegistry getTransSyncReg() {
            return transreg;
        }

        @Override
        protected KieServices getKieServices() {
            return kiesvc;
        }

        @Override
        protected EntityManagerFactory makeEntMgrFact(Map<String, Object> props) {
            ++emfCount;
            return emf;
        }

        @Override
        protected PolicyController getPolicyController(PolicyContainer container) {
            return polctlr;
        }
    }
}
