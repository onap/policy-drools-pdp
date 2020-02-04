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

import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_CONFIRMED_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_UNCONFIRMED_GRACE_PERIOD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_UNCONFIRMED_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_CONFIRMED_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_UNCONFIRMED_GRACE_PERIOD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_UNCONFIRMED_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

//import org.onap.policy.drools.serverpool.Bucket;
//import org.onap.policy.drools.serverpool.Server;
//import org.onap.policy.drools.serverpool.ServerPoolApi;
//import org.onap.policy.drools.serverpool.State;
//import org.onap.policy.drools.serverpool.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Each state instance is associated with a bucket, and is used when
 * that bucket is in a transient state where it is the new owner of a
 * bucket, or is presumed to be the new owner, based upon other events
 * that have occurred.
*/
public class NewOwner extends Thread implements State {
    private static Logger logger = LoggerFactory.getLogger(Bucket.class);
    /**
     * this value is 'true' if we have explicitly received a 'newOwner'
     * indication, and 'false' if there was another trigger for entering this
     * transient state (e.g. receiving serialized data).
     */
    boolean confirmed;

    // when 'System.currentTimeMillis()' reaches this value, we time out
    long endTime;

    /**
     * If not 'null', we are queueing messages for this bucket;
     * otherwise, we are sending them through.
     */
    Queue<Message> messages = new ConcurrentLinkedQueue<>();

    // this is used to signal the thread that we have data available
    CountDownLatch dataAvailable = new CountDownLatch(1);

    // this is the data
    byte[] data = null;

    // this is the old owner of the bucket
    Server oldOwner;
    
    // current bucket
    Bucket bucket;
    
    private static long confirmedTimeout;
    private static long unconfirmedTimeout;
    private static long unconfirmedGracePeriod;
    
    static void startup() {
        confirmedTimeout =
            getProperty(BUCKET_CONFIRMED_TIMEOUT, DEFAULT_BUCKET_CONFIRMED_TIMEOUT);
        unconfirmedTimeout =
            getProperty(BUCKET_UNCONFIRMED_TIMEOUT,
                        DEFAULT_BUCKET_UNCONFIRMED_TIMEOUT);
        unconfirmedGracePeriod =
            getProperty(BUCKET_UNCONFIRMED_GRACE_PERIOD,
                        DEFAULT_BUCKET_UNCONFIRMED_GRACE_PERIOD);
    }

    /**
     * Constructor - a transient state, where we are expecting to receive
     * bulk data from the old owner.
     *
     * @param confirmed 'true' if we were explicitly notified that we
     *      are the new owner of the bucket, 'false' if not
     */
    NewOwner(boolean confirmed, Server oldOwner, Bucket bucket) {
        super("New Owner for Bucket " + bucket.getBucketNumber());
        this.confirmed = confirmed;
        this.oldOwner = oldOwner;
        this.bucket = bucket;
        if (oldOwner == null) {
            // we aren't expecting any data -- this is indicated by 0-length data
            bulkSerializedData(new byte[0]);
        }
        endTime = System.currentTimeMillis()
                  + (confirmed ? confirmedTimeout : unconfirmedTimeout);
        start();
    }

    /**
     * Return the 'confirmed' indicator.
     *
     * @return the 'confirmed' indicator
     */
    private synchronized boolean getConfirmed() {
        return confirmed;
    }

    /**
     * This returns the timeout delay, which will always be less than or
     * equal to 1 second. This allows us to periodically check whether the
     * old server is still active.
     *
     * @return the timeout delay, which is the difference between the
     *      'endTime' value and the current time or 1 second
     *      (whichever is less)
     */
    private synchronized long getTimeout() {
        long lclEndTime;
        lclEndTime = endTime;
        
        return Math.min(lclEndTime - System.currentTimeMillis(), 1000L);
    }

    /**
     * Return the current value of the 'data' field.
     *
     * @return the current value of the 'data' field
     */
    private synchronized byte[] getData() {
        return data;
    }

