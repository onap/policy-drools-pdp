/*-
 * ============LICENSE_START=======================================================
 * feature-session-persistence
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

package org.onap.policy.drools.persistence;

import java.util.HashMap;
import java.util.Map;
import javax.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates the schema DDL files. */
public class GenSchema {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceFeatureTest.class);

    /**
     * Opens the EMF, which generates the schema, as a side-effect.
     *
     * @throws Exception exception
     */
    private GenSchema() throws Exception {
        Map<String, Object> propMap = new HashMap<>();

        propMap.put("javax.persistence.jdbc.driver", "org.h2.Driver");
        propMap.put("javax.persistence.jdbc.url", "jdbc:h2:mem:JpaDroolsSessionConnectorTest");

        Persistence.createEntityManagerFactory("schemaDroolsPU", propMap).close();
    }

    /**
     * This is is provided as a utility for producing a basic ddl schema file in the sql directory.
     */
    public static void main(String[] args) {
        try {
            new GenSchema();

        } catch (Exception e) {
            logger.error("failed to generate schema", e);
        }
    }
}
