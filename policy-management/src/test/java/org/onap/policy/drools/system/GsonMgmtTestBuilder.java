/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.system;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.utils.gson.GsonSerializer;
import org.onap.policy.common.utils.gson.GsonTestUtilsBuilder;
import org.onap.policy.drools.controller.DroolsController;

/**
 * Utilities used to test encoding and decoding of Policy objects.
 */
public class GsonMgmtTestBuilder extends GsonTestUtilsBuilder {

    /**
     * Adds support for serializing a topic source mock.
     *
     * @return the builder
     */
    public GsonMgmtTestBuilder addTopicSourceMock() {
        TypeAdapterFactory sgson = new TypeAdapterFactory() {
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                Class<? super T> clazz = type.getRawType();

                if (TopicSource.class.isAssignableFrom(clazz)) {
                    return new GsonSerializer<T>() {
                        @Override
                        public void write(JsonWriter out, T value) throws IOException {
                            TopicSource obj = (TopicSource) value;
                            out.beginObject().name("name").value(obj.getTopic()).endObject();
                        }
                    };
                }

                return null;
            }
        };

        addMock(TopicSource.class, sgson);

        return this;
    }

    /**
     * Adds support for serializing a topic sink mock.
     *
     * @return the builder
     */
    public GsonMgmtTestBuilder addTopicSinkMock() {
        TypeAdapterFactory sgson = new TypeAdapterFactory() {
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                Class<? super T> clazz = type.getRawType();

                if (TopicSink.class.isAssignableFrom(clazz)) {
                    return new GsonSerializer<T>() {
                        @Override
                        public void write(JsonWriter out, T value) throws IOException {
                            TopicSink obj = (TopicSink) value;
                            out.beginObject().name("name").value(obj.getTopic()).endObject();
                        }
                    };
                }

                return null;
            }
        };

        addMock(TopicSink.class, sgson);

        return this;
    }

    /**
     * Adds support for serializing a drools controller mock.
     *
     * @return the builder
     */
    public GsonMgmtTestBuilder addDroolsControllerMock() {
        TypeAdapterFactory sgson = new TypeAdapterFactory() {
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                Class<? super T> clazz = type.getRawType();

                if (DroolsController.class.isAssignableFrom(clazz)) {
                    return new GsonSerializer<T>() {
                        @Override
                        public void write(JsonWriter out, T value) throws IOException {
                            DroolsController obj = (DroolsController) value;
                            out.beginObject().name("group").value(obj.getGroupId()).name("artifact")
                                            .value(obj.getArtifactId()).name("version").value(obj.getVersion())
                                            .endObject();
                        }
                    };
                }

                return null;
            }
        };

        addMock(DroolsController.class, sgson);

        return this;
    }

    /**
     * Adds support for serializing an http servlet mock.
     *
     * @return the builder
     */
    public GsonMgmtTestBuilder addHttpServletServerMock() {
        TypeAdapterFactory sgson = new TypeAdapterFactory() {
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                Class<? super T> clazz = type.getRawType();

                if (HttpServletServer.class.isAssignableFrom(clazz)) {
                    return new GsonSerializer<T>() {
                        @Override
                        public void write(JsonWriter out, T value) throws IOException {
                            HttpServletServer obj = (HttpServletServer) value;
                            out.beginObject().name("port").value(obj.getPort()).endObject();
                        }
                    };
                }

                return null;
            }
        };

        addMock(HttpServletServer.class, sgson);

        return this;
    }
}
