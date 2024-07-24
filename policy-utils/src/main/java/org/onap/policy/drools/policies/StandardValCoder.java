/*--
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import lombok.NonNull;
import lombok.ToString;
import net.jimblackler.jsonschemafriend.GenerationException;
import net.jimblackler.jsonschemafriend.Schema;
import net.jimblackler.jsonschemafriend.SchemaStore;
import net.jimblackler.jsonschemafriend.ValidationException;
import net.jimblackler.jsonschemafriend.Validator;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.drools.exception.CoderRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension to the StandardCoder to support streaming validation against a Draft-07 Json schema specification.
 * Extending specifically for drools policies - moved from common to here.
 */

@ToString
public class StandardValCoder extends StandardCoder {

    private static final Logger logger = LoggerFactory.getLogger(StandardValCoder.class);

    private final Schema schema;

    /**
     * StandardCoder with validation.
     */
    public StandardValCoder(@NonNull String jsonSchema) {
        try {
            SchemaStore store = new SchemaStore();
            this.schema = store.loadSchemaJson(jsonSchema);
        } catch (GenerationException e) {
            throw new CoderRuntimeException(e);
        }
    }

    @Override
    protected String toPrettyJson(Object object) {
        try {
            validate(gsonPretty.toJson(object));
        } catch (CoderException e) {
            throw new CoderRuntimeException(e);
        }
        return super.toPrettyJson(object);
    }

    @Override
    protected String toJson(@NonNull Object object) {
        var output = new StringWriter();
        toJson(output, object);
        return output.toString();
    }

    @Override
    protected void toJson(@NonNull Writer target, @NonNull Object object) {
        try {
            validate(gson.toJson(object));
        } catch (CoderException e) {
            throw new CoderRuntimeException(e);
        }
        gson.toJson(object, object.getClass(), target);
    }

    @Override
    protected <T> T fromJson(@NonNull Reader source, @NonNull Class<T> clazz) {
        return convertFromDouble(clazz, gson.fromJson(source, clazz));
    }

    @Override
    protected <T> T fromJson(String json, Class<T> clazz) {
        try {
            validate(json);
        } catch (CoderException e) {
            throw new CoderRuntimeException(e);
        }
        var reader = new StringReader(json);
        return convertFromDouble(clazz, gson.fromJson(reader, clazz));
    }

    /**
     * Is the json conformant?.
     */
    public boolean isConformant(@NonNull String json) {
        try {
            conformance(json);
            return true;
        } catch (Exception e) {
            logger.error("JSON is not conformant to schema", e);
            return false;
        }
    }

    /**
     * Check a json string for conformance against its schema definition.
     */
    public void conformance(@NonNull String json) throws CoderException {
        validate(json);
    }

    private void validate(Object object) throws CoderException {
        try {
            final var validator = new Validator();
            validator.validate(schema, object);
        } catch (ValidationException exception) {
            var error = String.format("JSON validation failed: %s", exception.getMessage());
            logger.error(error);
            throw new CoderException(error);
        }
    }

    private void validate(String json) throws CoderException {
        validate(gson.fromJson(json, Object.class));
    }
}
