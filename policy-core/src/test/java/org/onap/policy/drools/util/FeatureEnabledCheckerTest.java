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

package org.onap.policy.drools.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Properties;
import org.junit.Test;

public class FeatureEnabledCheckerTest {

    private static final String PROP_NAME = "enable.it";

    @Test
    public void test() {
        assertFalse(check(null));
        assertTrue(check(true));
        assertFalse(check(false));
    }

    /**
     * Adds properties, as specified, and checks if the feature is enabled.
     * 
     * @param want value to assign to the specialized property, or
     *        {@code null} to leave it unset
     * @return {@code true} if the feature is enabled, {@code false} otherwise
     */
    public boolean check(Boolean want) {
        Properties props = new Properties();

        if (want != null) {
            props.setProperty(PROP_NAME, want.toString());
        }

        return FeatureEnabledChecker.isFeatureEnabled(props, PROP_NAME);
    }

}
