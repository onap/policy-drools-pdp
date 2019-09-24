/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.simple.locking;

import java.util.Properties;
import lombok.Getter;
import lombok.Setter;
import org.onap.policy.common.utils.properties.BeanConfigurator;
import org.onap.policy.common.utils.properties.Property;
import org.onap.policy.common.utils.properties.exception.PropertyException;


@Getter
@Setter
public class SimpleLockingProperties {

    public static final String EXPIRE_CHECK_SEC = "expire.check.seconds";

    /**
     * Time, in seconds, to wait between checks for expired locks.
     */
    @Property(name = EXPIRE_CHECK_SEC, defaultValue = "900")
    private int expireCheckSec;

    /**
     * Constructs the object, populating fields from the properties.
     *
     * @param props properties from which to configure this
     * @throws PropertyException if an error occurs
     */
    public SimpleLockingProperties(Properties props) throws PropertyException {
        new BeanConfigurator().configureFromProperties(this, props);
    }
}
