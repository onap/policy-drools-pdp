/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.event.comm;

/**
 * TopicSource that supports server-side filtering.
 */
public interface FilterableTopicSource extends TopicSource {

    /**
     * Sets the server-side filter.
     * 
     * @param filter new filter value, or {@code null}
     * @throws UnsupportedOperationException if the consumer does not support
     *         server-side filtering
     */
    public void setFilter(String filter);

}
