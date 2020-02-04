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
 * This interface corresponds to a transient state within a Bucket.
 */
public interface State {
    /**
     * This method allows state-specific handling of the
     * 'Bucket.forward()' methods
     *
     * @param message the message to be forwarded/processed
     * @return a value of 'true' indicates the message has been "handled"
     *      (forwarded or queued), and 'false' indicates it has not, and needs
     *      to be handled locally.
     */
    boolean forward(Message message);

    /**
     * This method indicates that the current server is the new owner
     * of the current bucket.
     */
    void newOwner();

    /**
     * This method indicates that serialized data has been received,
     * presumably from the old owner of the bucket. The data could correspond
     * to Drools objects within sessions, as well as global locks.
     *
     * @param data serialized data associated with this bucket (at present,
     *      this is assumed to be complete, all within a single message)
     */
    void bulkSerializedData(byte[] data);
}