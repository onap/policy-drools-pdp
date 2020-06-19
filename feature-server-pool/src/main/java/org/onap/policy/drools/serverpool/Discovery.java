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

import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_DISCOVERY_FETCH_LIMIT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_DISCOVERY_FETCH_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_DISCOVER_PUBLISHER_LOOP_CYCLE_TIME;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_ALLOW_SELF_SIGNED_CERTIFICATES;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_API_KEY;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_API_SECRET;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_FETCH_LIMIT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_FETCH_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_HTTPS;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_PASSWORD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_SERVERS;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_TOPIC;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVERY_USERNAME;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DISCOVER_PUBLISHER_LOOP_CYCLE_TIME;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class makes use of UEB/DMAAP to discover other servers in the pool.
 * The discovery processes ordinarily run only on the lead server, but they
 * run on other servers up until the point that they determine who the
 * leader is.
 */
public class Discovery implements TopicListener {
    private static Logger logger = LoggerFactory.getLogger(Discovery.class);

    // used for JSON <-> String conversion
    private static StandardCoder coder = new StandardCoder();

    private static Discovery discovery = null;

    private volatile Publisher publisherThread = null;

    private List<TopicSource> consumers = null;
    private List<TopicSink> publishers = null;

    private Discovery() {
        // we want to modify the properties we send to 'TopicManager'
        PropBuilder builder = new PropBuilder(ServerPoolProperties.getProperties());
        builder.convert(DISCOVERY_SERVERS, null,
                        PolicyEndPointProperties.PROPERTY_TOPIC_SERVERS_SUFFIX);
        builder.convert(DISCOVERY_USERNAME, null,
                        PolicyEndPointProperties.PROPERTY_TOPIC_AAF_MECHID_SUFFIX);
        builder.convert(DISCOVERY_PASSWORD, null,
                        PolicyEndPointProperties.PROPERTY_TOPIC_AAF_PASSWORD_SUFFIX);
        builder.convert(DISCOVERY_HTTPS, null,
                        PolicyEndPointProperties.PROPERTY_HTTP_HTTPS_SUFFIX);
        builder.convert(DISCOVERY_API_KEY, null,
                        PolicyEndPointProperties.PROPERTY_TOPIC_API_KEY_SUFFIX);
        builder.convert(DISCOVERY_API_SECRET, null,
                        PolicyEndPointProperties.PROPERTY_TOPIC_API_SECRET_SUFFIX);
        builder.convert(DISCOVERY_FETCH_TIMEOUT,
                        String.valueOf(DEFAULT_DISCOVERY_FETCH_TIMEOUT),
                        PolicyEndPointProperties.PROPERTY_TOPIC_SOURCE_FETCH_TIMEOUT_SUFFIX);
        builder.convert(DISCOVERY_FETCH_LIMIT,
                        String.valueOf(DEFAULT_DISCOVERY_FETCH_LIMIT),
                        PolicyEndPointProperties.PROPERTY_TOPIC_SOURCE_FETCH_LIMIT_SUFFIX);
        builder.convert(DISCOVERY_ALLOW_SELF_SIGNED_CERTIFICATES, null,
                        PolicyEndPointProperties.PROPERTY_ALLOW_SELF_SIGNED_CERTIFICATES_SUFFIX);
        Properties prop = builder.finish();
        logger.debug("Discovery converted properties: {}", prop);

        consumers = TopicEndpointManager.getManager().addTopicSources(prop);
        publishers = TopicEndpointManager.getManager().addTopicSinks(prop);

        if (consumers.isEmpty()) {
            logger.error("No consumer topics");
        }
        if (publishers.isEmpty()) {
            logger.error("No publisher topics");
        }
        logger.debug("Discovery: {} consumers, {} publishers",
                     consumers.size(), publishers.size());
    }

    /**
     * Start all consumers and publishers, and start the publisher thread.
     */
    static synchronized void startDiscovery() {
        if (discovery == null) {
            discovery = new Discovery();
        }
        discovery.start();
    }

    /**
     * Stop all consumers and publishers, and stop the publisher thread.
     */
    static synchronized void stopDiscovery() {
        if (discovery != null) {
            discovery.stop();
        }
    }

    /**
     * Start all consumers and publishers, and start the publisher thread.
     */
    private void start() {
        for (TopicSource consumer : consumers) {
            consumer.register(this);
            consumer.start();
        }
        for (TopicSink publisher : publishers) {
            publisher.start();
        }
        if (publisherThread == null) {
            // send thread wasn't running -- start it
            publisherThread = new Publisher();
            publisherThread.start();
        }
    }

    /**
     * Stop all consumers and publishers, and stop the publisher thread.
     */
    private void stop() {
        publisherThread = null;
        for (TopicSink publisher : publishers) {
            publisher.stop();
        }
        for (TopicSource consumer : consumers) {
            consumer.unregister(this);
            consumer.stop();
        }
    }

