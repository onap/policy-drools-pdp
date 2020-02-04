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

package org.onap.policy.drools.serverpooltest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

import org.kie.api.runtime.KieSession;
import org.onap.policy.drools.serverpool.Util;

/**
 * This is a common base class for 6 'AdapterImpl' instances, all running
 * with their own copies of the server pool classes, and a number of the ONAP
 * classes. The purpose is to simulate 6 separate hosts in a server pool.
 * Note that there is potentially a 7th copy of any of these classes, which is
 * the one loaded with the system class loader. Ideally, those classes
 * shouldn't be referred to, but there were some problems during testing,
 * where they unexpectedly were (prompting a change in
 * 'ExtendedObjectInputStream'). This is referred to as the 'null' host,
 * where the classes may exist, but have not gone through initialization.
 */
public abstract class Adapter {
    // 'true' indicates that initialization is still needed
    private static boolean initNeeded = true;

    // Each 'Adapter' instance is implemented by 'AdapterImpl', but loaded
    // with a different class loader that provides each with a different copy
    // of the set of classes with packages in the list below (see references to
    // 'BlockingClassLoader').
    public static Adapter[] adapters = new Adapter[6];

    /**
     * Ensure that all adapters have been initialized.
     */
    public static void ensureInit() throws Exception {
        synchronized (Adapter.class) {
            if (initNeeded) {
                initNeeded = false;

                // start DMAAP Simulator
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        SimDmaap.start();
                    }
                }, "DMAAP Simulator").start();

                // wait 1 second to allow time for the port 3904 listener
                Thread.sleep(1000);

                // build 'BlockingClassLoader'
                BlockingClassLoader bcl = new BlockingClassLoader(
                    Adapter.class.getClassLoader(),
                    // All 'org.onap.policy.*' classes are adapter-specific, except
                    // for the exclusions listed below.
                    "org.onap.policy.*"
                );
                bcl.addExclude("org.onap.policy.drools.core.DroolsRunnable");
                bcl.addExclude("org.onap.policy.drools.serverpooltest.*");

                // build URL list for class loader
                URL[] urls = {};

