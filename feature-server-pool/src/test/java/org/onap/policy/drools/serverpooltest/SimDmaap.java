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

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class simulates a UEB/DMAAP server.
 */

@Path("/")
public class SimDmaap {
    private static Logger logger = LoggerFactory.getLogger(SimDmaap.class);

    // miscellaneous Jetty/Servlet parameters
    private static ServletContextHandler context;
    private static Server jettyServer;
    private static ServerConnector connector;
    private static ServletHolder holder;

    /**
     * Do whatever needs to be done to start the server. I don't know exactly
     * what abstractions the various pieces provide, but the following code
     * ties the pieces together, and starts up the server.
     */
    public static void start() {
        try {
            context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");

            jettyServer = new Server();

            connector = new ServerConnector(jettyServer);
            connector.setName("simdmaap");
            connector.setReuseAddress(true);
            final String propertyFile = "src/main/feature/config/feature-server-pool.properties";
            Properties prop = PropertyUtil.getProperties(propertyFile);
            connector.setPort(Integer.parseInt(prop.getProperty("server.pool.discovery.port")));
            connector.setHost(prop.getProperty("server.pool.discovery.servers"));

            jettyServer.addConnector(connector);
            jettyServer.setHandler(context);

            holder = context.addServlet(org.glassfish.jersey.servlet.ServletContainer.class.getName(), "/*");
            holder.setInitParameter(
                "jersey.config.server.provider.classnames",
                "com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider"
                + "," + SimDmaap.class.getName());

            jettyServer.start();
            jettyServer.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Cleanly shut down the server.
     */
    public static void stop() {
        try {
            if (jettyServer != null) {
                jettyServer.stop();
                jettyServer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ============================================================ */

    // maps topic name to 'Topic' instance
    static Map<String,Topic> topicTable = new ConcurrentHashMap<>();

    /**
     * Each instance of this class corresponds to a DMAAP or UEB topic.
     */
    static class Topic {
        // topic name
        String topic;

        // maps group name into group instance
        Map<String,Group> groupTable = new ConcurrentHashMap<>();

        /**
         * Create or get a Topic.
         *
         * @param name the topic name
         * @return the associated Topic instance
         */
        static Topic createOrGet(String name) {
            // look up the topic name
            Topic topicObj = topicTable.get(name);
            if (topicObj == null) {
                // no entry found -- the following will create one, without
                // the need for explicit synchronization
                topicTable.putIfAbsent(name, new Topic(name));
                topicObj = topicTable.get(name);
            }
            return topicObj;
        }

        /**
         * Constructor - initialize the 'topic' field.
         *
         * @param topic the topic name
         */
        private Topic(String topic) {
            this.topic = topic;
        }

        /**
         * Handle an incoming '/events/{topic}' POST REST message.
         *
         * @param the body of the REST message
         * @return the appropriate JSON response
         */
        String post(String data) {
            // start of message processing
            long startTime = System.currentTimeMillis();

            // current and ending indices to the 'data' field
            int cur = 0;
            int end = data.length();

            // the number of messages retrieved so far
            int messageCount = 0;

            while (cur < end) {
                /*
                 * The body of the message may consist of multiple JSON messages,
                 * each preceded by 3 integers separated by '.'. The second one
                 * is the length, in bytes (the third seems to be some kind of
                 * channel identifier).
                 */
                int leftBrace = data.indexOf('{', cur);
                if (leftBrace < 0) {
                    // no more messages
                    break;
                }
                String[] prefix = data.substring(cur,leftBrace).split("\\.");
                if (prefix.length == 3) {
                    try {
                        // determine length of message, and advance current position
                        int length = Integer.valueOf(prefix[1]);
                        cur = leftBrace + length;
                        /*
                         * extract message, and update count -- each double quote
                         * has a '\' character placed before it, so the overall
                         * message can be placed in double quotes, and parsed as
                         * a literal string
                         */
                        String message = data.substring(leftBrace, cur)
                                         .replace("\\", "\\\\").replace("\"", "\\\"")
                                         .replace("\n", "\\n");
                        messageCount += 1;

                        // send to all listening groups
                        for (Group group : groupTable.values()) {
                            group.messages.add(message);
                        }
                    } catch (Exception e) {
                        logger.error("{}: {}", prefix[1], e);
                        break;
                    }
                } else if (cur == 0) {
                    // there is only a single message -- extract it, and update count
                    String message = data.substring(leftBrace, end)
                                     .replace("\\", "\\\\").replace("\"", "\\\"")
                                     .replace("\n", "\\n");
                    messageCount += 1;

                    // send to all listening grops
                    for (Group group : groupTable.values()) {
                        group.messages.add(message);
                    }
                    break;
                } else {
                    // don't know what this is -- toss it
                    break;
                }
            }

            // generate response message
            long elapsedTime = System.currentTimeMillis() - startTime;
            return "{\n"
                   + "    \"count\": " + messageCount + ",\n"
                   + "    \"serverTimeMs\": " + elapsedTime + "\n"
                   + "}";
        }

        /**
         * read one or more incoming messages.
         *
         * @param group the 'consumerGroup' value
         * @param timeout how long to wait for a message, in milliseconds
         * @param limit the maximum number of messages to receive
         * @return a JSON array, containing somewhere between 0 and 'limit' messages
         */
        String get(String group, long timeout, int limit) throws InterruptedException {
            // look up the group -- create one if it doesn't exist
            Group groupObj = groupTable.get(group);
            if (groupObj == null) {
                // no entry found -- the following will create one, without
                // the need for explicit synchronization
                groupTable.putIfAbsent(group, new Group());
                groupObj = groupTable.get(group);
            }

            // pass it on to the 'Group' instance
            return groupObj.get(timeout, limit);
        }
    }

    /* ============================================================ */

    /**
     * Each instance of this class corresponds to a Consumer Group.
     */
    static class Group {
        // messages queued for this group
        private BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        /**
         * Retrieve messages sent to this group.
         *
         * @param timeout how long to wait for a message, in milliseconds
         * @param limit the maximum number of messages to receive
         * @return a JSON array, containing somewhere between 0 and 'limit' messages
         */
        String get(long timeout, int limit) throws InterruptedException {
            String message = messages.poll(timeout, TimeUnit.MILLISECONDS);
            if (message == null) {
                // timed out without messages
                return "[]";
            }

            // use 'StringBuilder' to assemble the response -- add the first message
            StringBuilder builder = new StringBuilder();
            builder.append("[\"").append(message);

            // add up to '<limit>-1' more messages
            for (int i = 1 ; i < limit ; i += 1) {
                // fetch the next message -- don't wait if it isn't currently there
                message = messages.poll();
                if (message == null) {
                    // no more currently available
                    break;
                }
                builder.append("\",\"").append(message);
            }
            builder.append("\"]");
            return builder.toString();
        }
    }

    /* ============================================================ */

    /**
     * Incoming messages from the caller to the simulator.
     */
    @POST
    @Path("/events/{topic}")
    @Consumes("application/cambria")
    @Produces(MediaType.APPLICATION_JSON)
    public String send(@PathParam("topic") String topic,
                       String data) {
        logger.info("Send: topic={}", topic);
        return Topic.createOrGet(topic).post(data);
    }

    /**
     * Send messages from the simulator to the caller.
     */
    @GET
    @Path("/events/{topic}/{group}/{id}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public String receive(@PathParam("topic") String topic,
                          @PathParam("group") String group,
                          @PathParam("id") String id,
                          @QueryParam("timeout") long timeout,
                          @QueryParam("limit") int limit)
        throws InterruptedException {

        logger.info("Receive: topic={}, group={}, id={}, timeout={}, limit={}",
                    topic, group, id, timeout, limit);
        return Topic.createOrGet(topic).get(group, timeout, limit);
    }
}
