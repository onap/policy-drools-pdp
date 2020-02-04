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

import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_DROOLS_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_TIME_TO_LIVE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_DROOLS_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_TIME_TO_LIVE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Properties;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import lombok.AllArgsConstructor;

import org.kie.api.runtime.KieSession;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.coder.StandardCoderObject;
import org.onap.policy.drools.control.api.DroolsPdpStateControlApi;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.PolicySessionFeatureApi;
import org.onap.policy.drools.core.lock.PolicyResourceLockManager;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * </p>
 * This class hooks the server pool implementation into DroolsPDP.
 * <dl>
 * <li>PolicyEngineFeatureApi - the <i>afterStart</i> hook is where we initialize.</li>
 * <li>PolicyControllerFeatureApi - the <i>beforeOffer</i> hook is used to look
 *     at incoming topic messages, and decide whether to process them
 *     on this host, or forward to another host.</li>
 * </dl>
 */
public class FeatureServerPool
    implements PolicyEngineFeatureApi, PolicySessionFeatureApi,
    PolicyControllerFeatureApi, DroolsPdpStateControlApi {
    private static Logger logger =
        LoggerFactory.getLogger(FeatureServerPool.class);

    // used for JSON <-> String conversion
    private static StandardCoder coder = new StandardCoder();

    private static final String configFile =
            "config/feature-server-pool.properties";

    /*
     * </p>
     * <dl>
     *     Properties used when searching for keyword entries.
     * </dl>
     * <dl>
     *     The following types are supported:
     * </dl>
     *  <dl>    
     *     <li>1) keyword.&lttopic&gt.path=&ltfield-list&gt </li>
     *     <li>2) keyword.path=&ltfield-list&gt</li>
     *     <li>3) ueb.source.topics.&lttopic>.keyword=&ltfield-list&gt</li>
     *     <li>4) ueb.source.topics.keyword=&ltfield-list&gt</li>
     *     <li>5) dmaap.source.topics.&lttopic&gt.keyword=&ltfield-list&gt</li>
     *     <li>6) dmaap.source.topics.keyword=&ltfield-list&gt</li>
     *      
     *     <li>1, 3, and 5 are functionally equivalent</li>
     *     <li>2, 4, and 6 are functionally equivalent.</li>
     * </dl>
     */

    static final String KEYWORD_PROPERTY_START_1 = "keyword.";
    static final String KEYWORD_PROPERTY_END_1 = ".path";
    static final String KEYWORD_PROPERTY_START_2 = "ueb.source.topics.";
    static final String KEYWORD_PROPERTY_END_2 = ".keyword";
    static final String KEYWORD_PROPERTY_START_3 = "dmaap.source.topics.";
    static final String KEYWORD_PROPERTY_END_3 = ".keyword";

    /*
     * </p>
     * <dl>   
     * maps topic names to a keyword table derived from &ltfield-list&gt (above).
     * </dl>
     * <dl>
     * Example &ltfield-list&gt: requestID,CommonHeader.RequestID
     * </dl>
     * <dl>
     * Table generated from this example has length 2:
     * <li>table[0] = {"requestID"}</li>
     * <li>table[1] = {"CommonHeader", "RequestID"}</li>
     * </dl>
     */
    private static HashMap<String,String[][]> topicToPaths = new HashMap<>();

    // this table is used for any topics that aren't in 'topicToPaths'
    private static String[][] defaultPaths = new String[0][];

    // extracted from properties
    private static long droolsTimeoutMillis;
    private static String timeToLiveSecond;

    /******************************/
    /* 'OrderedService' interface */
    /******************************/

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSequenceNumber() {
        /**
         * we need to make sure we have an early position in 'selectThreadModel'
         * (in case there is feature that provides a thread model)
         */
        return -1000000;
    }

    /**************************************/
    /* 'PolicyEngineFeatureApi' interface */
    /**************************************/

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean afterStart(PolicyEngine engine) {
        logger.info("Starting FeatureServerPool");
        try {
            Server.startup(configFile);
        } catch (Exception e) {
            logger.error("FeatureServerPool.afterStart: error start server", e);
            return false;
        }
        TargetLock.startup();
        droolsTimeoutMillis =
            getProperty(BUCKET_DROOLS_TIMEOUT, DEFAULT_BUCKET_DROOLS_TIMEOUT);
        int intTimeToLive =
            getProperty(BUCKET_TIME_TO_LIVE, DEFAULT_BUCKET_TIME_TO_LIVE);
        timeToLiveSecond = String.valueOf(intTimeToLive);
        buildKeywordTable();
        DroolsSessionBackup droolsSessionBackup = new DroolsSessionBackup(droolsTimeoutMillis);
        Backup.register(droolsSessionBackup);
        TargetLock.LockBackup lockBackup = new TargetLock.LockBackup();
        Backup.register(lockBackup);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PolicyResourceLockManager beforeCreateLockManager(
        PolicyEngine engine, Properties properties) {

        return TargetLock.getLockFactory();
    }

    /*=====================================*/
    /* 'PolicySessionFeatureApi' interface */
    /*=====================================*/

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean insertDrools(
        final PolicySession session, final Object object) {

        final String keyword = Keyword.lookupKeyword(object);
        if (keyword == null) {
            // no keyword was found, so we process locally
            KieSession kieSession = session.getKieSession();
            if (kieSession != null) {
                kieSession.insert(object);
            }
            return true;
        }

        /**
         * 'keyword' determines the destination host,
         * which may be local or remote
         */
        Bucket.forwardAndProcess(keyword, new Message() {
            @Override
            public void process() {
                // if we reach this point, we process locally
                KieSession kieSession = session.getKieSession();
                if (kieSession != null) {
                    kieSession.insert(object);
                }
            }

            @Override
            public void sendToServer(Server server, int bucketNumber) {
                /**
                 * this object needs to sent to a remote host --
                 * first, serialize the object
                 */
                byte[] data = null;
                try {
                    data = Util.serialize(object);
                } catch (IOException e) {
                    logger.error("insertDrools: can't serialize object of {}",
                                 object.getClass(), e);
                    return;
                }

                // construct the message to insert remotely
                Entity<String> entity = Entity.entity(
                    new String(Base64.getEncoder().encode(data), StandardCharsets.UTF_8),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
                server.post("session/insertDrools", entity,
                    new Server.PostResponse() {
                        @Override
                        public WebTarget webTarget(WebTarget webTarget) {
                            PolicyContainer pc = session.getPolicyContainer();
                            String encodedSessionName =
                                pc.getGroupId() + ":" + pc.getArtifactId() + ":"
                                + session.getName();

                            return webTarget
                                .queryParam("keyword", keyword)
                                .queryParam("session", encodedSessionName)
                                .queryParam("bucket", bucketNumber)
                                .queryParam("ttl", timeToLiveSecond);
                        }

                        @Override
                        public void response(Response response) {
                            logger.info("/session/insertDrools response code = {}",
                                        response.getStatus());
                        }
                    });
            }
        });
        return true;
    }

    /******************************************/
    /* 'PolicyControllerFeatureApi' interface */
    /******************************************/

    /**
     * This method is called from 'AggregatedPolicyController.onTopicEvent',
     * and provides a way to intercept the message before it is decoded and
     * delivered to a local Drools session.
     *
     * @param controller the PolicyController instance receiving the message
     * @param protocol communication infrastructure type
     * @param topic topic name
     * @param event event message as a string
     * @return 'false' if this event should be processed locally, or 'true'
     *     if the message has been forwarded to a remote host, so local
     *     processing should be bypassed
     */
    @Override
    public boolean beforeOffer(final PolicyController controller,
                               final CommInfrastructure protocol,
                               final String topic,
                               final String event) {
        // choose the table, based upon the topic
        String[][] table = topicToPaths.getOrDefault(topic, defaultPaths);

        // build a JSON object from the event
        StandardCoderObject sco;

        try {
            sco = coder.decode(event, StandardCoderObject.class);
        } catch (CoderException e) {
            return false;
        }
        String keyword = null;

        for (String[] path : table) {
            /**
             * Each entry in 'table' is a String[] containing an encoding
             * of a possible keyword field. Suppose the value is 'a.b.c.d.e' --
             * 'path' would be encoded as 'String[] {"a", "b", "c", "d", "e"}'
             */
            String fieldName = path[path.length - 1];
            String conversionFunctionName = null;
            int index = fieldName.indexOf(':');

            if (index > 0) {
                conversionFunctionName = fieldName.substring(index + 1);
                fieldName = fieldName.substring(0, index);
                path = Arrays.copyOf(path, path.length);
                path[path.length - 1] = fieldName;
            }
            keyword = sco.getString(path);

            
            /**
             * If keyword is not 'null', it means we have traversed down the entire
             * 'a.b.c.d' part of 'a.b.c.d.e'. The last step is to see if 'e'
             * exists -- if so, we have found the keyword.
             */
            if (keyword != null) {
                if (conversionFunctionName == null) {
                    /**
                      * We found a keyword -- we don't need to try other paths,
                      * so we should break out of the loop.
                     */
                    break;
                }

                // we have post-processing to do
                keyword = Keyword.convertKeyword(keyword, conversionFunctionName);
                if (keyword != null) {
                    // conversion was successful
                    break;
                }
            }
        }

        if (keyword == null) {
            // couldn't find any keywords -- just process this message locally
            logger.warn("Can't locate bucket keyword within message");
            return false;
        }

        /**
         * build a message object implementing the 'Bucket.Message' interface --
         * it will be processed locally, forwarded, or queued based upon the
         * current state.
         */
        TopicMessage message =
            new TopicMessage(keyword, controller, protocol, topic, event);
        int bucketNumber = Bucket.bucketNumber(keyword);
        if (Bucket.forward(bucketNumber, message)) {
            // message was queued or forwarded -- abort local processing
            return true;
        }

        /**
         * the bucket happens to be assigned to this server, and wasn't queued  --
         * return 'false', so it will be processed locally
         */
        logger.info("Keyword={}, bucket={} -- owned by this server",
                    keyword, bucketNumber);
        return false;
    }

    /**
     * Incoming topic message has been forwarded from a remote host.
     *
     * @param bucketNumber the bucket number calculated on the remote host
     * @param keyword the keyword associated with the message
     * @param controllerName the controller the message was directed to
     *     on the remote host
     * @param protocol String value of the 'Topic.CommInfrastructure' value
     *     (UEB, DMAAP, NOOP, or REST -- NOOP and REST shouldn't be used
     *     here)
     * @param topic the UEB/DMAAP topic name
     * @param event this is the JSON message
     */
    public static void topicMessage(
        int bucketNumber, String keyword, String controllerName,
        String protocol, String topic, String event) {

        // @formatter:off
        logger.info("Incoming topic message: Keyword={}, bucket={}\n"
                    + "    controller = {}\n"
                    + "    topic      = {}",
                    keyword, bucketNumber, controllerName, topic);
        // @formatter:on

        // locate the 'PolicyController'
        PolicyController controller = PolicyControllerConstants.getFactory().get(controllerName);
        if (controller == null) {
            /**
             * This controller existed on the sender's host, but doesn't exist
             * on the destination. This is a problem -- we are counting on all
             * hosts being configured with the same controllers.
             */
            logger.error("Can't locate controller '{}' for incoming topic message",
                         controllerName);
        } else if (controller instanceof TopicListener) {
            /**
             * This is the destination host -- repeat the 'onTopicEvent'
             * method (the one that invoked 'beforeOffer' on the originating host).
             * Note that this message could be forwarded again if the sender's
             * bucket table was somehow different from ours -- perhaps there was
             * an update in progress.
             *
             * TBD: it would be nice to limit the number of hops, in case we
             * somehow have a loop.
             */
            ((TopicListener)controller).onTopicEvent(
                CommInfrastructure.valueOf(protocol), topic, event);
        } else {
            /**
             * This 'PolicyController' was also a 'TopicListener' on the sender's
             * host -- it isn't on this host, and we are counting on them being
             * configured the same way.
             */
            logger.error("Controller {} is not a TopicListener", controllerName);
        }
    }

    /**
     * An incoming '/session/insertDrools' message was received.
     *
     * @param keyword the keyword associated with the incoming object
     * @param sessionName encoded session name(groupId:artifactId:droolsSession)
     * @param bucket the bucket associated with keyword
     * @param ttl similar to IP time-to-live -- it controls the number of hops
     *     the message may take
     * @param data base64-encoded serialized data for the object
     */
    public static void incomingInsertDrools(
        String keyword, String sessionName, int bucket, int ttl, byte[] data) {

        logger.info("Incoming insertDrools: keyword={}, session={}, bucket={}, ttl={}",
            keyword, sessionName, bucket, ttl);

        if (Bucket.isKeyOnThisServer(keyword)) {
            /**
             * </p>
             * process locally
             * <dl>
             * [0]="&ltgroupId&gt" [1]="&ltartifactId&gt", [2]="&ltsessionName&gt"
             * </dl>
             */
            String[] nameSegments = sessionName.split(":");
            PolicySession policySession = null;

            // locate the 'PolicyContainer' and 'PolicySession'
            if (nameSegments.length == 3) {
                /**
                 * step through all 'PolicyContainer' instances looking
                 * for a matching 'artifactId' & 'groupId'
                 */
                for (PolicyContainer pc : PolicyContainer.getPolicyContainers()) {
                    if (nameSegments[1].equals(pc.getArtifactId())
                            && nameSegments[0].equals(pc.getGroupId())) {
                        policySession = pc.getPolicySession(nameSegments[2]);
                        break;
                    }
                }
            }

            if (policySession == null) {
                logger.error("incomingInsertDrools: Can't find PolicySession={}",
                             sessionName);
                return;
            }
            
            KieSession kieSession = policySession.getKieSession();
            if (kieSession != null) {
                try {
                    // deserialization needs to use the correct class loader
                    Object obj = Util.deserialize(
                        Base64.getDecoder().decode(data),
                        policySession.getPolicyContainer().getClassLoader());
                    kieSession.insert(obj);
                } catch (IOException | ClassNotFoundException
                             | IllegalArgumentException e) {
                    logger.error("incomingInsertDrools: failed to read data "
                                 + "for session '{}'", sessionName, e);
                }
            }
        } else if ((ttl -= 1) > 0) {
            /**
             * This host is not the intended destination -- this could happen
             * if it was sent from another site. Forward the message in the
             * same thread.
             */
            forwardInsertDroolsMessage(bucket, keyword, sessionName, ttl, data);
        }
    }

    /**
     * Forward the insertDrools message in the same thread.
     */
    private static void forwardInsertDroolsMessage(int bucket, String keyword, 
            String sessionName, int ttl, byte[] data) {
        Server server = Bucket.bucketToServer(bucket);
        WebTarget webTarget = server.getWebTarget("session/insertDrools");
        if (webTarget != null) {
            logger.info("Forwarding 'session/insertDrools' "
                        + "(key={},session={},bucket={},ttl={})",
                        keyword, sessionName, bucket, ttl);
            Entity<String> entity =
                Entity.entity(new String(data, StandardCharsets.UTF_8),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
            webTarget
            .queryParam("keyword", keyword)
            .queryParam("session", sessionName)
            .queryParam("bucket", bucket)
            .queryParam("ttl", ttl)
            .request().post(entity);
        }
    }

    /**
     * This method builds the table that is used to locate the appropriate
     * keywords within incoming JSON messages (e.g. 'requestID'). The
     * associated values are then mapped into bucket numbers.
     */
    private static void buildKeywordTable() {
        Properties prop = ServerPoolProperties.getProperties();

        /**
         *iterate over all of the properties, picking out those we are
         * interested in
         */
        for (String name : prop.stringPropertyNames()) {
            String topic = null;
            String begin;
            String end;

            if (name.startsWith(KEYWORD_PROPERTY_START_1)
                    && name.endsWith(KEYWORD_PROPERTY_END_1)) {
                /**
                 * 1) keyword.<topic>.path=<field-list>
                 * 2) keyword.path=<field-list>
                 */
                begin = KEYWORD_PROPERTY_START_1;
                end = KEYWORD_PROPERTY_END_1;
            } else if (name.startsWith(KEYWORD_PROPERTY_START_2)
                       && name.endsWith(KEYWORD_PROPERTY_END_2)) {
                /**
                 * 3) ueb.source.topics.<topic>.keyword=<field-list>
                 * 4) ueb.source.topics.keyword=<field-list>
                 */
                begin = KEYWORD_PROPERTY_START_2;
                end = KEYWORD_PROPERTY_END_2;
            } else if (name.startsWith(KEYWORD_PROPERTY_START_3)
                       && name.endsWith(KEYWORD_PROPERTY_END_3)) {
                /**
                 * 5) dmaap.source.topics.<topic>.keyword=<field-list>
                 * 6) dmaap.source.topics.keyword=<field-list>
                 */
                begin = KEYWORD_PROPERTY_START_3;
                end = KEYWORD_PROPERTY_END_3;
            } else {
                // we aren't interested in this property
                continue;
            }

            int beginIndex = begin.length();
            int endIndex = name.length() - end.length();
            if (beginIndex < endIndex) {
                /**
                 * <topic> is specified, so this table is limited to this
                 * specific topic
                 */
                topic = name.substring(beginIndex, endIndex);
            }

            /**
             * now, process the value
             * Example: requestID,CommonHeader.RequestID
             */
            String[] commaSeparatedEntries = prop.getProperty(name).split(",");
            String[][] paths = new String[commaSeparatedEntries.length][];
            for (int i = 0 ; i < commaSeparatedEntries.length ; i += 1) {
                paths[i] = commaSeparatedEntries[i].split("\\.");
            }

            if (topic == null) {
                /**
                 * these paths are used for any topics not explicitly
                 * in the 'topicToPaths' table
                 */
                defaultPaths = paths;
            } else {
                // these paths are specific to 'topic'
                topicToPaths.put(topic, paths);
            }
        }
    }

    /*======================================*/
    /* 'DroolsPdpStateControlApi' interface */
    /*======================================*/

    /**
     * Stop the processing of messages and server pool participation(non-Javadoc)
     * Note: This is not static because it should only be used if feature-server-pool
     * has been enabled.
     * (non-Javadoc)
     * @see org.onap.policy.drools.control.api.DroolsPdpStateControlApi#shutdown()
     */
    @Override
    public void shutdown() {
        PolicyEngineConstants.getManager().deactivate();
        Server.shutdown();
    }

    /**
     * Stop the processing of messages and server pool participation(non-Javadoc)
     * Note: This is not static because it should only be used if feature-server-pool
     * has been enabled.
     * (non-Javadoc)
     * @see org.onap.policy.drools.control.api.DroolsPdpStateControlApi#restart()
     */
    @Override
    public void restart() {
        MainLoop.startThread();
        Discovery.startDiscovery();
        PolicyEngineConstants.getManager().activate();
    }

    /* ============================================================ */

    /**
     * This class implements the 'Bucket.Message' interface for UEB/DMAAP
     * messages.
     */
    @AllArgsConstructor
    private static class TopicMessage implements Message {
        /**
         * the keyword associated with this message
         * (which determines the bucket number).
         */
        private final String keyword;

        // the controller receiving this message
        private final PolicyController controller;

        // enumeration: UEB or DMAAP
        private final CommInfrastructure protocol;

        // UEB/DMAAP topic
        private final String topic;

        // JSON message as a String
        private final String event;

        /**
         * Process this message locally using 'TopicListener.onTopicEvent'
         * (the 'PolicyController' instance is assumed to implement
         * the 'TopicListener' interface as well).
         */
        @Override
        public void process() {
            if (controller instanceof TopicListener) {
                /**
                 * This is the destination host -- repeat the 'onTopicEvent' method
                 * (the one that invoked 'beforeOffer' on the originating host).
                 * Note that this message could be forwarded again if the sender's
                 * bucket table was somehow different from ours -- perhaps there was
                 * an update in progress.
                 *
                 * TBD: it would be nice to limit the number of hops, in case we
                 * somehow have a loop.
                 */
                ((TopicListener)controller).onTopicEvent(protocol, topic, event);
            } else {
                /**
                 * This 'PolicyController' was also a 'TopicListener' on the sender's
                 * host -- it isn't on this host, and we are counting on them being
                 * configured the same way.
                 */
                logger.error("Controller {} is not a TopicListener",
                             controller.getName());
            }
        }

        /**
         * Send this message to a remote server for processing (presumably, it
         * is the destination host).
         *
         * @param server the Server instance to send the message to
         * @param bucketNumber the bucket number to send it to
         */
        @Override
        public void sendToServer(Server server, int bucketNumber) {
            /**
             * if we reach this point, we have determined the remote server
             * that should process this message
             */
            // @formatter:off
            logger.info("Outgoing topic message: Keyword={}, bucket={}\n"
                        + "    controller = {}"
                        + "    topic      = {}"
                        + "    sender     = {}"
                        + "    receiver   = {}",
                        keyword, bucketNumber, controller.getName(), topic,
                        Server.getThisServer().getUuid(), server.getUuid());
            // @formatter:on

            Entity<String> entity = Entity.entity(event, MediaType.APPLICATION_JSON);
            server.post("bucket/topic", entity, new Server.PostResponse() {
                @Override
                public WebTarget webTarget(WebTarget webTarget) {
                    return webTarget
                           .queryParam("bucket", bucketNumber)
                           .queryParam("keyword", keyword)
                           .queryParam("controller", controller.getName())
                           .queryParam("protocol", protocol.toString())
                           .queryParam("topic", topic);
                }

                @Override
                public void response(Response response) {
                    /**
                     * TBD: eventually, we will want to do something different
                     * based upon success/failure
                     */
                }
            });
        }
    }
}
