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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.util.HashMap;
import java.util.Map;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

/**
 * Serialization helper functions.
 */
public class Serializer {

    /**
     * The message type is stored in fields of this name within the JSON.
     */
    private static final String TYPE_FIELD = "type";

    /**
     * Used to encode & decode JSON messages sent & received, respectively, on the
     * internal DMaaP topic.
     */
    private final Gson gson = new Gson();

    /**
     * Maps a message subclass to its type.
     */
    private static final Map<Class<? extends Message>, String> class2type = new HashMap<>();

    /**
     * Maps a message type to the appropriate subclass.
     */
    private static final Map<String, Class<? extends Message>> type2class = new HashMap<>();

    static {
        class2type.put(Heartbeat.class, "heartbeat");
        class2type.put(Identification.class, "identification");
        class2type.put(Leader.class, "leader");
        class2type.put(Offline.class, "offline");
        class2type.put(Query.class, "query");

        class2type.forEach((clazz, type) -> type2class.put(type, clazz));
    }

    /**
     * Constructor.
     */
    public Serializer() {
        super();
    }

    /**
     * Encodes a filter.
     *
     * @param filter filter to be encoded
     * @return the filter, serialized as a JSON string
     */
    public String encodeFilter(Map<String, Object> filter) {
        return gson.toJson(filter);
    }

    /**
     * Encodes a message.
     *
     * @param msg message to be encoded
     * @return the message, serialized as a JSON string
     */
    public String encodeMsg(Message msg) {
        JsonElement jsonEl = gson.toJsonTree(msg);

        String type = class2type.get(msg.getClass());
        if (type == null) {
            throw new JsonParseException("cannot serialize " + msg.getClass());
        }

        jsonEl.getAsJsonObject().addProperty(TYPE_FIELD, type);

        return gson.toJson(jsonEl);
    }

    /**
     * Decodes a JSON string into a Message.
     *
     * @param msg JSON string representing the message
     * @return the message
     */
    public Message decodeMsg(String msg) {
        JsonElement jsonEl = gson.fromJson(msg, JsonElement.class);

        JsonElement typeEl = jsonEl.getAsJsonObject().get(TYPE_FIELD);
        if (typeEl == null) {
            throw new JsonParseException("cannot deserialize " + Message.class
                            + " because it does not contain a field named " + TYPE_FIELD);

        }

        Class<? extends Message> clazz = type2class.get(typeEl.getAsString());
        if (clazz == null) {
            throw new JsonParseException("cannot deserialize " + typeEl);
        }

        return gson.fromJson(jsonEl, clazz);
    }
}
