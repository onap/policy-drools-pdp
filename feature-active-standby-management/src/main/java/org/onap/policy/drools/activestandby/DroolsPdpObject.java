/*
 * ============LICENSE_START=======================================================
 * feature-active-standby-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.activestandby;


public abstract class DroolsPdpObject implements DroolsPdp {

    @Override
    public boolean equals(Object other) {
        if (other instanceof DroolsPdp) {
            return this.getPdpId().equals(((DroolsPdp) other).getPdpId());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.getPdpId() == null ? 0 : this.getPdpId().hashCode());
        return result;
    }

    private int nullSafeCompare(String one, String two) {
        if (one != null) {
            if (two != null) {
                return one.compareTo(two);

            } else {
                return 1;
            }

        } else if (two != null) {
            return -1;

        } else {
            return 0;
        }
    }

    @Override
    public int comparePriority(DroolsPdp other) {
        return commonCompare(other);
    }

    @Override
    public int comparePriority(DroolsPdp other, String previousSite) {
        if (previousSite == null || previousSite.isEmpty()) {
            return comparePriority(other);
        }
        return commonCompare(other);
    }

    private int commonCompare(DroolsPdp other) {
        if (nullSafeCompare(this.getSite(), other.getSite()) == 0) {
            if (this.getPriority() != other.getPriority()) {
                return this.getPriority() - other.getPriority();
            }
            return this.getPdpId().compareTo(other.getPdpId());
        } else {
            return nullSafeCompare(this.getSite(), other.getSite());
        }
    }
}
