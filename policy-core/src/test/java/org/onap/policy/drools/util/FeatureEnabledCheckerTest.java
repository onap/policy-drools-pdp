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
import static org.onap.policy.common.utils.properties.SpecPropertyConfiguration.generalize;
import static org.onap.policy.common.utils.properties.SpecPropertyConfiguration.specialize;
import java.util.Properties;
import org.junit.Test;
import org.onap.policy.drools.util.FeatureEnabledChecker;

public class FeatureEnabledCheckerTest {

    private static final String PROP_NAME = "enable.{?.}it";

    private static final String SPEC = "my.specializer";

    @Test
    public void test() {
        assertFalse(check(null, null));
        assertTrue(check(null, true));
        assertFalse(check(null, false));

        assertTrue(check(true, null));
        assertTrue(check(true, true));
        assertFalse(check(true, false));

        assertFalse(check(false, null));
        assertTrue(check(false, true));
        assertFalse(check(false, false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_ArgEx() {
        
        // check case where there's an exception in the property
        Properties props = new Properties();
        props.setProperty(generalize(PROP_NAME), "invalid-boolean");
        
        assertFalse(FeatureEnabledChecker.isFeatureEnabled(props, SPEC, PROP_NAME));        
    }

    /**
     * Adds properties, as specified, and checks if the feature is enabled.
     * 
     * @param wantGen value to assign to the generalized property, or
     *        {@code null} to leave it unset
     * @param wantSpec value to assign to the specialized property, or
     *        {@code null} to leave it unset
     * @return {@code true} if the feature is enabled, {@code false} otherwise
     */
    public boolean check(Boolean wantGen, Boolean wantSpec) {
        Properties props = new Properties();

        if (wantGen != null) {
            props.setProperty(generalize(PROP_NAME), wantGen.toString());
        }

        if (wantSpec != null) {
            props.setProperty(specialize(PROP_NAME, SPEC), wantSpec.toString());
        }

        return FeatureEnabledChecker.isFeatureEnabled(props, SPEC, PROP_NAME);
    }

}
