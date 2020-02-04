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

import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_DISCOVERY_CONSUMER_SOCKET_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_DISCOVERY_FETCH_LIMIT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_DISCOVERY_FETCH_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_DISCOVERY_PUBLISHER_SOCKET_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_DISCOVER_PUBLISHER_LOOP_CYCLE_TIME;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_ALLOW_SELF_SIGNED_CERTIFICATES;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_API_KEY;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_API_SECRET;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_CONSUMER_SOCKET_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_FETCH_LIMIT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_FETCH_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_HTTPS;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_PASSWORD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_PUBLISHER_SOCKET_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_SERVERS;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_TOPIC;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_USERNAME;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVER_PUBLISHER_LOOP_CYCLE_TIME;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import com.att.nsa.cambria.client.CambriaBatchingPublisher;
import com.att.nsa.cambria.client.CambriaClientBuilders.ConsumerBuilder;
import com.att.nsa.cambria.client.CambriaClientBuilders.PublisherBuilder;
import com.att.nsa.cambria.client.CambriaConsumer;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.security.CryptoUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class makes use of UEB/DMAAP to discover other servers in the pool.
 * The discovery processes ordinarily run only on the lead server, but they
 * run on other servers up until the point that they determine who the
 * leader is.
 */
public class Discovery {
    private static Logger logger = LoggerFactory.getLogger(Discovery.class);

    // used for JSON <-> String conversion
    private static StandardCoder coder = new StandardCoder();

    // message receive thread
    private static volatile Consumer discoveryConsumer = null;

    // message send thread
    private static volatile Publisher discoveryPublisher = null;

    private static String secretKey = System.getenv("AES_ENCRYPTION_KEY");

    /**
     * This method starts the send and receive threads, if they aren't
     * currently running.
     */
    static synchronized void startDiscovery() {
        try {
            if (discoveryConsumer == null) {
                // receive thread wasn't running -- start it
                discoveryConsumer = new Consumer();
                discoveryConsumer.start();
            }
            if (discoveryPublisher == null) {
                // send thread wasn't running -- start it
                discoveryPublisher = new Publisher();
                discoveryPublisher.start();
            }
        } catch (Exception e) {
            logger.error("Exception in Discovery.startDiscovery():", e);
        }
    }

    /**
     * This method stops the send and receive threads (note that there may
     * be a delay before each thread responds to the 'stop' request).
     */
    static synchronized void stopDiscovery() {
        discoveryConsumer = null;
        discoveryPublisher = null;
    }

    /* ============================================================ */

    /**
     * This is the receiver thread, which receives 'ping' messages, and
     * passes them on to 'Server.adminRequest(...)' for processing.
     */
    private static class Consumer extends Thread {
        CambriaConsumer consumer;

        /**
         * Constructor -- read in the properties, and initialize 'consumer'.
         */
        Consumer() throws MalformedURLException, GeneralSecurityException {
            super("Discovery Consumer Thread");

            // the set of servers property is mandatory
            String servers = getProperty(DISCOVERY_SERVERS, null);
            if (servers == null) {
                throw(new IllegalArgumentException(
                      "Property '" + DISCOVERY_SERVERS + "' is not specified"));
            }

            // the topic property is mandatory
            String topic = getProperty(DISCOVERY_TOPIC, null);
            if (topic == null) {
                throw(new IllegalArgumentException(
                      "Property '" + DISCOVERY_TOPIC + "' is not specified"));
            }

            // create the 'ConsumerBuilder' instance, and pass the information we
            // have so far: 'servers' and 'topic'
            ConsumerBuilder builder = new ConsumerBuilder();
            builder.usingHosts(servers).onTopic(topic);

            // optional HTTP authentication
            String username = getProperty(DISCOVERY_USERNAME, null);
            String password = CryptoUtils.decrypt(getProperty(DISCOVERY_PASSWORD, null), secretKey);

            if (username != null && password != null
                    && !username.isEmpty() && !password.isEmpty()) {
                builder.authenticatedByHttp(username, password);
            }

            // optional Cambria authentication
            String apiKey = getProperty(DISCOVERY_API_KEY, null);
            String apiSecret = CryptoUtils.decrypt(getProperty(DISCOVERY_API_SECRET, null), secretKey);

            if (apiKey != null && apiSecret != null
                    && !apiKey.isEmpty() && !apiSecret.isEmpty()) {
                builder.authenticatedBy(apiKey, apiSecret);
            }

            // socket timeout -- use a default value if not specified
            builder.withSocketTimeout(
                getProperty(DISCOVERY_CONSUMER_SOCKET_TIMEOUT,
                            DEFAULT_DISCOVERY_CONSUMER_SOCKET_TIMEOUT));

            // fetch timeout -- use a default value if not specified
            builder.waitAtServer(getProperty(DISCOVERY_FETCH_TIMEOUT, DEFAULT_DISCOVERY_FETCH_TIMEOUT));

            // fetch limit -- use a default value if not specified
            builder.receivingAtMost(getProperty(DISCOVERY_FETCH_LIMIT, DEFAULT_DISCOVERY_FETCH_LIMIT));

            // encryption
            if (getProperty(DISCOVERY_HTTPS, false)) {
                builder.usingHttps();
                if (getProperty(DISCOVERY_ALLOW_SELF_SIGNED_CERTIFICATES, false)) {
                    builder.allowSelfSignedCertificates();
                }
            }

            // set consumer group and consumer id -- ensure that they are unique
            {
                String id = Server.getThisServer().getUuid().toString();
                builder.knownAs(id, "0");
            }

            // create consumer
            consumer = builder.build();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            // this loop will terminate once 'discoveryConsumer' is set to 'null',
            // or some other 'Consumer' instance replaces it
            while (this == discoveryConsumer) {
                try {
                    int count = 0;
                    for (String message : consumer.fetch()) {
                        // a JSON message has been received -- it should contain
                        // a single string parameter 'pingData', which contains the
                        // same format base64-encoded message that 'Server'
                        // instances periodically exchange
                        JsonObject jsonObject =
                            coder.decode(message, JsonObject.class);
                        Server.adminRequest(jsonObject
                                            .getAsJsonPrimitive("pingData")
                                            .getAsString()
                                            .getBytes());
                        count += 1;
                    }
                    if (count != 0) {
                        logger.info("Received {} messages, server count={}",
                                    count, Server.getServerCount());
                    }
                } catch (Exception e) {
                    logger.error("Exception in Discovery.Consumer.run():", e);

                    // grace period -- we don't want to get UEB upset at us
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException e2) {
                        logger.error("Discovery.Consumer sleep interrupted");
                    }
                }
            }

