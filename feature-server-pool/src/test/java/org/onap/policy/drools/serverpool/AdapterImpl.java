/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.serverpool;

import static org.awaitility.Awaitility.await;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.kie.api.runtime.KieSession;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.PolicySessionFeatureApiConstants;
import org.onap.policy.drools.serverpooltest.Adapter;
import org.onap.policy.drools.serverpooltest.BucketWrapper;
import org.onap.policy.drools.serverpooltest.ServerWrapper;
import org.onap.policy.drools.serverpooltest.TargetLockWrapper;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.drools.util.KieUtils;
import org.onap.policy.drools.utils.PropertyUtil;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the 'Adapter' interface. There is one 'AdapterImpl'
 * class for each simulated host, and one instance of each 'AdapterImpl' class.
 */
public class AdapterImpl extends Adapter {
    private static Logger logger = LoggerFactory.getLogger(AdapterImpl.class);
    /*
     * Each 'AdapterImpl' instance has it's own class object, making it a
     * singleton. There is only a single 'Adapter' class object, and all
     * 'AdapterImpl' classes are derived from it.
     *
     * Sonar thinks this field isn't used.  However, it's value is actually
     * retrieved via Whitebox, below.  Thus it is marked "protected" instead
     * of "private" to avoid the sonar complaint.
     */
    protected static AdapterImpl adapter = null;

    // this is the adapter index
    private int index;

    // this will refer to the Drools session 'PolicyController' instance
    private PolicyController policyController = null;

    // this will refer to the Drools session 'PolicySession' instance
    private PolicySession policySession = null;

    // used by Drools session to signal back to Junit tests
    private LinkedBlockingQueue<String> inotificationQueue =
        new LinkedBlockingQueue<>();

    // provides indirect references to a select set of static 'Server' methods
    private static ServerWrapper.Static serverStatic =
        new ServerWrapperImpl.Static();

