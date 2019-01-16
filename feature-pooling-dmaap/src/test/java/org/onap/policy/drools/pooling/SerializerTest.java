/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeAnd;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeEquals;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeOr;

import com.google.gson.JsonParseException;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Query;

public class SerializerTest {

    @Test
    public void testSerializer() {
        new Serializer();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEncodeFilter() throws Exception {
        final Serializer ser = new Serializer();

        /*
         * Ensure raw maps serialize as expected. Use a TreeMap so the field
         * order is predictable.
         */
        Map<String, Object> top = new TreeMap<>();
        Map<String, Object> inner = new TreeMap<>();
        top.put("abc", 20);
        top.put("def", inner);
        top.put("ghi", true);
        inner.put("xyz", 30);
        assertEquals("{'abc':20,'def':{'xyz':30},'ghi':true}".replace('\'', '"'), ser.encodeFilter(top));

        /*
         * Ensure we can encode a complicated filter without throwing an
         * exception
         */
        Map<String, Object> complexFilter = makeAnd(makeEquals("fieldC", "valueC"),
                        makeOr(makeEquals("fieldA", "valueA"), makeEquals("fieldB", "valueB")));
        String val = ser.encodeFilter(complexFilter);
        assertFalse(val.isEmpty());
    }

    @Test
    public void testEncodeMsg_testDecodeMsg() throws Exception {
        Serializer ser = new Serializer();

        Query msg = new Query("hostA");
        msg.setChannel("channelB");

        String encoded = ser.encodeMsg(msg);
        assertNotNull(encoded);

        Message decoded = ser.decodeMsg(encoded);
        assertEquals(Query.class, decoded.getClass());

        assertEquals(msg.getSource(), decoded.getSource());
        assertEquals(msg.getChannel(), decoded.getChannel());

        // should work a second time, too
        encoded = ser.encodeMsg(msg);
        assertNotNull(encoded);

        decoded = ser.decodeMsg(encoded);
        assertEquals(Query.class, decoded.getClass());

        assertEquals(msg.getSource(), decoded.getSource());
        assertEquals(msg.getChannel(), decoded.getChannel());

        // invalid subclass when encoding
        assertThatThrownBy(() -> ser.encodeMsg(new Message() {})).isInstanceOf(JsonParseException.class)
                        .hasMessageContaining("cannot serialize");

        // missing type when decoding
        final String enc2 = encoded.replaceAll("type", "other-field-name");

        assertThatThrownBy(() -> ser.decodeMsg(enc2)).isInstanceOf(JsonParseException.class)
                        .hasMessageContaining("does not contain a field named");

        // invalid type
        final String enc3 = encoded.replaceAll("query", "invalid-type");

        assertThatThrownBy(() -> ser.decodeMsg(enc3)).isInstanceOf(JsonParseException.class)
                        .hasMessage("cannot deserialize \"invalid-type\"");
    }

}
