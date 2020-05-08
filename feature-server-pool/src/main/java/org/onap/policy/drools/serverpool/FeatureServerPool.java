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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import lombok.AllArgsConstructor;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.coder.StandardCoderObject;
import org.onap.policy.drools.control.api.DroolsPdpStateControlApi;
import org.onap.policy.drools.core.DroolsRunnable;
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
import org.onap.policy.drools.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * </p>
 * This class hooks the server pool implementation into DroolsPDP.
 * <dl>
 * <dt>PolicyEngineFeatureApi</dt><dd> - the <i>afterStart</i> hook is where we initialize.</dd>
 * <dt>PolicyControllerFeatureApi</dt><dd> - the <i>beforeOffer</i> hook is used to look
 *     at incoming topic messages, and decide whether to process them
 *     on this host, or forward to another host.</dd>
 * </dl>
 */
public class FeatureServerPool
    implements PolicyEngineFeatureApi, PolicySessionFeatureApi,
    PolicyControllerFeatureApi, DroolsPdpStateControlApi {
    private static Logger logger =
        LoggerFactory.getLogger(FeatureServerPool.class);

    // used for JSON <-> String conversion
    private static StandardCoder coder = new StandardCoder();

    private static final String CONFIG_FILE =
            "config/feature-server-pool.properties";

    /*
     * Properties used when searching for keyword entries
     *
     * The following types are supported:
     *
     * 1) keyword.<topic>.path=<field-list>
     * 2) keyword.path=<field-list>
     * 3) ueb.source.topics.<topic>.keyword=<field-list>
     * 4) ueb.source.topics.keyword=<field-list>
     * 5) dmaap.source.topics.<topic>.keyword=<field-list>
     * 6) dmaap.source.topics.keyword=<field-list>
     *
     * 1, 3, and 5 are functionally equivalent
     * 2, 4, and 6 are functionally equivalent
     */

    static final String KEYWORD_PROPERTY_START_1 = "keyword.";
    static final String KEYWORD_PROPERTY_END_1 = ".path";
    static final String KEYWORD_PROPERTY_START_2 = "ueb.source.topics.";
    static final String KEYWORD_PROPERTY_END_2 = ".keyword";
    static final String KEYWORD_PROPERTY_START_3 = "dmaap.source.topics.";
    static final String KEYWORD_PROPERTY_END_3 = ".keyword";

    /*
     * maps topic names to a keyword table derived from <field-list> (above)
     *
     * Example <field-list>: requestID,CommonHeader.RequestID
     *
     * Table generated from this example has length 2:
     * table[0] = {"requestID"}
     * table[1] = {"CommonHeader", "RequestID"}
     */
    private static HashMap<String,String[][]> topicToPaths = new HashMap<>();

    // this table is used for any topics that aren't in 'topicToPaths'
    private static String[][] defaultPaths = new String[0][];

    // extracted from properties
    private static long droolsTimeoutMillis;
    private static String timeToLiveSecond;

    // HTTP query parameters
    private static final String QP_KEYWORD = "keyword";
    private static final String QP_SESSION = "session";
    private static final String QP_BUCKET = "bucket";
    private static final String QP_TTL = "ttl";
    private static final String QP_CONTROLLER = "controller";
    private static final String QP_PROTOCOL = "protocol";
    private static final String QP_TOPIC = "topic";

    /******************************/
    /* 'OrderedService' interface */
    /******************************/

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSequenceNumber() {
        // we need to make sure we have an early position in 'selectThreadModel'
        // (in case there is feature that provides a thread model)
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
        Server.startup(CONFIG_FILE);
        TargetLock.startup();
        droolsTimeoutMillis =
            getProperty(BUCKET_DROOLS_TIMEOUT, DEFAULT_BUCKET_DROOLS_TIMEOUT);
        int intTimeToLive =
            getProperty(BUCKET_TIME_TO_LIVE, DEFAULT_BUCKET_TIME_TO_LIVE);
        timeToLiveSecond = String.valueOf(intTimeToLive);
        buildKeywordTable();
        Bucket.Backup.register(new DroolsSessionBackup());
        Bucket.Backup.register(new TargetLock.LockBackup());
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

        /*
         * 'keyword' determines the destination host,
         * which may be local or remote
         */
        Bucket.forwardAndProcess(keyword, new Bucket.Message() {
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
                // this object needs to sent to a remote host --
                // first, serialize the object
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
                                .queryParam(QP_KEYWORD, keyword)
                                .queryParam(QP_SESSION, encodedSessionName)
                                .queryParam(QP_BUCKET, bucketNumber)
                                .queryParam(QP_TTL, timeToLiveSecond);
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
            /*
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
            if (keyword != null) {
                if (conversionFunctionName == null) {
                    // We found a keyword -- we don't need to try other paths,
                    // so we should break out of the loop
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

        /*
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

        /*
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
    static void topicMessage(
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
            /*
             * This controller existed on the sender's host, but doesn't exist
             * on the destination. This is a problem -- we are counting on all
             * hosts being configured with the same controllers.
             */
            logger.error("Can't locate controller '{}' for incoming topic message",
                         controllerName);
        } else if (controller instanceof TopicListener) {
            /*
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
            /*
             * This 'PolicyController' was also a 'TopicListener' on the sender's
             * host -- it isn't on this host, and we are counting on them being
             * config
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
    static void incomingInsertDrools(
        String keyword, String sessionName, int bucket, int ttl, byte[] data) {

        logger.info("Incoming insertDrools: keyword={}, session={}, bucket={}, ttl={}",
            keyword, sessionName, bucket, ttl);

        if (Bucket.isKeyOnThisServer(keyword)) {
            // process locally

            // [0]="<groupId>" [1]="<artifactId>", [2]="<sessionName>"
            String[] nameSegments = sessionName.split(":");

            // locate the 'PolicyContainer' and 'PolicySession'
            PolicySession policySession = locatePolicySession(nameSegments);

            if (policySession == null) {
                logger.error("incomingInsertDrools: Can't find PolicySession={}",
                             sessionName);
            } else {
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
            }
        } else {
            ttl -= 1;
            if (ttl > 0) {
                /*
                 * This host is not the intended destination -- this could happen
                 * if it was sent from another site. Forward the message in the
                 * same thread.
                 */
                forwardInsertDroolsMessage(bucket, keyword, sessionName, ttl, data);
            }
        }
    }

    /**
     * step through all 'PolicyContainer' instances looking
     * for a matching 'artifactId' & 'groupId'.
     * @param nameSegments name portion from sessionName
     * @return policySession match artifactId and groupId
     */
    private static PolicySession locatePolicySession(String[] nameSegments) {
        PolicySession policySession = null;
        if (nameSegments.length == 3) {
            for (PolicyContainer pc : PolicyContainer.getPolicyContainers()) {
                if (nameSegments[1].equals(pc.getArtifactId())
                    && nameSegments[0].equals(pc.getGroupId())) {
                    policySession = pc.getPolicySession(nameSegments[2]);
                    break;
                }
            }
        }
        return policySession;
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
            .queryParam(QP_KEYWORD, keyword)
            .queryParam(QP_SESSION, sessionName)
            .queryParam(QP_BUCKET, bucket)
            .queryParam(QP_TTL, ttl)
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

        // iterate over all of the properties, picking out those we are
        // interested in
        for (String name : prop.stringPropertyNames()) {
            String topic = null;
            String begin;
            String end;

            if (name.startsWith(KEYWORD_PROPERTY_START_1)
                    && name.endsWith(KEYWORD_PROPERTY_END_1)) {
                // 1) keyword.<topic>.path=<field-list>
                // 2) keyword.path=<field-list>
                begin = KEYWORD_PROPERTY_START_1;
                end = KEYWORD_PROPERTY_END_1;
            } else if (name.startsWith(KEYWORD_PROPERTY_START_2)
                       && name.endsWith(KEYWORD_PROPERTY_END_2)) {
                // 3) ueb.source.topics.<topic>.keyword=<field-list>
                // 4) ueb.source.topics.keyword=<field-list>
                begin = KEYWORD_PROPERTY_START_2;
                end = KEYWORD_PROPERTY_END_2;
            } else if (name.startsWith(KEYWORD_PROPERTY_START_3)
                       && name.endsWith(KEYWORD_PROPERTY_END_3)) {
                // 5) dmaap.source.topics.<topic>.keyword=<field-list>
                // 6) dmaap.source.topics.keyword=<field-list>
                begin = KEYWORD_PROPERTY_START_3;
                end = KEYWORD_PROPERTY_END_3;
            } else {
                // we aren't interested in this property
                continue;
            }

            int beginIndex = begin.length();
            int endIndex = name.length() - end.length();
            if (beginIndex < endIndex) {
                // <topic> is specified, so this table is limited to this
                // specific topic
                topic = name.substring(beginIndex, endIndex);
            }

            // now, process the value
            // Example: requestID,CommonHeader.RequestID
            String[] commaSeparatedEntries = prop.getProperty(name).split(",");
            String[][] paths = new String[commaSeparatedEntries.length][];
            for (int i = 0 ; i < commaSeparatedEntries.length ; i += 1) {
                paths[i] = commaSeparatedEntries[i].split("\\.");
            }

            if (topic == null) {
                // these paths are used for any topics not explicitly
                // in the 'topicToPaths' table
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

    /*
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

    /*
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
    private static class TopicMessage implements Bucket.Message {
        /*
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
                /*
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
                /*
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
            // if we reach this point, we have determined the remote server
            // that should process this message

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
                           .queryParam(QP_BUCKET, bucketNumber)
                           .queryParam(QP_KEYWORD, keyword)
                           .queryParam(QP_CONTROLLER, controller.getName())
                           .queryParam(QP_PROTOCOL, protocol.toString())
                           .queryParam(QP_TOPIC, topic);
                }

                @Override
                public void response(Response response) {
                    // log a message indicating success/failure
                    int status = response.getStatus();
                    if (status >= 200 && status <= 299) {
                        logger.info("/bucket/topic response code = {}", status);
                    } else {
                        logger.error("/bucket/topic response code = {}", status);
                    }
                }
            });
        }
    }

    /* ============================================================ */

    /**
     * Backup data associated with a Drools session.
     */
    static class DroolsSessionBackup implements Bucket.Backup {
        /**
         * {@inheritDoc}
         */
        @Override
        public Bucket.Restore generate(int bucketNumber) {
            // Go through all of the Drools sessions, and generate backup data.
            // If there is no data to backup for this bucket, return 'null'

            DroolsSessionRestore restore = new DroolsSessionRestore();
            return restore.backup(bucketNumber) ? restore : null;
        }
    }

    /* ============================================================ */

    /**
     * This class is used to generate and restore backup Drools data.
     */
    static class DroolsSessionRestore implements Bucket.Restore, Serializable {
        // backup data for all Drools sessions on this host
        private final List<SingleSession> sessions = new LinkedList<>();

        /**
         * {@inheritDoc}
         */
        boolean backup(int bucketNumber) {
            /*
             * There may be multiple Drools sessions being backed up at the same
             * time. There is one 'Pair' in the list for each session being
             * backed up.
             */
            LinkedList<Pair<CompletableFuture<List<Object>>, PolicySession>>
                pendingData = new LinkedList<>();
            for (PolicyContainer pc : PolicyContainer.getPolicyContainers()) {
                for (PolicySession session : pc.getPolicySessions()) {
                    // Wraps list of objects, to be populated in the session
                    final CompletableFuture<List<Object>> droolsObjectsWrapper =
                        new CompletableFuture<>();

                    // 'KieSessionObject'
                    final KieSession kieSession = session.getKieSession();

                    logger.info("{}: about to fetch data for session {}",
                                this, session.getFullName());
                    DroolsRunnable backupAndRemove = () -> {
                        List<Object> droolsObjects = new ArrayList<>();
                        for (FactHandle fh : kieSession.getFactHandles()) {
                            Object obj = kieSession.getObject(fh);
                            String keyword = Keyword.lookupKeyword(obj);
                            if (keyword != null
                                    && Bucket.bucketNumber(keyword) == bucketNumber) {
                                // bucket matches -- include this object
                                droolsObjects.add(obj);
                                /*
                                 * delete this factHandle from Drools memory
                                 * this classes are used in bucket migration,
                                 * so the delete is intentional.
                                 */
                                kieSession.delete(fh);
                            }
                        }

                        // send notification that object list is complete
                        droolsObjectsWrapper.complete(droolsObjects);
                    };
                    kieSession.insert(backupAndRemove);

                    // add pending operation to the list
                    pendingData.add(new Pair<>(droolsObjectsWrapper, session));
                }
            }

            /**
             * data copying can start as soon as we receive results
             * from pending sessions (there may not be any)
             */
            copyDataFromSession(pendingData);
            return !sessions.isEmpty();
        }

        /**
         * Copy data from pending sessions.
         * @param pendingData a list of policy sessions
         */
        private void copyDataFromSession(List<Pair<CompletableFuture<List<Object>>, PolicySession>>
            pendingData) {
            long endTime = System.currentTimeMillis() + droolsTimeoutMillis;

            for (Pair<CompletableFuture<List<Object>>, PolicySession> pair :
                    pendingData) {
                PolicySession session = pair.second();
                long delay = endTime - System.currentTimeMillis();
                if (delay < 0) {
                    /**
                     * we have already reached the time limit, so we will
                     * only process data that has already been received
                     */
                    delay = 0;
                }
                try {
                    List<Object> droolsObjects =
                        pair.first().get(delay, TimeUnit.MILLISECONDS);

                    // if we reach this point, session data read has completed
                    logger.info("{}: session={}, got {} object(s)",
                                this, session.getFullName(),
                                droolsObjects.size());
                    if (!droolsObjects.isEmpty()) {
                        sessions.add(new SingleSession(session, droolsObjects));
                    }
                } catch (TimeoutException e) {
                    logger.error("{}: Timeout waiting for data from session {}",
                        this, session.getFullName());
                } catch (Exception e) {
                    logger.error("{}: Exception writing output data", this, e);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void restore(int bucketNumber) {
            /*
             * There may be multiple Drools sessions being restored at the same
             * time. There is one entry in 'sessionLatches' for each session
             * being restored.
             */
            LinkedList<CountDownLatch> sessionLatches = new LinkedList<>();
            for (SingleSession session : sessions) {
                try {
                    CountDownLatch sessionLatch = session.restore();
                    if (sessionLatch != null) {
                        // there is a restore in progress -- add it to the list
                        sessionLatches.add(sessionLatch);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    logger.error("Exception in {}", this, e);
                }
            }

            // wait for all sessions to be updated
            try {
                for (CountDownLatch sessionLatch : sessionLatches) {
                    if (!sessionLatch.await(droolsTimeoutMillis, TimeUnit.MILLISECONDS)) {
                        logger.error("{}: timed out waiting for session latch", this);
                    }
                }
            } catch (InterruptedException e) {
                logger.error("Exception in {}", this, e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /* ============================================================ */

    /**
     * Each instance of this class corresponds to a Drools session that has
     * been backed up, or is being restored.
     */
    static class SingleSession implements Serializable {
        // the group id associated with the Drools container
        String groupId;

        // the artifact id associated with the Drools container
        String artifactId;

        // the session name within the Drools container
        String sessionName;

        // serialized data associated with this session (and bucket)
        byte[] data;

        /**
         * Constructor - initialize the 'SingleSession' instance, so it can
         * be serialized.
         *
         * @param session the Drools session being backed up
         * @param droolsObjects the Drools objects from this session associated
         *     with the bucket currently being backed up
         */
        SingleSession(PolicySession session, List<Object> droolsObjects) throws IOException {
            // 'groupId' and 'artifactId' are set from the 'PolicyContainer'
            PolicyContainer pc = session.getPolicyContainer();
            groupId = pc.getGroupId();
            artifactId = pc.getArtifactId();

            // 'sessionName' is set from the 'PolicySession'
            sessionName = session.getName();

            /*
             * serialize the Drools objects -- we serialize them here, because they
             * need to be deserialized within the scope of the Drools session
             */
            data = Util.serialize(droolsObjects);
        }

        CountDownLatch restore() throws IOException, ClassNotFoundException {
            PolicySession session = null;

            // locate the 'PolicyContainer', and 'PolicySession'
            for (PolicyContainer pc : PolicyContainer.getPolicyContainers()) {
                if (artifactId.equals(pc.getArtifactId())
                        && groupId.equals(pc.getGroupId())) {
                    session = pc.getPolicySession(sessionName);
                    return insertSessionData(session, new ByteArrayInputStream(data));
                }
            }
            logger.error("{}: unable to locate session name {}", this, sessionName);
            return null;
        }

        /**
         * Deserialize session data, and insert the objects into the session
         * from within the Drools session thread.
         *
         * @param session the associated PolicySession instance
         * @param bis the data to be deserialized
         * @return a CountDownLatch, which will indicate when the operation has
         *     completed (null in case of failure)
         * @throws IOException IO errors while creating or reading from
         *     the object stream
         * @throws ClassNotFoundException class not found during deserialization
         */
        private CountDownLatch insertSessionData(PolicySession session, ByteArrayInputStream bis)
            throws IOException, ClassNotFoundException {
            ClassLoader classLoader = session.getPolicyContainer().getClassLoader();
            ExtendedObjectInputStream ois =
                new ExtendedObjectInputStream(bis, classLoader);

            /*
             * associate the current thread with the session,
             * and deserialize
             */
            session.setPolicySession();
            Object obj = ois.readObject();

            if (obj instanceof List) {
                final List<?> droolsObjects = (List<?>)obj;
                logger.info("{}: session={}, got {} object(s)",
                            this, session.getFullName(), droolsObjects.size());

                // signal when session update is complete
                final CountDownLatch sessionLatch = new CountDownLatch(1);

                // 'KieSession' object
                final KieSession kieSession = session.getKieSession();

                // run the following within the Drools session thread
                DroolsRunnable doRestore = () -> {
                    try {
                        /*
                         * Insert all of the objects -- note that this is running
                         * in the session thread, so no other rules can fire
                         * until all of the objects are inserted.
                         */
                        for (Object droolsObj : droolsObjects) {
                            kieSession.insert(droolsObj);
                        }
                    } finally {
                        // send notification that the inserts have completed
                        sessionLatch.countDown();
                    }
                };
                kieSession.insert(doRestore);
                return sessionLatch;
            } else {
                logger.error("{}: Invalid session data for session={}, type={}",
                             this, session.getFullName(), obj.getClass().getName());
            }
            return null;
        }
    }
}
