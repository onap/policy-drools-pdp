/*-
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.domain.models.operational;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

/**
 * Policy Operation.
 */

@Data
@Builder
public class Operation implements Serializable {
    private static final long serialVersionUID = 6175229119078195110L;

    /**
     * Operation Identifier.
     */
    private String id;

    /**
     * Description.
     */
    private String description;

    /**
     * Actor Operation.
     */
    @SerializedName("operation")
    private ActorOperation actorOperation;

    /**
     * Operation Timeout in seconds.
     */
    @Builder.Default
    private int timeout = 10;

    /**
     * Number of Retries.
     */
    @Builder.Default
    private int retries = 0;

    /**
     * Success Treatment.
     */
    @Builder.Default
    private String success = "final_success";

    /**
     * Failure Treatment.
     */
    @Builder.Default
    private String failure = "final_failure";

    /**
     * Failure Timeout Treatment.
     */
    @SerializedName("failure_timeout")
    @Builder.Default
    private String failureTimeout = "final_failure_timeout";

    /**
     * Failure Retry Treatment.
     */
    @SerializedName("failure_retries")
    @Builder.Default
    private String failureRetries = "final_failure_retries";

    /**
     * Failure Exception Treatment.
     */
    @SerializedName("failure_exception")
    @Builder.Default
    private String failureException = "final_failure_exception";

    /**
     * Failure Guard Treatment.
     */
    @SerializedName("failure_guard")
    @Builder.Default
    private String failureGuard = "final_failure_guard";
}
