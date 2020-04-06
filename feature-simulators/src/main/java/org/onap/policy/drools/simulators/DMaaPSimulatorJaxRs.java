/*-
 * ============LICENSE_START=======================================================
 * feature-simulators
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

package org.onap.policy.drools.simulators;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/events")
public class DMaaPSimulatorJaxRs {
    public static final String NO_TOPIC_MSG = "No topic";
    public static final String NO_DATA_MSG = "No Data";

    private static final Map<String, BlockingQueue<String>> queues = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(DMaaPSimulatorJaxRs.class);
    private static int responseCode = 200;

    /**
     * Get consumer ID.
     *
     * @param timeout timeout value
     * @param topicName the dmaap topic
     * @param httpResponse http response object
     * @return topic or error message
     */
    @GET
    @Path("/{topicName}/{consumeGroup}/{consumerId}")
    public String subscribe(@DefaultValue("0") @QueryParam("timeout") int timeout, @PathParam("topicName")
            String topicName, @Context final HttpServletResponse httpResponse) {
        int currentRespCode = responseCode;
        httpResponse.setStatus(currentRespCode);
        try {
            httpResponse.flushBuffer();
        } catch (IOException e) {
            logger.error("flushBuffer threw: ", e);
            return "Got an error";
        }

        if (currentRespCode < 200 || currentRespCode >= 300) {
            return "You got response code: " + currentRespCode;
        }

        if (queues.containsKey(topicName)) {
            return getNextMessageFromQueue(timeout, topicName);
        }
        else if (timeout > 0) {
            return waitForNextMessageFromQueue(timeout, topicName);
        }
        return NO_TOPIC_MSG;
    }

    private String getNextMessageFromQueue(final int timeout, final String topicName) {
        BlockingQueue<String> queue = queues.get(topicName);
        String response = NO_DATA_MSG;
        try {
            response = poll(queue, timeout);
        } catch (InterruptedException e) {
            logger.debug("error in DMaaP simulator", e);
            Thread.currentThread().interrupt();
        }
        if (response == null) {
            response = NO_DATA_MSG;
        }
        return response;
    }

    protected String waitForNextMessageFromQueue(int timeout, String topicName) {
        try {
            sleep(timeout);
            if (queues.containsKey(topicName)) {
                BlockingQueue<String> queue = queues.get(topicName);
                String response = queue.poll();
                if (response == null) {
                    response = NO_DATA_MSG;
                }
                return response;
            }
        } catch (InterruptedException e) {
            logger.debug("error in DMaaP simulator", e);
            Thread.currentThread().interrupt();
        }
        return NO_TOPIC_MSG;
    }

    /**
     * Post to a topic.
     *
     * @param topicName name of the topic
     * @param body message
     * @return empty string
     */
    @POST
    @Path("/{topicName}")
    @Consumes(MediaType.TEXT_PLAIN)
    public String publish(@PathParam("topicName") String topicName, String body) {
        BlockingQueue<String> queue = queues.computeIfAbsent(topicName, entry -> new LinkedBlockingQueue<>());
        queue.add(body);

        return "";
    }

    @POST
    @Path("/setStatus")
    public String setStatus(@QueryParam("statusCode") int statusCode) {
        setResponseCode(statusCode);
        return "Status code set";
    }

    // the following non-static methods may be overridden by junit tests

    protected String poll(BlockingQueue<String> queue, final int timeout) throws InterruptedException {
        return queue.poll(timeout, TimeUnit.MILLISECONDS);
    }

    protected void sleep(int timeout) throws InterruptedException {
        Thread.sleep(timeout);
    }

    /**
     * Static method to set static response code, synchronized for multiple possible uses.
     *
     * @param incomingResponseCode the response code to set
     */
    private static synchronized void setResponseCode(final int incomingResponseCode) {
        responseCode = incomingResponseCode;
    }

    /**
     * Used only by junit tests to reset the simulator.
     */
    protected static void reset() {
        responseCode = 200;
        queues.clear();
    }
}