    /*********************/
    /* 'State' interface */
    /*********************/

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean forward(Message message) {
        // the caller of this method is synchronized on 'Bucket.this'
        if (messages != null && Thread.currentThread() != this) {
            // just queue the message
            messages.add(message);
            return true;
        } else {
            /**
             * Either:
             *
             * 1) We are in a grace period, where 'state' is still set, but
             *    we are no longer forwarding messages.
             * 2) We are calling 'message.process()' from this thread
             *    in the 'finally' block of 'NewOwner.run()'.
             *
             * In either case, messages should be processed locally.
             */
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newOwner() {
        // the caller of this method is synchronized on 'Bucket.this'
        if (!confirmed) {
            confirmed = true;
            endTime += (confirmedTimeout - unconfirmedTimeout);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bulkSerializedData(byte[] data) {
        // the caller of this method is synchronized on 'Bucket.this'
        if (this.data == null) {
            this.data = data;
            dataAvailable.countDown();
        }
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
            byte[] lclData;
            long delay;

            while ((lclData = getData()) == null
                    && oldOwner.isActive()
                    && (delay = getTimeout()) > 0) {
                // ignore return value -- 'data' will indicate the result
                dataAvailable.await(delay, TimeUnit.MILLISECONDS);
            }
            if (lclData == null) {
                // no data available -- log an error, and abort
                if (getConfirmed()) {
                    // we never received the data, but we are the new owner
                    logger.error("{}: never received session data", this);
                } else {
                    /**
                     * no data received, and it was never confirmed --
                     * assume that the forwarded message that triggered this was
                     * erroneus.
                     */
                    logger.error("{}: no confirmation or data received -- aborting", this);
                    return;
                }
            } else {
                logger.info("{}: {} bytes of data available",
                            this, lclData.length);
            }

            // if we reach this point, this server is the new owner
            if (lclData == null || lclData.length == 0) {
                // see if any features can do the restore
                for (ServerPoolApi feature : ServerPoolApi.impl.getList()) {
                    feature.restoreBucket(bucket);
                }
            } else {
                // deserialize data
                Object obj = Util.deserialize(lclData);
                restoreBucketData(obj);
            }
        } catch (Exception e) {
            logger.error("Exception in {}", this, e);
        } finally {
            /**
             * cleanly leave state -- we want to make sure that messages
             * are processed in order, so the queue needs to remain until
             * it is empty.
             */
            logger.info("{}: entering cleanup state", this);
            for ( ; ; ) {
                Message message = messages.poll();
                if (message == null) {
                    // no messages left, but this could change
                    synchronized (this) {
                        message = messages.poll();
                        if (message == null) {
                            // no messages left
                            if (bucket.getState() == this) {
                                if (bucket.getOwner() == Server.getThisServer()) {
                                    // we can now exit the state
                                    bucket.setState(null);
                                    bucket.stateChanged();
                                } else {
                                    /**
                                     * We need a grace period before we can
                                     * remove the 'state' value (this can happen
                                     * if we receive and process the bulk data
                                     * before receiving official confirmation
                                     * that we are owner of the bucket.
                                     */
                                    messages = null;
                                }
                            }
                            break;
                        }
                    }
                }
                /**
                 * this doesn't work -- it ends up right back in the queue
                 * if 'messages' is defined.
                 */
                message.process();
            }
            if (messages == null) {
                // this indicates we need to enter a grace period before cleanup,
                try {
                    logger.info("{}: entering grace period before terminating",
                                this);
                    Thread.sleep(unconfirmedGracePeriod);
                } catch (InterruptedException e) {
                    // we are exiting in any case
                    Thread.currentThread().interrupt();
                } finally {
                    synchronized (this) {
                        /**
                         * Do we need to confirm that we really are the owner?
                         * What does it mean if we are not?.
                         */
                        if (bucket.getState() == this) {
                            bucket.setState(null);
                            bucket.stateChanged();
                        }
                    }
                }
            }
            logger.info("{}: exiting cleanup state", this);
        }
    }

    /**
     * Restore bucket data.
     *
     * @param obj deserialized bucket data
     */
    private void restoreBucketData(Object obj) {
        if (obj instanceof List) {
            for (Object entry : (List<?>)obj) {
                if (entry instanceof Restore) {
                    // entry-specific 'restore' operation
                    ((Restore)entry).restore(bucket.getBucketNumber());
                } else {
                    logger.error("{}: Expected '{}' but got '{}'",
                                 this, Restore.class.getName(),
                                 entry.getClass().getName());
                }
            }
        } else {
            logger.error("{}: expected 'List' but got '{}'",
                         this, obj.getClass().getName());
        }
    }

    /**
     * Return a useful value to display in log messages.
     *
     * @return a useful value to display in log messages
     */
    public String toString() {
        return "Bucket.NewOwner(" + bucket.getBucketNumber() + ")";
    }
}