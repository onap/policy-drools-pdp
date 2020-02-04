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
 * There is a single instance of this class (Bucket.eventHandler), which
 * is registered to listen for notifications of state transitions. Note
 * that all of these methods are running within the 'MainLoop' thread.
 */
public class EventHandler implements Events {
    /**
     * {@inheritDoc}
     */
    @Override
    public void serverFailed(Server server) {
        // remove this server from any bucket where it is referenced

        Server thisServer = Server.getThisServer();
        Bucket[] indexToBucket = Bucket.getIndexToBucket();
        for (Bucket bucket : indexToBucket) {
            boolean changes = false;
            if (bucket.getOwner() == server) {
                /**
                 * the failed server owns this bucket --
                 * move to the primary backup.
                 */
                bucket.setOwner(bucket.getPrimaryBackup());
                bucket.setPrimaryBackup(null);
                changes = true;

                if (bucket.getOwner() == null) {
                    /**
                     * bucket owner is still null -- presumably, we had no
                     * primary backup, so use the secondary backup instead.
                     */
                    Server secondaryBackup = bucket.getSecondaryBackup();
                    bucket.setOwner(secondaryBackup);
                    bucket.setSecondaryBackup(null);
                }
            }
            if (bucket.getPrimaryBackup() == server) {
                /**
                 * the failed server was a primary backup to this bucket --
                 * mark the entry as 'null'.
                 */
                bucket.setPrimaryBackup(null);
                changes = true;
            }
            if (bucket.getSecondaryBackup() == server) {
                /**
                 * the failed server was a secondary backup to this bucket --
                 * mark the entry as 'null'.
                 */
                bucket.setSecondaryBackup(null);
                changes = true;
            }

            if (bucket.getOwner() == thisServer && bucket.getState() == null) {
                // the current server is the new owner
                bucket.setState(new NewOwner(false, null, bucket));
                changes = true;
            }

            if (changes) {
                // may give audits a chance to run
                bucket.stateChanged();
            }
        }

        // trigger a rebalance (only happens if we are the lead server)
        Bucket.rebalance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newLeader(Server server) {
        // trigger a rebalance (only happens if we are the new lead server)
        Bucket.rebalance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void leaderConfirmed(Server server) {
        // trigger a rebalance (only happens if we are the lead server)
        Bucket.rebalance();
    }
}