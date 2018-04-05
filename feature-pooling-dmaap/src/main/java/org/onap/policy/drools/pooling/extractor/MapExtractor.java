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

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to extract an object stored in a map.
 */
public class MapExtractor implements Extractor {

    private static final Logger logger = LoggerFactory.getLogger(MapExtractor.class);

    /**
     * Key to the item to extract from the map.
     */
    private final String key;

    /**
     * 
     * @param key key to the item to extract from the map
     */
    public MapExtractor(String key) {
        this.key = key;
    }

    @Override
    public Object extract(Object object) {
        
        if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;

            return map.get(key);

        } else {
            logger.warn("expecting a map, instead of {}", object.getClass());
            return null;
        }
    }
}
