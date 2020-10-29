/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling;

import java.util.List;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the internal DMaaP topic. Assumes all topics are managed by
 * {@link TopicEndpoint#manager}.
 */
public class DmaapManager {

    private static final Logger logger = LoggerFactory.getLogger(DmaapManager.class);

    /**
     * Name of the DMaaP topic.
     */
    private final String topic;

    /**
     * Topic source whose filter is to be manipulated.
     */
    private final TopicSource topicSource;

    /**
     * Where to publish messages.
     */
    private final TopicSink topicSink;

    /**
     * {@code True} if the consumer is running, {@code false} otherwise.
     */
    private boolean consuming = false;

    /**
     * {@code True} if the publisher is running, {@code false} otherwise.
     */
    private boolean publishing = false;

    /**
     * Constructs the manager, but does not start the source or sink.
     *
     * @param topic name of the internal DMaaP topic
     * @throws PoolingFeatureException if an error occurs
     */
    public DmaapManager(String topic) throws PoolingFeatureException {

        logger.info("initializing bus for topic {}", topic);

        try {
            this.topic = topic;

            this.topicSource = findTopicSource();
            this.topicSink = findTopicSink();

        } catch (IllegalArgumentException e) {
            logger.error("failed to attach to topic {}", topic);
            throw new PoolingFeatureException(e);
        }
    }

    public String getTopic() {
        return topic;
    }

    /**
     * Finds the topic source associated with the internal DMaaP topic.
     *
     * @return the topic source
     * @throws PoolingFeatureException if the source doesn't exist or is not filterable
     */
    private TopicSource findTopicSource() throws PoolingFeatureException {
        for (TopicSource src : getTopicSources()) {
            if (topic.equals(src.getTopic())) {
                return src;
            }
        }

        throw new PoolingFeatureException("missing topic source " + topic);
    }

    /**
     * Finds the topic sink associated with the internal DMaaP topic.
     *
     * @return the topic sink
     * @throws PoolingFeatureException if the sink doesn't exist
     */
    private TopicSink findTopicSink() throws PoolingFeatureException {
        for (TopicSink sink : getTopicSinks()) {
            if (topic.equals(sink.getTopic())) {
                return sink;
            }
        }

        throw new PoolingFeatureException("missing topic sink " + topic);
    }

    /**
     * Starts the publisher, if it isn't already running.
     */
    public void startPublisher() {
        if (publishing) {
            return;
        }

        logger.info("start publishing to topic {}", topic);
        publishing = true;
    }

    /**
     * Stops the publisher.
     *
     * @param waitMs time, in milliseconds, to wait for the sink to transmit any queued messages and
     *        close
     */
    public void stopPublisher(long waitMs) {
        if (!publishing) {
            return;
        }

        /*
         * Give the sink a chance to transmit messages in the queue. It would be better if "waitMs"
         * could be passed to sink.stop(), but that isn't an option at this time.
         */
        try {
            Thread.sleep(waitMs);

        } catch (InterruptedException e) {
            logger.warn("message transmission stopped due to {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        logger.info("stop publishing to topic {}", topic);
        publishing = false;
    }

    /**
     * Starts the consumer, if it isn't already running.
     *
     * @param listener listener to register with the source
     */
    public void startConsumer(TopicListener listener) {
        if (consuming) {
            return;
        }

        logger.info("start consuming from topic {}", topic);
        topicSource.register(listener);
        consuming = true;
    }

    /**
     * Stops the consumer.
     *
     * @param listener listener to unregister with the source
     */
    public void stopConsumer(TopicListener listener) {
        if (!consuming) {
            return;
        }

        logger.info("stop consuming from topic {}", topic);
        consuming = false;
        topicSource.unregister(listener);
    }

    /**
     * Publishes a message to the sink.
     *
     * @param msg message to be published
     * @throws PoolingFeatureException if an error occurs or the publisher isn't running
     */
    public void publish(String msg) throws PoolingFeatureException {
        if (!publishing) {
            throw new PoolingFeatureException(new IllegalStateException("no topic sink " + topic));
        }

        try {
            if (!topicSink.send(msg)) {
                throw new PoolingFeatureException("failed to send to topic sink " + topic);
            }

        } catch (IllegalStateException e) {
            throw new PoolingFeatureException("cannot send to topic sink " + topic, e);
        }
    }

    /*
     * The remaining methods may be overridden by junit tests.
     */

    /**
     * Get topic source.
     *
     * @return the topic sources
     */
    protected List<TopicSource> getTopicSources() {
        return TopicEndpointManager.getManager().getTopicSources();
    }

    /**
     * Get topic sinks.
     *
     * @return the topic sinks
     */
    protected List<TopicSink> getTopicSinks() {
        return TopicEndpointManager.getManager().getTopicSinks();
    }
}
