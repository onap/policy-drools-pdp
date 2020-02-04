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

/**
 * This interface is an abstraction for all messages that are routed
 * through buckets. It exists, so that messages may be queued while
 * bucket migration is taking place, and it makes it possible to support
 * multiple types of messages (routed UEB/DMAAP messages, or lock messages).
 */
public interface Message {
    /**
     * Process the current message -- this may mean delivering it locally,
     * or forwarding it.
     */
    public void process();

    /**
     * Send the message to another host for processing.
     *
     * @param server the destination host (although it could end up being
     *     forwarded again)
     * @param bucketNumber the bucket number determined by extracting the
     *     current message's keyword
     */
    public void sendToServer(Server server, int bucketNumber);
}