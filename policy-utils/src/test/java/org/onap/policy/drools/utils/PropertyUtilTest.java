/*-
 * ============LICENSE_START=======================================================
 * policy-utils
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.utils;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyUtilTest {

    private static final Logger logger = LoggerFactory.getLogger(PropertyUtilTest.class);
    private static final String INTERPOLATION_PROPERTIES = "src/test/resources/interpolation.properties";
    private static final String INTERPOLATION_NO = "interpolation.no";
    private static final String INTERPOLATION_ENV = "interpolation.env";
    private static final String INTERPOLATION_CONST = "interpolation.const";
    private static final String INTERPOLATION_SYS = "interpolation.sys";

    private static File directory = null;

    /**
     * Test Setup -- Create a directory for temporary files.
     */
    @BeforeClass
    public static void setup() {
        logger.info("setup: creating a temporary directory");

        // create a directory for temporary files
        directory = new File(UUID.randomUUID().toString());
        directory.mkdir();
    }

    /**
     * Test Cleanup -- Remove temporary files.
     */
    @AfterClass
    public static void teardown() {
        logger.info("teardown: remove the temporary directory");

        // the assumption is that we only have one level of temporary files
        for (File file : directory.listFiles()) {
            file.delete();
        }
        directory.delete();
    }

    /**
     * Utility method to write a properties file.
     *
     * @param name the file name, relative to the temporary directory
     * @param properties to store in the file
     * @return a File instance associated with the newly-created file
     * @throws IOException if the file can't be created for some reason
     */
    File createFile(String name, Properties properties) throws IOException {
        File file = new File(directory, name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            properties.store(fos, "Property file '" + name + "'");
        }
        return (file);
    }

    /**
     * Create a 'PropertyUtil.Listener' subclass, which receives property
     * file updates. It stores the latest values in an array, and notifies
     * any thread waiting on this array.
     *
     * @param returns this is an array of length 2 -- the first entry will
     *     contain the 'properties' value, and the second will contain
     *     'changedKeys'. It is also used to signal any waiting thread
     *     using 'returns.notifyAll()'.
     */
    PropertyUtil.Listener createListenerThread(final Object[] returns) {
        return (new PropertyUtil.Listener() {
            public void propertiesChanged(Properties properties, Set<String> changedKeys) {
                // When a notification is received, store the values in the
                // 'returns' array, and signal using the same array.
                logger.info("Listener invoked: properties=" + properties
                        + ", changedKeys=" + changedKeys);
                returns[0] = properties;
                returns[1] = changedKeys;
                synchronized (returns) {
                    returns.notifyAll();
                }
            }
        });
    }

    /**
     * Test the basic properties file interface.
     */
    @Test
    public void testGetProperties() throws Exception {
        logger.info("testGetProperties: test the basic properties file interface");

        // copy system properties
        logger.info("Copy system properties to a file");
        Properties prop1 = System.getProperties();
        File file1 = createFile("createAndReadPropertyFile-1", prop1);

        // read in properties, and compare
        logger.info("Read in properties from new file");
        Properties prop2 = PropertyUtil.getProperties(file1);

        // they should match
        assertEquals(prop1, prop2);

        Properties prop3 = PropertyUtil.getProperties(INTERPOLATION_PROPERTIES);

        assertEquals("no", prop3.getProperty(INTERPOLATION_NO));
        assertEquals(System.getenv("HOME"), prop3.getProperty(INTERPOLATION_ENV));
        assertEquals(LoggerUtil.ROOT_LOGGER, prop3.getProperty(INTERPOLATION_CONST));
        assertEquals(System.getProperty("user.home"), prop3.getProperty(INTERPOLATION_SYS));

        Properties prop4 = new Properties();
        prop4.put(INTERPOLATION_NO, "no");
        prop4.put(INTERPOLATION_ENV, "${env:HOME}");
        prop4.put(INTERPOLATION_CONST, "${const:org.onap.policy.drools.utils.logging.LoggerUtil.ROOT_LOGGER}");
        prop4.put(INTERPOLATION_SYS, "${sys:user.home}");

        PropertyUtil.setSystemProperties(prop4);

        assertEquals("no", System.getProperty(INTERPOLATION_NO));
        assertEquals(System.getenv("HOME"), System.getProperty(INTERPOLATION_ENV));
        assertEquals(LoggerUtil.ROOT_LOGGER, System.getProperty(INTERPOLATION_CONST));
        assertEquals(System.getProperty("user.home"), System.getProperty(INTERPOLATION_SYS));
    }

    /**
     * This tests the 'PropertyUtil.Listener' interface.
     */
    @Test
    public void testListenerInterface() throws Exception {
        logger.info("testListenerInterface: test receipt of dynamic updates");

        // create initial property file
        Properties prop1 = new Properties();
        prop1.setProperty("p1", "p1 value");
        prop1.setProperty("p2", "p2 value");
        prop1.setProperty("p3", "p3 value");
        logger.info("Create initial properties file: " + prop1);
        File file1 = createFile("createAndReadPropertyFile-2", prop1);

        // create a listener for the notification interface
        Object[] returns = new Object[2];
        PropertyUtil.Listener listener = createListenerThread(returns);

        // read it in, and do a comparison
        Properties prop2 = PropertyUtil.getProperties(file1, listener);
        logger.info("Read in properties: " + prop2);
        assertEquals(prop1, prop2);
        assertEquals("p1 value", prop2.getProperty("p1"));
        assertEquals("p2 value", prop2.getProperty("p2"));
        assertEquals("p3 value", prop2.getProperty("p3"));

        // make some changes, and update the file (p3 is left unchanged)
        prop2.remove("p1"); // remove one property
        prop2.setProperty("p2", "new p2 value");    // change one property
        prop2.setProperty("p4", "p4 value");        // add a new property
        logger.info("Modified properties: " + prop2);

        // now, update the file, and wait for notification
        synchronized (returns) {
            createFile("createAndReadPropertyFile-2", prop2);

            // wait up to 60 seconds, although we should receive notification
            // in 10 seconds or less (if things are working)
            returns.wait(60000L);
        }

        // verify we have the updates
        assertEquals(prop2, returns[0]);

        // verify that we have the expected set of keys
        assertEquals(new TreeSet<String>(Arrays.asList(new String[]{"p1", "p2", "p4"})),
                returns[1]);
    }
}
