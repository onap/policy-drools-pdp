/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.utils.security.CryptoUtils;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyUtilTest {
    /*
     * Note: to generate the encrypted values, invoke CryptoUtils passing both the value
     * to be encrypted and the crypto key.
     *
     * The INTERPOLATION_CRYPTO_KEY is a 16 or 32 character string, base-64 encoded.
     *
     * For "INTERPOLATION_ENC_HELLOWORLD", the encrypted value was generated via:
     *  java org.onap.policy.common.utils.security.CryptoUtils enc HelloWorld MTIzNDU2Nzg5MDEyMzQ1Ng==
     *
     * The generated value should also be placed into the following properties within
     * the file, interpolation.properties:
     *  interpolation.enc
     *  interpolation.enc2
     *  interpolation.envenc
     */

    private static final String INTERPOLATION_PROPERTIES = "src/test/resources/interpolation.properties";
    private static final String INTERPOLATION_CRYPTO_KEY = "MTIzNDU2Nzg5MDEyMzQ1Ng==";
    private static final String INTERPOLATION_PLAINTEXT = "HelloWorld";
    private static final String INTERPOLATION_ENVD_DEFAULT_VALUE = "default";
    private static final String INTERPOLATION_ENC_HELLOWORLD =
                    "enc:MjGhDZTTIx1ihB7KvxLnOJcvb0WN/CSgpw7sY1hDnvL1VHa8wGRzOX3X";
    private static final String INTERPOLATION_ENC_HELLOWORLD_VAR = "${" + INTERPOLATION_ENC_HELLOWORLD + "}";

    private static final String INTERPOLATION_NO = "interpolation.no";
    private static final String INTERPOLATION_ENV = "interpolation.env";
    private static final String INTERPOLATION_ENVD = "interpolation.envd";
    private static final String INTERPOLATION_CONST = "interpolation.const";
    private static final String INTERPOLATION_SYS = "interpolation.sys";
    private static final String INTERPOLATION_ENVD_NONE = "interpolation.envd.none";
    private static final String INTERPOLATION_ENVD_DEFAULT = "interpolation.envd.default";
    private static final String INTERPOLATION_ENVD_NO_DEFAULT = "interpolation.envd.nodefault";
    private static final String INTERPOLATION_ENC = "interpolation.enc";
    private static final String INTERPOLATION_ENC2 = "interpolation.enc2";
    private static final String INTERPOLATION_ENVENC = "interpolation.envenc";


    private static final Logger logger = LoggerFactory.getLogger(PropertyUtilTest.class);

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

        // tests performed in sequence
        testGetCryptoCoderArg();
        testGetNoCryptoProps();
        testGetDefaultCryptoProps();
        testGetNoCryptoSystemProps();
        testGetCryptoArgSystemProps();
        testGetDefaultCryptoSystemProps();

    }

    private void testGetDefaultCryptoSystemProps() throws IOException {
        // system properties + default crypto coder
        PropertyUtil.setDefaultCryptoCoder(new CryptoUtils(INTERPOLATION_CRYPTO_KEY));
        PropertyUtil.setSystemProperties(PropertyUtil.getPropertiesFile(new File(INTERPOLATION_PROPERTIES)));
        assertPropInterpolation(System.getProperties());
        assertPropEncInterpolation(System.getProperties());
    }

    private void testGetCryptoArgSystemProps() throws IOException {
        // system properties + crypto coder passed in
        PropertyUtil
            .setSystemProperties(PropertyUtil
                .getPropertiesFile(new File(INTERPOLATION_PROPERTIES)), new CryptoUtils(INTERPOLATION_CRYPTO_KEY));
        assertPropInterpolation(System.getProperties());
        assertPropEncInterpolation(System.getProperties());
    }

    private void testGetNoCryptoSystemProps() throws IOException {
        /* system properties + no crypto coder */
        PropertyUtil.setDefaultCryptoCoder(null);
        PropertyUtil.setSystemProperties(PropertyUtil.getPropertiesFile(new File(INTERPOLATION_PROPERTIES)));
        assertPropInterpolation(System.getProperties());
        assertPropNoEncInterpolation(System.getProperties());
    }

    private void testGetDefaultCryptoProps() throws IOException {
        /* properties + default crypto coder */
        PropertyUtil.setDefaultCryptoCoder(new CryptoUtils(INTERPOLATION_CRYPTO_KEY));
        Properties props = PropertyUtil.getProperties(INTERPOLATION_PROPERTIES);
        assertPropInterpolation(props);
        assertPropEncInterpolation(props);
    }

    private void testGetNoCryptoProps() throws IOException {
        /* properties + no crypto coder */
        Properties props = PropertyUtil.getProperties(INTERPOLATION_PROPERTIES);
        assertPropInterpolation(props);
        assertPropNoEncInterpolation(props);
    }

    private void testGetCryptoCoderArg() throws IOException {
        /* properties + crypto coder passed in */
        Properties props =
            PropertyUtil.getProperties(INTERPOLATION_PROPERTIES, new CryptoUtils(INTERPOLATION_CRYPTO_KEY));
        assertPropInterpolation(props);
        assertPropEncInterpolation(props);
    }

    private void assertPropNoEncInterpolation(Properties props) {
        assertEquals(INTERPOLATION_ENC_HELLOWORLD_VAR, props.getProperty(INTERPOLATION_ENC));
        assertEquals(INTERPOLATION_ENC_HELLOWORLD, props.getProperty(INTERPOLATION_ENC2));
        assertEquals(INTERPOLATION_ENC_HELLOWORLD, props.getProperty(INTERPOLATION_ENVENC));
    }

    private void assertPropEncInterpolation(Properties props) {
        assertEquals(INTERPOLATION_PLAINTEXT, props.getProperty(INTERPOLATION_ENC));
        assertEquals(INTERPOLATION_PLAINTEXT, props.getProperty(INTERPOLATION_ENC2));
        assertEquals(INTERPOLATION_PLAINTEXT, props.getProperty(INTERPOLATION_ENVENC));
    }

    private void assertPropInterpolation(Properties props) {
        assertEquals("no", props.getProperty(INTERPOLATION_NO));
        assertEquals(System.getenv("HOME"), props.getProperty(INTERPOLATION_ENV));
        assertEquals(System.getenv("HOME"), props.getProperty(INTERPOLATION_ENVD));
        assertEquals(StringUtils.EMPTY, props.getProperty(INTERPOLATION_ENVD_NONE));
        assertEquals(StringUtils.EMPTY, props.getProperty(INTERPOLATION_ENVD_NO_DEFAULT));
        assertEquals(LoggerUtil.ROOT_LOGGER, props.getProperty(INTERPOLATION_CONST));
        assertEquals(System.getProperty("user.home"), props.getProperty(INTERPOLATION_SYS));
        assertEquals(INTERPOLATION_ENVD_DEFAULT_VALUE, props.getProperty(INTERPOLATION_ENVD_DEFAULT));
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