            // shut down
            consumer.close();
        }
    }

    /* ============================================================ */

    /**
     * This is the sender thread, which periodically sends out 'ping' messages.
     */
    private static class Publisher extends Thread {
        CambriaBatchingPublisher publisher;

        /**
         * Constructor -- read in the properties, and initialze 'publisher'.
         */
        Publisher() throws MalformedURLException, GeneralSecurityException {
            super("Discovery Publisher Thread");

            // the set of servers property is mandatory
            String servers = getProperty(DISCOVERY_SERVERS, null);
            if (servers == null) {
                throw(new IllegalArgumentException(
                      "Property '" + DISCOVERY_SERVERS + "' is not specified"));
            }

            // the topic property is mandatory
            String topic = getProperty(DISCOVERY_TOPIC, null);
            if (topic == null) {
                throw(new IllegalArgumentException(
                      "Property '" + DISCOVERY_TOPIC + "' is not specified"));
            }

            // create the 'PublisherBuilder' instance, and pass the information we
            // have so far: 'servers' and 'topic'
            PublisherBuilder builder = new PublisherBuilder();
            builder.usingHosts(servers).onTopic(topic);

            // optional HTTP authentication
            String username = getProperty(DISCOVERY_USERNAME, null);
            String password = CryptoUtils.decrypt(getProperty(DISCOVERY_PASSWORD, null), secretKey);

            if (username != null && password != null
                    && !username.isEmpty() && !password.isEmpty()) {
                builder.authenticatedByHttp(username, password);
            }

            // optional Cambria authentication
            String apiKey = getProperty(DISCOVERY_API_KEY, null);
            String apiSecret = CryptoUtils.decrypt(getProperty(DISCOVERY_API_SECRET, null), secretKey);

            if (apiKey != null && apiSecret != null
                    && !apiKey.isEmpty() && !apiSecret.isEmpty()) {
                builder.authenticatedBy(apiKey, apiSecret);
            }

            // socket timeout -- use a default value if not specified
            builder.withSocketTimeout(getProperty(DISCOVERY_PUBLISHER_SOCKET_TIMEOUT,
                DEFAULT_DISCOVERY_PUBLISHER_SOCKET_TIMEOUT));

            // encryption
            if (getProperty(DISCOVERY_HTTPS, false)) {
                builder.usingHttps();
                if (getProperty(DISCOVERY_ALLOW_SELF_SIGNED_CERTIFICATES, false)) {
                    // TBD: Doesn't publisher support this?
                    // builder.allowSelfSignedCertificates();
                }
            }

            // create publisher
            publisher = builder.build();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            // this loop will terminate once 'discoveryPublisher' is set to 'null',
            // or some other 'Publisher' instance replaces it
            long cycleTime = getProperty(DISCOVER_PUBLISHER_LOOP_CYCLE_TIME,
                                         DEFAULT_DISCOVER_PUBLISHER_LOOP_CYCLE_TIME);
            while (this == discoveryPublisher) {
                try {
                    // wait 5 seconds (default)
                    Thread.sleep(cycleTime);

                    // generate a 'ping' message
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);

                    // write the 'ping' data for this server
                    Server thisServer = Server.getThisServer();
                    thisServer.writeServerData(dos);
                    String encodedData =
                        new String(Base64.getEncoder().encode(bos.toByteArray()));

                    // base64-encoded value is passed as JSON parameter 'pingData'
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("pingData", encodedData);

                    // send out the JSON message
                    publisher.send(thisServer.getUuid().toString(),
                                   jsonObject.toString());
                } catch (Exception e) {
                    logger.error("Exception in Discovery.Publisher.run():", e);

                    // grace period -- we don't want to get UEB upset at us
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException e2) {
                        logger.error("Discovery.Publisher sleep interrupted");
                    }
                }
            }

            try {
                // shut down publisher thread
                publisher.close(10, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException e) {
                logger.error("Exception in Discovery.Publisher.run():", e);
            }
        }
    }
}
