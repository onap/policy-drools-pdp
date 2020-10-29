/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonParseException;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Query;

public class SerializerTest {

    @Test
    public void testSerializer() {
        assertThatCode(() -> new Serializer()).doesNotThrowAnyException();
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
        Message msg2 = new Message() {};
        assertThatThrownBy(() -> ser.encodeMsg(msg2)).isInstanceOf(JsonParseException.class)
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
