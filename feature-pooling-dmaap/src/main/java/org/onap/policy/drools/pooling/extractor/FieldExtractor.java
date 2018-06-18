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

package org.onap.policy.drools.pooling.extractor;

import java.lang.reflect.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to extract an object stored in one of the container's fields.
 */
public class FieldExtractor implements Extractor {

    private static final Logger logger = LoggerFactory.getLogger(FieldExtractor.class);

    /**
     * Field containing the object.
     */
    private final Field field;

    /**
     * 
     * @param field field containing the object
     */
    public FieldExtractor(Field field) {
        this.field = field;
    }

    @Override
    public Object extract(Object object) {
        try {
            return field.get(object);

        } catch (IllegalAccessException | IllegalArgumentException e) {
            logger.warn("cannot get {} from {}", field.getName(), object.getClass(), e);
            return null;
        }
    }
}
