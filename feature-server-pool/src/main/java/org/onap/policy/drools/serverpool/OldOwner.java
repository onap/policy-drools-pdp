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

import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_TIME_TO_LIVE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_TIME_TO_LIVE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.onap.policy.drools.serverpool.Backup;
import org.onap.policy.drools.serverpool.Message;
import org.onap.policy.drools.serverpool.Restore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each state instance is associated with a bucket, and is used when
 * that bucket is in a transient state where it is the old owner of
 * a bucket, and the data is being transferred to the new owner.
 */
public class OldOwner extends Thread implements State {
    private static Logger logger = LoggerFactory.getLogger(OldOwner.class);

    Server newOwner;

    // current bucket
    Bucket bucket;
    
    private static String timeToLiveSecond;
    
    static void startup() {
        int intTimeToLive =
            getProperty(BUCKET_TIME_TO_LIVE, DEFAULT_BUCKET_TIME_TO_LIVE);
        timeToLiveSecond = String.valueOf(intTimeToLive);
    }

    OldOwner(Server newOwner, Bucket bucket) {
        super("Old Owner for Bucket " + bucket.getBucketNumber());
        this.newOwner = newOwner;
        this.bucket = bucket;
        start();
    }

    /*********************/
    /* 'State' interface */
    /*********************/

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean forward(Message message) {
        // forward message to new owner
        message.sendToServer(newOwner, bucket.getBucketNumber());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newOwner() {
        // shouldn't happen -- just log an error
        logger.error("{}: 'newOwner()' shouldn't be called in this state", this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bulkSerializedData(byte[] data) {
        // shouldn't happen -- just log an error
        logger.error("{}: 'bulkSerializedData()' shouldn't be called in this state", this);
    }

    /**********************/
    /* 'Thread' interface */
    /**********************/

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        logger.info("{}: 'run' method invoked", this);
        try {
            // go through all of the entries in the list, collecting restore data
            List<Restore> restoreData = new LinkedList<>();
            List<Backup> backupList = bucket.getBackupList();
            for (Backup backup : backupList) {
                Restore restore = backup.generate(bucket.getBucketNumber());
                if (restore != null) {
                    restoreData.add(restore);
                }
            }

            /**
             * serialize all of the objects,
             * and send what we have to the new owner.
             */
            Entity<String> entity = Entity.entity(
                new String(Base64.getEncoder().encode(Util.serialize(restoreData)), 
                    StandardCharsets.UTF_8),
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
            newOwner.post("bucket/sessionData", entity, new Server.PostResponse() {
                @Override
                public WebTarget webTarget(WebTarget webTarget) {
                    return webTarget
                           .queryParam("bucket", bucket.getBucketNumber())
                           .queryParam("dest", newOwner.getUuid())
                           .queryParam("ttl", timeToLiveSecond);
                }

                @Override
                public void response(Response response) {
                    logger.info("/bucket/sessionData response code = {}",
                                response.getStatus());
                }
            });
        } catch (Exception e) {
            logger.error("Exception in {}", this, e);
        } finally {
            synchronized (this) {
                // restore the state
                if (bucket.getState() == this) {
                    bucket.setState(null);
                    bucket.stateChanged();
                }
            }
        }
    }

    /**
     * Return a useful value to display in log messages.
     *
     * @return a useful value to display in log messages
     */
    public String toString() {
        return "Bucket.OldOwner(" + bucket.getBucketNumber() + ")";
    }
}