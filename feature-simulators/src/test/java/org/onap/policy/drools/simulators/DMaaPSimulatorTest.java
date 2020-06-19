/*-
 * ============LICENSE_START=======================================================
 * feature-simulators
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

package org.onap.policy.drools.simulators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.utils.logging.LoggerUtil;

public class DMaaPSimulatorTest {

    private static int DMAAPSIM_SERVER_PORT;

    /**
     * Setup the simulator.
     * @throws IOException if a server port cannot be allocated
     */
    @BeforeClass
    public static void setUpSimulator() throws IOException {
        LoggerUtil.setLevel("ROOT", "INFO");
        LoggerUtil.setLevel("org.eclipse.jetty", "WARN");
        DMAAPSIM_SERVER_PORT = NetworkUtil.allocPort();
        try {
            final HttpServletServer testServer = HttpServletServerFactoryInstance.getServerFactory().build("dmaapSim",
                            "localhost", DMAAPSIM_SERVER_PORT, "/", false, true);
            testServer.addServletClass("/*", DMaaPSimulatorJaxRs.class.getName());
            testServer.waitedStart(5000);
            if (!NetworkUtil.isTcpPortOpen("localhost", testServer.getPort(), 5, 10000L)) {
                throw new IllegalStateException("cannot connect to port " + testServer.getPort());
            }
        } catch (final Exception e) {
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void tearDownSimulator() {
        HttpServletServerFactoryInstance.getServerFactory().destroy();
    }

    @Test
    public void testGetNoData() {
        int timeout = 1000;
        Pair<Integer, String> response = dmaapGet("myTopicNoData", timeout);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(DMaaPSimulatorJaxRs.NO_TOPIC_MSG, response.second);
    }

    @Test
    public void testSinglePost() {
        String myTopic = "myTopicSinglePost";
        String testData = "This is some test data";
        Pair<Integer, String> response = dmaapPost(myTopic, testData);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapGet(myTopic, 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(testData, response.second);
    }

    @Test
    public void testOneTopicMultiPost() {
        String[] data = {"data point 1", "data point 2", "something random"};
        String myTopic = "myTopicMultiPost";
        Pair<Integer, String> response = dmaapPost(myTopic, data[0]);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapPost(myTopic, data[1]);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapPost(myTopic, data[2]);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapGet(myTopic, 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(data[0], response.second);

        response = dmaapGet(myTopic, 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(data[1], response.second);

        response = dmaapGet(myTopic, 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(data[2], response.second);
    }

    @Test
    public void testMultiTopic() {
        String[][] data = {{"Topic one message one", "Topic one message two"},
            {"Topic two message one", "Topic two message two"}};
        String[] topics = {"topic1", "topic2"};

        Pair<Integer, String> response = dmaapPost(topics[0], data[0][0]);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapGet(topics[0], 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(data[0][0], response.second);

        response = dmaapGet(topics[1], 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(DMaaPSimulatorJaxRs.NO_TOPIC_MSG, response.second);

        response = dmaapPost(topics[1], data[1][0]);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapPost(topics[1], data[1][1]);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapPost(topics[0], data[0][1]);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapGet(topics[1], 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(data[1][0], response.second);

        response = dmaapGet(topics[0], 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(data[0][1], response.second);

        response = dmaapGet(topics[1], 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(data[1][1], response.second);

        response = dmaapGet(topics[0], 1000);
        assertNotNull(response);
        assertNotNull(response.first);
        assertEquals(DMaaPSimulatorJaxRs.NO_DATA_MSG, response.second);
    }

    @Test
    public void testResponseCode() {
        Pair<Integer, String> response = dmaapPost("myTopic", "myTopicData");
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = setStatus(503);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapGet("myTopic", 500);
        assertNotNull(response);
        assertEquals(503, response.first.intValue());
        assertEquals("You got response code: 503", response.second);

        response = setStatus(202);
        assertNotNull(response);
        assertNotNull(response.first);
        assertNotNull(response.second);

        response = dmaapGet("myTopic", 500);
        assertNotNull(response);
        assertEquals(202, response.first.intValue());
        assertEquals("myTopicData", response.second);
    }

    private static Pair<Integer, String> dmaapGet(String topic, int timeout) {
        return dmaapGet(topic, "1", "1", timeout);
    }

    private static Pair<Integer, String> dmaapGet(String topic, String consumerGroup, String consumerId, int timeout) {
        String url = "http://localhost:" + DMAAPSIM_SERVER_PORT + "/events/" + topic + "/" + consumerGroup + "/"
                + consumerId + "?timeout=" + timeout;
        try {
            URLConnection conn = new URL(url).openConnection();
            HttpURLConnection httpConn = null;
            if (conn instanceof HttpURLConnection) {
                httpConn = (HttpURLConnection) conn;
            } else {
                fail("connection not set up right");
            }
            httpConn.setRequestMethod("GET");
            httpConn.connect();
            String response = "";
            try (BufferedReader connReader = new BufferedReader(new InputStreamReader(httpConn.getInputStream()))) {
                String line;
                while ((line = connReader.readLine()) != null) {
                    response += line;
                }
                httpConn.disconnect();
                return new Pair<Integer, String>(httpConn.getResponseCode(), response);
            } catch (IOException e) {
                if (e.getMessage().startsWith("Server returned HTTP response code")) {
                    System.out.println("hi");
                    BufferedReader connReader = new BufferedReader(new InputStreamReader(httpConn.getErrorStream()));
                    String line;
                    while ((line = connReader.readLine()) != null) {
                        response += line;
                    }
                    httpConn.disconnect();
                    return new Pair<Integer, String>(httpConn.getResponseCode(), response);
                } else {
                    fail("we got an exception: " + e);
                }
            }
        } catch (Exception e) {
            fail("we got an exception" + e);
        }

        return null;
    }

    private static Pair<Integer, String> dmaapPost(String topic, String data) {
        String url = "http://localhost:" + DMAAPSIM_SERVER_PORT + "/events/" + topic;
        byte[] postData = data.getBytes(StandardCharsets.UTF_8);
        try {
            URLConnection conn = new URL(url).openConnection();
            HttpURLConnection httpConn = null;
            if (conn instanceof HttpURLConnection) {
                httpConn = (HttpURLConnection) conn;
            } else {
                fail("connection not set up right");
            }
            httpConn.setRequestMethod("POST");
            httpConn.setDoOutput(true);
            httpConn.setRequestProperty("Content-Type", "text/plain");
            httpConn.setRequestProperty("Content-Length", "" + postData.length);
            httpConn.connect();
            String response = "";
            try (DataOutputStream connWriter = new DataOutputStream(httpConn.getOutputStream())) {
                connWriter.write(postData);
                connWriter.flush();
            }
            try (BufferedReader connReader = new BufferedReader(new InputStreamReader(httpConn.getInputStream()))) {
                String line;
                while ((line = connReader.readLine()) != null) {
                    response += line;
                }
                httpConn.disconnect();
                return new Pair<Integer, String>(httpConn.getResponseCode(), response);
            } catch (IOException e) {
                if (e.getMessage().startsWith("Server returned HTTP response code")) {
                    System.out.println("hi");
                    BufferedReader connReader = new BufferedReader(new InputStreamReader(httpConn.getErrorStream()));
                    String line;
                    while ((line = connReader.readLine()) != null) {
                        response += line;
                    }
                    httpConn.disconnect();
                    return new Pair<Integer, String>(httpConn.getResponseCode(), response);
                } else {
                    fail("we got an exception: " + e);
                }
            }
        } catch (Exception e) {
            fail("we got an exception: " + e);
        }
        return null;
    }

    private static Pair<Integer, String> setStatus(int status) {
        String url = "http://localhost:" + DMAAPSIM_SERVER_PORT + "/events/setStatus?statusCode=" + status;
        try {
            URLConnection conn = new URL(url).openConnection();
            HttpURLConnection httpConn = null;
            if (conn instanceof HttpURLConnection) {
                httpConn = (HttpURLConnection) conn;
            } else {
                fail("connection not set up right");
            }
            httpConn.setRequestMethod("POST");
            httpConn.connect();
            String response = "";
            try (BufferedReader connReader = new BufferedReader(new InputStreamReader(httpConn.getInputStream()))) {
                String line;
                while ((line = connReader.readLine()) != null) {
                    response += line;
                }
                httpConn.disconnect();
                return new Pair<Integer, String>(httpConn.getResponseCode(), response);
            } catch (IOException e) {
                if (e.getMessage().startsWith("Server returned HTTP response code")) {
                    System.out.println("hi");
                    BufferedReader connReader = new BufferedReader(new InputStreamReader(httpConn.getErrorStream()));
                    String line;
                    while ((line = connReader.readLine()) != null) {
                        response += line;
                    }
                    httpConn.disconnect();
                    return new Pair<Integer, String>(httpConn.getResponseCode(), response);
                } else {
                    fail("we got an exception: " + e);
                }
            }
        } catch (Exception e) {
            fail("we got an exception" + e);
        }
        return null;
    }

    private static class Pair<A, B> {
        public final A first;
        public final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }
}