                // iterate through 'adapter' entries
                ClassLoader saveClassLoader =
                    Thread.currentThread().getContextClassLoader();
                if (saveClassLoader instanceof URLClassLoader) {
                    urls = ((URLClassLoader)saveClassLoader).getURLs();
                } else {
                    // the parent is not a 'URLClassLoader' --
                    // try to get this information from 'java.class.path'
                    ArrayList<URL> tmpUrls = new ArrayList<>();
                    for (String entry : System.getProperty("java.class.path").split(
                        Character.toString(File.pathSeparatorChar))) {
                        if (new File(entry).isDirectory()) {
                            tmpUrls.add(new URL("file:" + entry + "/"));
                        } else {
                            tmpUrls.add(new URL("file:" + entry));
                        }
                    }
                    urls = tmpUrls.toArray(new URL[0]);
                }
                try {
                    for (int i = 0 ; i < adapters.length ; i += 1) {
                        // Build a new 'ClassLoader' for this adapter. The
                        // 'ClassLoader' hierarchy is:
                        //
                        // AdapterClassLoader(one copy per Adapter) ->
                        // BlockingClassLoader ->
                        // base ClassLoader (with the complete URL list)
                        ClassLoader classLoader =
                            new AdapterClassLoader(i, urls, bcl);

                        // set the current thread class loader, which should be
                        // inherited by any child threads created during
                        // the initialization of the adapter
                        Thread.currentThread().setContextClassLoader(classLoader);

                        // now, build the adapter -- it is not just a new instance,
                        // but a new copy of class 'AdapterImpl'
                        Adapter adapter = (Adapter) classLoader.loadClass(
                            "org.onap.policy.drools.serverpool.AdapterImpl")
                            .newInstance();

                        // initialize the adapter
                        adapter.init(i);
                        adapters[i] = adapter;
                    }
                } finally {
                    // restore the class loader to that used during the Junit tests
                    Thread.currentThread().setContextClassLoader(saveClassLoader);
                }
            }
        }
    }

    /**
     * Shut everything down.
     */
    public static void ensureShutdown() {
        for (Adapter adapter : adapters) {
            adapter.shutdown();
        }
        SimDmaap.stop();
        // not sure why the following is started
        Util.shutdown();
    }

    /**
     * Runs server pool initialization for a particular host.
     *
     * @param index the index of the adapter (0-5)
     */
    public abstract void init(int index) throws Exception;

    /**
     * Shuts down the server pool for this host.
     */
    public abstract void shutdown();

    /**
     * Return a 'LinkedBlockingQueue' instance, which is used as a way for
     *     Drools code to signal back to running Junit tests.
     *
     * @return a 'LinkedBlockingQueue' instance, which is used as a way for
     *     Drools code to signal back to running Junit tests
     */
    public abstract LinkedBlockingQueue<String> notificationQueue();

    /**
     * This method blocks and waits for all buckets to have owners, or for
     * a timeout, whichever occurs first.
     *
     * @param endTime the point at which timeout occurs
     * @return 'true' if all buckets have owners, 'false' if a timeout occurred
     */
    public abstract boolean waitForInit(long endTime) throws InterruptedException;

    /**
     * Return an object providing indirect references to a select set of
     *     static 'Server' methods.
     *
     * @return an object providing indirect references to a select set of
     *     static 'Server' methods
     */
    public abstract ServerWrapper.Static getServerStatic();

    /**
     * Return an object providing an indirect reference to the lead 'Server'
     *     object.
     *
     * @return an object providing an indirect reference to the lead 'Server'
     *     object
     */
    public abstract ServerWrapper getLeader();

    /**
     * Return an object providing indirect references to a select set of
     *     static 'Bucket' methods.
     *
     * @return an object providing indirect references to a select set of
     *     static 'Bucket' methods
     */
    public abstract BucketWrapper.Static getBucketStatic();

    /**
     * Create a new 'TargetLock' instance, returning an indirect reference.
     *
     * @param key string key identifying the lock
     * @param ownerKey string key identifying the owner, which must hash to
     *     a bucket owned by the current host (it is typically a 'RequestID')
     * @param owner owner of the lock (will be notified when going from
     *     WAITING to ACTIVE)
     * @param waitForLock this controls the behavior when 'key' is already
     *     locked - 'true' means wait for it to be freed, 'false' means fail
     */
    public abstract TargetLockWrapper newTargetLock(
        String key, String ownerKey, TargetLockWrapper.Owner owner,
        boolean waitForLock);

    /**
     * Create a new 'TargetLock' instance, returning an indirect reference.
     *
     * @param key string key identifying the lock
     * @param ownerKey string key identifying the owner, which must hash to
     *     a bucket owned by the current host (it is typically a 'RequestID')
     * @param owner owner of the lock (will be notified when going from
     *     WAITING to ACTIVE)
     */
    public abstract TargetLockWrapper newTargetLock(
        String key, String ownerKey, TargetLockWrapper.Owner owner);

    /**
     * Call 'TargetLock.DumpLocks.dumpLocks'
     *
     * @param out where the output should be displayed
     * @param detail 'true' provides additional bucket and host information
     *     (but abbreviates all UUIDs in order to avoid excessive
     *     line length)
     */
    public abstract void dumpLocks(PrintStream out, boolean detail);

    /**
     * Create and initialize PolicyController 'TestController', and start
     * the associated Drools container and session.
     *
     * @return a string containing controller session information
     */
    public abstract String createController();

    /**
     * Send an event in the form of a JSON message string. The message is
     * sent to JUNIT-TEST-TOPIC, and the JSON object is converted to a
     * 'TestDroolsObject' (all compatible with the Drools session created by
     * 'createController'.
     *
     * @param key determines the bucket number, which affects which host the
     *     message is eventually routed to
     */
    public abstract void sendEvent(String key);

    /**
     * Return the one-and-only 'KieSession' on this host.
     *
     * @return the one-and-only 'KieSession' on this host
     */
    public abstract KieSession getKieSession();

    /**
     * Insert an object into the one-and-only Drools session.
     *
     * @param object the object to insert
     */
    public abstract void insertDrools(Object object);

    // some test utilities

    /**
     * Determine whether any of the objects passed as parameters are of a class
     * that belongs to different adapter. Print messages are displayed
     * for any that do occur.
     *
     * @param objects one or more objects to be tested
     * @return 'true' if one or more are foreign
     */
    public abstract boolean isForeign(Object... objects);

    /**
     * This method is used to generate keys that hash to a bucket associated
     * with a particular server. The algorithm generates a key using 'prefix'
     * concatenated with a numeric value, and searches for the first one on
     * the desired host. It will try up to 10000 indices before giving up --
     * each host owns 1/6 of the buckets, should the 10000 number should be
     * way more than enough. The tests are written with the assumption that
     * a valid key will be returned, and 'NullPointerException' is an acceptable
     * way to handle the situation if this doesn't work out somehow.
     *
     * @param prefix the first portion of the key
     * @param startingIndex the first index to try
     * @param host this indicates the 'Server' instance to locate, which must
     *     not be foreign to this adapter
     * @return a key associated with 'host' ('null' if not found)
     */
    public abstract String findKey(String prefix, int startingIndex, ServerWrapper host);

    /**
     * Equivalent to 'findKey(prefix, startingIndex, THIS-SERVER)'.
     *
     * @param prefix the first portion of the key
     * @param startingIndex the first index to try
     * @return a key associated with 'host' ('null' if not found)
     */
    public abstract String findKey(String prefix, int startingIndex);

    /**
     * Equivalent to 'findKey(prefix, 1, THIS-SERVER)'.
     *
     * @param prefix the first portion of the key
     * @return a key associated with 'host' ('null' if not found)
     */
    public abstract String findKey(String prefix);

    /* ============================================================ */

    /**
     * This class is basically a 'URLClassLoader', but with a 'toString()'
     * method that indicates the host and adapter number.
     */
    public static class AdapterClassLoader extends URLClassLoader {
        private int index;

        public AdapterClassLoader(int index, URL[] urls, ClassLoader parent) {
            super(urls, parent);
            this.index = index;
        }

        @Override
        public String toString() {
            return "AdapterClassLoader(" + index + ")";
        }
    }
}
