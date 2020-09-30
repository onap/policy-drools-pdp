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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
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
    public void testGetNoData() throws IOException {
        validateResponse(DMaaPSimulatorJaxRs.NO_TOPIC_MSG, dmaapGet("myTopicNoData", 1000));
    }

    @Test
    public void testSinglePost() throws IOException {
        String myTopic = "myTopicSinglePost";
        String testData = "This is some test data";

        validateResponse(dmaapPost(myTopic, testData));

        validateResponse(testData, dmaapGet(myTopic, 1000));
    }

    @Test
    public void testOneTopicMultiPost() throws IOException {
        String[] data = {"data point 1", "data point 2", "something random"};
        String myTopic = "myTopicMultiPost";

        validateResponse(dmaapPost(myTopic, data[0]));
        validateResponse(dmaapPost(myTopic, data[1]));
        validateResponse(dmaapPost(myTopic, data[2]));

        validateResponse(data[0], dmaapGet(myTopic, 1000));
        validateResponse(data[1], dmaapGet(myTopic, 1000));
        validateResponse(data[2], dmaapGet(myTopic, 1000));
    }

    @Test
    public void testMultiTopic() throws IOException {
        String[][] data = {{"Topic one message one", "Topic one message two"},
            {"Topic two message one", "Topic two message two"}};
        String[] topics = {"topic1", "topic2"};

        validateResponse(dmaapPost(topics[0], data[0][0]));

        validateResponse(data[0][0], dmaapGet(topics[0], 1000));
        validateResponse(DMaaPSimulatorJaxRs.NO_TOPIC_MSG, dmaapGet(topics[1], 1000));

        validateResponse(dmaapPost(topics[1], data[1][0]));
        validateResponse(dmaapPost(topics[1], data[1][1]));
        validateResponse(dmaapPost(topics[0], data[0][1]));

        validateResponse(data[1][0], dmaapGet(topics[1], 1000));
        validateResponse(data[0][1], dmaapGet(topics[0], 1000));
        validateResponse(data[1][1], dmaapGet(topics[1], 1000));
        validateResponse(DMaaPSimulatorJaxRs.NO_DATA_MSG, dmaapGet(topics[0], 1000));
    }

    @Test
    public void testResponseCode() throws IOException {
        validateResponse(dmaapPost("myTopic", "myTopicData"));

        validateResponse(setStatus(503));
        validateResponse(503, "You got response code: 503", dmaapGet("myTopic", 500));

        validateResponse(setStatus(202));
        validateResponse(202, "myTopicData", dmaapGet("myTopic", 500));
    }

    private void validateResponse(Pair<Integer, String> response) {
        assertNotNull(response);
        assertNotNull(response.getLeft());
        assertNotNull(response.getRight());
    }

    private void validateResponse(int expectedCode, String expectedResponse, Pair<Integer, String> response) {
        assertNotNull(response);
        assertEquals(expectedCode, response.getLeft().intValue());
        assertEquals(expectedResponse, response.getRight());
    }

    private void validateResponse(String expectedResponse, Pair<Integer, String> response) {
        assertNotNull(response);
        assertNotNull(response.getLeft());
        assertEquals(expectedResponse, response.getRight());
    }

    private static Pair<Integer, String> dmaapGet(String topic, int timeout) throws IOException {
        return dmaapGet(topic, "1", "1", timeout);
    }

    private static Pair<Integer, String> dmaapGet(String topic, String consumerGroup, String consumerId, int timeout)
                    throws IOException {
        String url = "http://localhost:" + DMAAPSIM_SERVER_PORT + "/events/" + topic + "/" + consumerGroup + "/"
                + consumerId + "?timeout=" + timeout;
        HttpURLConnection httpConn = (HttpURLConnection) new URL(url).openConnection();
        httpConn.setRequestMethod("GET");
        httpConn.connect();
        return getResponse(httpConn);
    }

    private static Pair<Integer, String> dmaapPost(String topic, String data) throws IOException {
        String url = "http://localhost:" + DMAAPSIM_SERVER_PORT + "/events/" + topic;
        byte[] postData = data.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection httpConn = (HttpURLConnection) new URL(url).openConnection();
        httpConn.setRequestMethod("POST");
        httpConn.setDoOutput(true);
        httpConn.setRequestProperty("Content-Type", "text/plain");
        httpConn.setRequestProperty("Content-Length", "" + postData.length);
        httpConn.connect();
        IOUtils.write(postData, httpConn.getOutputStream());
        return getResponse(httpConn);
    }

    private static Pair<Integer, String> setStatus(int status) throws IOException {
        String url = "http://localhost:" + DMAAPSIM_SERVER_PORT + "/events/setStatus?statusCode=" + status;
        HttpURLConnection httpConn = (HttpURLConnection) new URL(url).openConnection();
        httpConn.setRequestMethod("POST");
        httpConn.connect();
        return getResponse(httpConn);
    }

    private static Pair<Integer, String> getResponse(HttpURLConnection httpConn) throws IOException {
        try {
            String response = IOUtils.toString(httpConn.getInputStream(), StandardCharsets.UTF_8);
            return Pair.of(httpConn.getResponseCode(), response);

        } catch (IOException e) {
            if (e.getMessage().startsWith("Server returned HTTP response code")) {
                String response = IOUtils.toString(httpConn.getErrorStream(), StandardCharsets.UTF_8);
                return Pair.of(httpConn.getResponseCode(), response);
            }

            throw e;
        }
    }
}