    /*===========================*/
    /* 'TopicListener' interface */
    /*===========================*/

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTopicEvent(CommInfrastructure infra, String topic, String event) {
        /*
         * a JSON message has been received -- it should contain
         * a single string parameter 'pingData', which contains the
         * same format base64-encoded message that 'Server'
         * instances periodically exchange
         */
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        try {
            map = coder.decode(event, LinkedHashMap.class);
            String message = map.get("pingData");
            Server.adminRequest(message.getBytes(StandardCharsets.UTF_8));
            logger.info("Received a message, server count={}", Server.getServerCount());
        } catch (CoderException e) {
            logger.error("Can't decode message: {}", e);
        }
    }

    /* ============================================================ */

    /**
     * This class is used to convert internal 'discovery.*' properties to
     * properties that 'TopicEndpointManager' can use.
     */
    private static class PropBuilder {
        // properties being incrementally modified
        Properties prop;

        // value from 'discovery.topic' parameter
        String topic;

        // 'true' only if both 'discovery.topic' and 'discovery.servers'
        // has been defined
        boolean doConversion = false;

        // contains "ueb.source.topics" or "dmaap.source.topics"
        String sourceTopicsName = null;

        // contains "<TYPE>.source.topics.<TOPIC>" (<TYPE> = ueb|dmaap)
        String sourcePrefix = null;

        // contains "ueb.sink.topics" or "dmaap.sink.topics"
        String sinkTopicsName = null;

        // contains "<TYPE>.sink.topics.<TOPIC>" (<TYPE> = ueb|dmaap)
        String sinkPrefix = null;

        /**
         * Constructor - decide whether we are going to do conversion or not,
         * and initialize accordingly.
         *
         * @param prop the initial list of properties
         */
        PropBuilder(Properties prop) {
            this.prop = new Properties(prop);
            this.topic = prop.getProperty(DISCOVERY_TOPIC);
            String servers = prop.getProperty(DISCOVERY_SERVERS);
            if (topic != null && servers != null) {
                // we do have property conversion to do
                doConversion = true;
                String type = topic.contains(".") ? "dmaap" : "ueb";
                sourceTopicsName = type + ".source.topics";
                sourcePrefix = sourceTopicsName + "." + topic;
                sinkTopicsName = type + ".sink.topics";
                sinkPrefix = sinkTopicsName + "." + topic;
            }
        }

        /**
         * If we are doing conversion, convert an internal property
         * to something that 'TopicEndpointManager' can use.
         *
         * @param intName server pool property name (e.g. "discovery.servers")
         * @param defaultValue value to use if property 'intName' is not specified
         * @param extSuffix TopicEndpointManager suffix, including leading "."
         */
        void convert(String intName, String defaultValue, String extSuffix) {
            if (doConversion) {
                String value = prop.getProperty(intName, defaultValue);
                if (value != null) {
                    prop.setProperty(sourcePrefix + extSuffix, value);
                    prop.setProperty(sinkPrefix + extSuffix, value);
                }
            }
        }

        /**
         * Generate/update the '*.source.topics' and '*.sink.topics' parameters.
         *
         * @return the updated properties list
         */
        Properties finish() {
            if (doConversion) {
                String currentValue = prop.getProperty(sourceTopicsName);
                if (currentValue == null) {
                    // '*.source.topics' is not defined -- set it
                    prop.setProperty(sourceTopicsName, topic);
                } else {
                    // '*.source.topics' is defined -- append to it
                    prop.setProperty(sourceTopicsName, currentValue + "," + topic);
                }
                currentValue = prop.getProperty(sinkTopicsName);
                if (currentValue == null) {
                    // '*.sink.topics' is not defined -- set it
                    prop.setProperty(sinkTopicsName, topic);
                } else {
                    // '*.sink.topics' is defined -- append to it
                    prop.setProperty(sinkTopicsName, currentValue + "," + topic);
                }
            }
            return prop;
        }
    }

    /* ============================================================ */

    /**
     * This is the sender thread, which periodically sends out 'ping' messages.
     */
    private class Publisher extends Thread {
        /**
         * Constructor -- read in the properties, and initialze 'publisher'.
         */
        Publisher() {
            super("Discovery Publisher Thread");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            // this loop will terminate once 'publisher' is set to 'null',
            // or some other 'Publisher' instance replaces it
            long cycleTime = getProperty(DISCOVER_PUBLISHER_LOOP_CYCLE_TIME,
                                         DEFAULT_DISCOVER_PUBLISHER_LOOP_CYCLE_TIME);
            while (this == publisherThread) {
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
                    LinkedHashMap<String, String> map = new LinkedHashMap<>();
                    map.put("pingData", encodedData);
                    String jsonString = new Gson().toJson(map, Map.class);
                    for (TopicSink publisher : publishers) {
                        publisher.send(jsonString);
                    }
                } catch (InterruptedException e) {
                    logger.error("Exception in Discovery.Publisher.run():", e);
                    return;
                } catch (Exception e) {
                    logger.error("Exception in Discovery.Publisher.run():", e);
                    // grace period -- we don't want to get UEB upset at us
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException e2) {
                        logger.error("Discovery.Publisher sleep interrupted");
                    }
                    return;
                }
            }
        }
    }
}