    // provides indirect references to a select set of static 'Bucket' methods
    private static BucketWrapper.Static bucketStatic =
        new BucketWrapperImpl.Static();

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(int index) throws Exception {
        adapter = this;
        this.index = index;

        PolicyEngineConstants.getManager().configure(new Properties());
        PolicyEngineConstants.getManager().start();
        /*
         * Note that this method does basically what
         * 'FeatureServerPool.afterStart(PolicyEngine)' does, but allows us to
         * specify different properties for each of the 6 simulated hosts
         */
        logger.info("{}: Running: AdapterImpl.init({}), class hash code = {}",
                    this, index, AdapterImpl.class.hashCode());
        final String propertyFile = "src/test/resources/feature-server-pool-test.properties";
        Properties prop = PropertyUtil.getProperties(propertyFile);
        if (System.getProperty("os.name").toLowerCase().indexOf("mac") < 0) {
            // Window, Unix
            String[] ipComponent = prop.getProperty("server.pool.server.ipAddress").split("[.]");
            String serverIP = ipComponent[0] + "." + ipComponent[1] + "." + ipComponent[2] + "."
                + (Integer.parseInt(ipComponent[3]) + index);
            prop.setProperty("server.pool.server.ipAddress", serverIP);
        } else {
            // Mac, use localhost and different ports
            String port = Integer.toString(Integer.parseInt(
                prop.getProperty("server.pool.server.port")) + index);
            prop.setProperty("server.pool.server.port", port);
        }
        logger.info("server={}, serverIP={}, port={}", index,
            prop.getProperty("server.pool.server.ipAddress"),
            prop.getProperty("server.pool.server.port"));

        TargetLock.startup();
        Server.startup(prop);

        // use reflection to set private static field
        // 'FeatureServerPool.droolsTimeoutMillis'
        Whitebox.setInternalState(FeatureServerPool.class, "droolsTimeoutMillis",
            ServerPoolProperties.DEFAULT_BUCKET_DROOLS_TIMEOUT);

        // use reflection to set private static field
        // 'FeatureServerPool.timeToLiveSecond'
        Whitebox.setInternalState(FeatureServerPool.class, "timeToLiveSecond",
            String.valueOf(ServerPoolProperties.DEFAULT_BUCKET_TIME_TO_LIVE));

        // use reflection to call private static method
        // 'FeatureServerPool.buildKeywordTable()'
        Whitebox.invokeMethod(FeatureServerPool.class, "buildKeywordTable");

        Bucket.Backup.register(new FeatureServerPool.DroolsSessionBackup());
        Bucket.Backup.register(new TargetLock.LockBackup());

        // dump out feature lists
        logger.info("{}: ServerPoolApi features list: {}",
                    this, ServerPoolApi.impl.getList());
        logger.info("{}: PolicySessionFeatureApi features list: {}",
                    this, PolicySessionFeatureApiConstants.getImpl().getList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        policyController.stop();
        Server.shutdown();

        PolicyEngineConstants.getManager().stop();
        PolicyEngineConstants.getManager().getExecutorService().shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkedBlockingQueue<String> notificationQueue() {
        return inotificationQueue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForInit(long endTime) throws InterruptedException {
        try {
            // wait until a leader is elected
            await().atMost(endTime - System.currentTimeMillis(),
                    TimeUnit.MILLISECONDS).until(() -> Leader.getLeader() != null);

            // wait for each bucket to have an owner
            for (int i = 0; i < Bucket.BUCKETCOUNT; i += 1) {
                Bucket bucket = Bucket.getBucket(i);
                while (bucket.getOwner() == null) {
                    await().atMost(Math.min(endTime - System.currentTimeMillis(), 100L), TimeUnit.MILLISECONDS);
                }
            }
        } catch (IllegalArgumentException e) {
            // 'Thread.sleep()' was passed a negative time-out value --
            // time is up
            logger.debug("AdapterImpl waitForInit error", e);
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerWrapper.Static getServerStatic() {
        return serverStatic;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerWrapper getLeader() {
        return ServerWrapperImpl.getWrapper(Leader.getLeader());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BucketWrapper.Static getBucketStatic() {
        return bucketStatic;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TargetLockWrapper newTargetLock(
        String key, String ownerKey, TargetLockWrapper.Owner owner, boolean waitForLock) {

        return TargetLockWrapperImpl.newTargetLock(key, ownerKey, owner, waitForLock);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TargetLockWrapper newTargetLock(String key, String ownerKey, TargetLockWrapper.Owner owner) {
        return TargetLockWrapperImpl.newTargetLock(key, ownerKey, owner);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dumpLocks(PrintStream out, boolean detail) {
        try {
            TargetLock.DumpLocks.dumpLocks(out, detail);
        } catch (Exception e) {
            logger.error("{}: Exception in 'dumpLocks'", this, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String createController() {
        Properties properties;
        /*
         * set the thread class loader to be the same as the one associated
         * with the 'AdapterImpl' instance, so it will be inherited by any
         * new threads created (the Drools session thread, in particular)
         */
        ClassLoader saveClassLoader =
            Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(AdapterImpl.class.getClassLoader());

        try {
            // build and install Drools artifact
            KieUtils.installArtifact(
                Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/META-INF/kmodule.xml").toFile(),
                Paths.get("src/test/resources/drools-artifact-1.1/pom.xml").toFile(),
                "src/main/resources/rules/org/onap/policy/drools/core/test/rules.drl",
                Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/rules.drl").toFile());

            // load properties from file
            properties = PropertyUtil.getProperties("src/test/resources/TestController-controller.properties");
        } catch (Exception e) {
            e.printStackTrace();
            Thread.currentThread().setContextClassLoader(saveClassLoader);
            return e.toString();
        }

        StringBuilder sb = new StringBuilder();
        try {
            // create and start 'PolicyController'
            policyController = PolicyEngineConstants.getManager()
                .createPolicyController("TestController", properties);
            policyController.start();

            // dump out container information (used for debugging tests)
            sb.append("PolicyContainer count: ")
            .append(PolicyContainer.getPolicyContainers().size()).append('\n');
            for (PolicyContainer policyContainer :
                    PolicyContainer.getPolicyContainers()) {
                sb.append("    name = ")
                    .append(policyContainer.getName())
                    .append('\n')
                    .append("    session count = ")
                    .append(policyContainer.getPolicySessions().size())
                    .append('\n');
                for (PolicySession pc : policyContainer.getPolicySessions()) {
                    policySession = pc;
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(saveClassLoader);
        }
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendEvent(String key) {
        /*
         * Note: the dumping out of package information was useful in tracking
         * down strange Drools behavior that was eventually tied to the
         * Drools class loader.
         */
        logger.info("{}: Calling 'sendEvent': packages = {}", this,
                    policySession.getKieSession().getKieBase().getKiePackages());
        ((TopicListener) policyController).onTopicEvent(
            CommInfrastructure.UEB, "JUNIT-TEST-TOPIC",
            "{\"key\":\"" + key + "\"}");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KieSession getKieSession() {
        return policySession == null ? null : policySession.getKieSession();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void insertDrools(Object object) {
        if (policySession != null) {
            /*
             * this will eventually be changed to use the
             * 'PolicySession.insertObject(...)' method
             */
            new FeatureServerPool().insertDrools(policySession, object);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isForeign(Object... objects) {
        boolean rval = false;
        ClassLoader myClassLoader = AdapterImpl.class.getClassLoader();
        for (Object o : objects) {
            Class<?> clazz = o.getClass();
            ClassLoader objClassLoader = clazz.getClassLoader();

            try {
                if (myClassLoader != objClassLoader
                        && clazz != myClassLoader.loadClass(clazz.getName())) {
                    rval = true;
                    logger.info("{}: FOREIGN OBJECT ({}) - {}",
                                this, getAdapter(objClassLoader), o);
                }
            } catch (ClassNotFoundException e) {
                rval = true;
                logger.error("{}: FOREIGN OBJECT -- CLASS NOT FOUND ({}) - {}",
                             this, getAdapter(objClassLoader), o);
            }
        }
        return rval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String findKey(String prefix, int startingIndex, ServerWrapper host) {
        String rval = null;

        // try up to 10000 numeric values to locate one on a particular host
        for (int i = 0; i < 10000; i += 1) {
            // generate key, and see if it is on the desired server
            String testString = prefix + (startingIndex + i);
            if (ServerWrapperImpl.getWrapper(
                Bucket.bucketToServer(Bucket.bucketNumber(testString))) == host) {
                // we have one that works
                rval = testString;
                break;
            }
        }
        return rval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String findKey(String prefix, int startingIndex) {
        return findKey(prefix, startingIndex, serverStatic.getThisServer());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String findKey(String prefix) {
        return findKey(prefix, 1, serverStatic.getThisServer());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "AdapterImpl[" + index + "]";
    }

    /**
     * Return an Adapter.
     *
     * @return the 'Adapter' instance associated with the ClassLoader associated
     *     with the current thread
     */
    public static Adapter getAdapter() {
        /*
         * Note that 'return(adapter)' doesn't work as expected when called from
         * within a 'Drools' session, because of the strange way that the Drools
         * 'ClassLoader' works -- it bypasses 'AdapterClassLoader' when doing
         * class lookups, even though it is the immediate parent of the Drools
         * session class loader.
         */
        return getAdapter(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Return an Adapter.
     *
     * @param classLoader a ClassLoader instance
     * @return the 'Adapter' instance associated with the specified ClassLoader
     */
    public static Adapter getAdapter(ClassLoader classLoader) {
        try {
            // locate the 'AdapterImpl' class associated with a particular
            // 'ClassLoader' (which may be different from the current one)
            Class<?> thisAdapterClass =
                classLoader.loadClass("org.onap.policy.drools.serverpool.AdapterImpl");

            // return the 'adapter' field value
            return Whitebox.getInternalState(thisAdapterClass, "adapter");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
