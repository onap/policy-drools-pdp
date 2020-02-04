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

import java.util.Collection;

import org.onap.policy.common.utils.services.OrderedService;
import org.onap.policy.common.utils.services.OrderedServiceImpl;

public interface ServerPoolApi extends OrderedService {
    /**
     * 'ServerPoolApi.impl.getList()' returns an ordered list of objects
     * implementing the 'ServerPoolApi' interface.
     */
    public static OrderedServiceImpl<ServerPoolApi> impl =
        new OrderedServiceImpl<>(ServerPoolApi.class);

    /**
     * method gives all of the listening features the ability to add
     * classes to the 'HttpServletServer'.
     *
     * @return a Collection of classes implementing REST methods
     */
    public default Collection<Class<?>> servletClasses() {
        return null;
    }

    /**
     * This is called in the case where no bucket migration data was received
     * from the old owner of the bucket (such as if the old owner failed).
     * It gives one or more features the opportunity to do the restore.
     *
     * @param bucket the bucket that needs restoring
     */
    public default void restoreBucket(Bucket bucket) {
    }

    /**
     * This is called whenever a 'GlobalLocks' object is updated. It was added
     * in order to support persistence, but may be used elsewhere as well.
     *
     * @param bucket the bucket containing the 'GlobalLocks' adjunct
     * @param globalLocks the 'GlobalLocks' adjunct
     */
    public default void lockUpdate(Bucket bucket, TargetLock.GlobalLocks globalLocks) {
    }

    /**
     * This is called when the state of a bucket has changed, but is currently
     * stable, and it gives features the ability to do an audit. The intent is
     * to make sure that the adjunct state is correct; in particular, to remove
     * adjuncts that should no longer be there based upon the current state.
     * Note that this method is called while being synchronized on the bucket.
     *
     * @param bucket the bucket to audit
     * @param isOwner 'true' if the current host owns the bucket
     * @param isBackup 'true' if the current host is a backup for the bucket
     */
    public default void auditBucket(Bucket bucket, boolean isOwner, boolean isBackup) {
    }
}
