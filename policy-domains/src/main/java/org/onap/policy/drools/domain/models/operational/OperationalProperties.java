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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;


/**
 * Operational Policy Properties.
 */

@Data
@Builder
public class OperationalProperties implements Serializable {
    private static final long serialVersionUID = 2455300363502597721L;

    /**
     * Control Loop Name.
     */
    private String id;

    /**
     * Timeout in seconds.
     */
    @Builder.Default
    private int timeout = 30;

    /**
     * Abatement.
     */
    @Builder.Default
    private boolean abatement = false;

    /**
     * Guard check should be disabled.
     */
    @Builder.Default
    private boolean guardDisabled = false;

    /**
     * Trigger Operation.
     */
    private String trigger;

    /**
     * Operations.
     */
    @Builder.Default
    private List<Operation> operations = new ArrayList<>();

    /**
     * Controller Name.
     */
    private String controllerName;
}
