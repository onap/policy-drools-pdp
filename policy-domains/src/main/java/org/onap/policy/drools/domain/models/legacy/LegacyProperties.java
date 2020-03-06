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

package org.onap.policy.drools.domain.models.legacy;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;


/**
 * Legacy Operational Policy Properties.
 */

@Data
@Builder
public class LegacyProperties implements Serializable {
    private static final long serialVersionUID = 2455300363502597721L;

    /**
     * Content (Operational Policy URL encoded yaml).
     */
    private String content;

    /**
     * Controller Name (optional) to select an specific controller among many that
     * support a policy type.
     */
    private String controllerName;
}
