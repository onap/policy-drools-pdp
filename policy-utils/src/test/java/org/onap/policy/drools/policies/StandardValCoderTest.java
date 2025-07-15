/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
 *  Modifications Copyright (C) 2024-2025 OpenInfra Foundation Europe. All rights reserved.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.drools.exception.CoderRuntimeException;

class StandardValCoderTest {
    private String jsonSchema;
    private String validJson;
    private String missingReqJson;
    private String badRegexJson;

    @Data
    @NoArgsConstructor
    public static class ValOuter {
        @Data
        @NoArgsConstructor
        public static class ValInner {
            public String subItemString;
            public Integer subItemInteger;
        }

        public String aaString;
        public int anInteger;
        public boolean aaBoolean;
        public List<ValInner> aaCollection;
    }

    @BeforeEach
    void testSetUp() {
        jsonSchema = getJson("src/test/resources/coder/test.schema.json");
        validJson = getJson("src/test/resources/coder/valid.json");
        missingReqJson = getJson("src/test/resources/coder/missing-required.json");
        badRegexJson = getJson("src/test/resources/coder/bad-regex.json");
    }

    @Test
    void testDecode() throws CoderException {
        StandardValCoder valCoder = new StandardValCoder(jsonSchema);

        ValOuter valOuter = valCoder.decode(validJson, ValOuter.class);
        assertValidJson(valOuter);

        StringReader reader = new StringReader(validJson);
        valOuter = valCoder.decode(reader, ValOuter.class);
        assertValidJson(valOuter);

        try {
            valCoder.decode(missingReqJson, ValOuter.class);
            fail("missing required field should have been flagged by the schema validation");
        } catch (CoderException e) {
            assertThat(e.getMessage()).contains("Missing property aaCollection");
        }

        try {
            valCoder.decode(badRegexJson, ValOuter.class);
            fail("bad regex should have been flagged by the schema validation");
        } catch (CoderException e) {
            assertThat(e.getMessage())
                .contains("Validation errors: \"abc123\" at #/aaString failed")
                .contains("Did not match pattern: ^([a-z]*)$");
        }
    }

    @Test
    void testEncode() throws CoderException {
        StandardValCoder valCoder = new StandardValCoder(jsonSchema);
        ValOuter valOuter = valCoder.decode(validJson, ValOuter.class);

        String valOuterJson = valCoder.encode(valOuter);
        assertEquals(valOuter, valCoder.decode(valOuterJson, ValOuter.class));
        assertValidJson(valOuter);

        StringWriter writer = new StringWriter();
        valCoder.encode(writer, valOuter);
        assertEquals(valOuterJson, writer.toString());

        // test exception case with an empty object
        assertThatThrownBy(() -> valCoder.encode(new ValOuter())).isInstanceOf(CoderException.class);
    }

    @Test
    void testPretty() throws CoderException {
        StandardValCoder valCoder = new StandardValCoder(jsonSchema);
        ValOuter valOuter = valCoder.decode(validJson, ValOuter.class);

        String valOuterJson = valCoder.encode(valOuter);
        assertEquals(valOuterJson, valCoder.encode(valOuter, false));
        String prettyValOuterJson = valCoder.encode(valOuter, true);
        assertNotEquals(valOuterJson, prettyValOuterJson);

        assertEquals(valOuter, valCoder.decode(prettyValOuterJson, ValOuter.class));

        // test exception cases with an empty object
        assertThatThrownBy(() -> valCoder.encode(new ValOuter(), false)).isInstanceOf(CoderException.class);
        assertThatThrownBy(() -> valCoder.encode(new ValOuter(), true)).isInstanceOf(CoderException.class);
    }

    @Test
    void testConformance() {
        StandardValCoder valCoder = new StandardValCoder(jsonSchema);
        assertTrue(valCoder.isConformant(validJson));
        assertFalse(valCoder.isConformant(missingReqJson));
        assertFalse(valCoder.isConformant(badRegexJson));
    }

    @Test
    void testNullValues() throws CoderException {
        StandardValCoder valCoder = new StandardValCoder(jsonSchema);
        assertThrows(NullPointerException.class, () -> valCoder.toJson(null));
        assertThrows(NullPointerException.class, () -> valCoder.isConformant(null));
        assertThrows(NullPointerException.class, () -> valCoder.conformance(null));

        assertThrows(NullPointerException.class, () -> valCoder.toJson(null, null));
        var writer = new StringWriter();
        assertThrows(NullPointerException.class, () -> valCoder.toJson(writer, null));
        ValOuter valOuter = valCoder.decode(validJson, ValOuter.class);
        assertThrows(NullPointerException.class, () -> valCoder.toJson(null, valOuter));
    }

    @Test
    void testConstructor() {
        assertThrows(NullPointerException.class, () -> new StandardValCoder(null));

        assertThrows(CoderRuntimeException.class, () -> new StandardValCoder("$schema"));
    }

    private void assertValidJson(ValOuter valOuter) {
        assertEquals("abcd", valOuter.getAaString());
        assertEquals(90, valOuter.getAnInteger());
        assertTrue(valOuter.isAaBoolean());
        assertEquals("defg", valOuter.getAaCollection().get(0).getSubItemString());
        assertEquals(Integer.valueOf(1200), valOuter.getAaCollection().get(0).getSubItemInteger());
    }

    private String getJson(String filePath) {
        return ResourceUtils.getResourceAsString(filePath);
    }
}