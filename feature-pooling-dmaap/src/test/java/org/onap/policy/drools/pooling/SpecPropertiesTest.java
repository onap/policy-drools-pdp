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

package org.onap.policy.drools.pooling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;

public class SpecPropertiesTest {

    /**
     * Property prefix of interest.
     */
    private static final String MY_PREFIX = "my.prefix";

    /**
     * Specialization, which follows the prefix.
     */
    private static final String MY_SPEC = "my.spec";

    /**
     * Generalized prefix (i.e., without the spec).
     */
    private static final String PREFIX_GEN = MY_PREFIX + ".";

    /**
     * Specialized prefix (i.e., with the spec).
     */
    private static final String PREFIX_SPEC = PREFIX_GEN + MY_SPEC + ".";

    /**
     * Suffix to add to property names to generate names of properties that are not
     * populated.
     */
    private static final String SUFFIX = ".suffix";

    /**
     * Property name without a prefix.
     */
    private static final String PROP_NO_PREFIX = "other";

    /**
     * Generalized property name (i.e., without the spec).
     */
    private static final String PROP_GEN = PREFIX_GEN + "generalized";

    // property names that include the spec
    private static final String PROP_SPEC = PREFIX_SPEC + "specialized";
    private static final String PROP_UNKNOWN = PREFIX_SPEC + "unknown";

    // property values
    private static final String VAL_NO_PREFIX = "no-prefix";
    private static final String VAL_GEN = "gen";
    private static final String VAL_SPEC = "spec";

    private static final String VAL_DEFAULT = "default value";

    private Properties supportingProps;
    private SpecProperties props;

    @Before
    public void setUp() {
        supportingProps = new Properties();

        supportingProps.setProperty(PROP_NO_PREFIX, VAL_NO_PREFIX);
        supportingProps.setProperty(PROP_GEN, VAL_GEN);
        supportingProps.setProperty(PROP_SPEC, VAL_SPEC);

        props = new SpecProperties(MY_PREFIX, MY_SPEC);

        props.putAll(supportingProps);
    }

    @Test
    public void testSpecPropertiesStringString() {

        // no supporting properties
        props = new SpecProperties(MY_PREFIX, MY_SPEC);

        assertEquals(PREFIX_GEN, props.getPrefix());
        assertEquals(PREFIX_SPEC, props.getSpecPrefix());

        // everything is null
        assertNull(props.getProperty(gen(PROP_NO_PREFIX)));
        assertNull(props.getProperty(gen(PROP_GEN)));
        assertNull(props.getProperty(gen(PROP_SPEC)));
        assertNull(props.getProperty(gen(PROP_UNKNOWN)));
    }

    @Test
    public void testSpecPropertiesStringStringProperties() {

        // use supportingProps as default properties
        props = new SpecProperties(MY_PREFIX, MY_SPEC, supportingProps);

        assertEquals(PREFIX_GEN, props.getPrefix());
        assertEquals(PREFIX_SPEC, props.getSpecPrefix());

        assertEquals(VAL_NO_PREFIX, props.getProperty(gen(PROP_NO_PREFIX)));
        assertEquals(VAL_GEN, props.getProperty(gen(PROP_GEN)));
        assertEquals(VAL_SPEC, props.getProperty(gen(PROP_SPEC)));
        assertNull(props.getProperty(gen(PROP_UNKNOWN)));
    }

    @Test
    public void testWithTrailingDot() {
        // neither has trailing dot
        assertEquals(PREFIX_GEN, props.getPrefix());
        assertEquals(PREFIX_SPEC, props.getSpecPrefix());

        // both have trailing dot
        props = new SpecProperties(PREFIX_GEN, MY_SPEC + ".");
        assertEquals(PREFIX_GEN, props.getPrefix());
        assertEquals(PREFIX_SPEC, props.getSpecPrefix());
    }

    @Test
    public void testGetPropertyString() {
        // the key does contain the prefix
        assertEquals(VAL_NO_PREFIX, props.getProperty(gen(PROP_NO_PREFIX)));
        assertNull(props.getProperty(gen(PROP_NO_PREFIX + SUFFIX)));

        // specialized value exists
        assertEquals(VAL_GEN, props.getProperty(gen(PROP_GEN)));
        assertNull(props.getProperty(gen(PROP_GEN + SUFFIX)));

        // generalized value exists
        assertEquals(VAL_SPEC, props.getProperty(gen(PROP_SPEC)));
        assertNull(props.getProperty(gen(PROP_SPEC + SUFFIX)));

        // not found
        assertNull(props.getProperty(gen(PROP_UNKNOWN)));
        assertNull(props.getProperty(gen(PROP_UNKNOWN + SUFFIX)));
    }

    @Test
    public void testGetPropertyStringString() {
        // the key does contain the prefix
        assertEquals(VAL_NO_PREFIX, props.getProperty(gen(PROP_NO_PREFIX), VAL_DEFAULT));
        assertEquals(VAL_DEFAULT, props.getProperty(gen(PROP_NO_PREFIX + SUFFIX), VAL_DEFAULT));

        // specialized value exists
        assertEquals(VAL_GEN, props.getProperty(gen(PROP_GEN), VAL_DEFAULT));
        assertEquals(VAL_DEFAULT, props.getProperty(gen(PROP_GEN + SUFFIX), VAL_DEFAULT));

        // generalized value exists
        assertEquals(VAL_SPEC, props.getProperty(gen(PROP_SPEC), VAL_DEFAULT));
        assertEquals(VAL_DEFAULT, props.getProperty(gen(PROP_SPEC + SUFFIX), VAL_DEFAULT));

        // not found
        assertEquals(VAL_DEFAULT, props.getProperty(gen(PROP_UNKNOWN), VAL_DEFAULT));
        assertEquals(VAL_DEFAULT, props.getProperty(gen(PROP_UNKNOWN + SUFFIX), VAL_DEFAULT));

        // can return null
        assertNull(props.getProperty(gen(PROP_UNKNOWN), null));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testHashCode() {
        props.hashCode();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testEquals() {
        props.equals(props);
    }

    private String gen(String propnm) {
        if (propnm.startsWith(PREFIX_SPEC)) {
            return PREFIX_GEN + propnm.substring(PREFIX_SPEC.length());
        }

        return propnm;
    }

}
