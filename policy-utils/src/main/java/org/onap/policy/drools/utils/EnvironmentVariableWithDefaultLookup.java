/*
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.utils;

import com.google.common.base.Splitter;
import java.util.List;
import org.apache.commons.configuration2.interpol.Lookup;
import org.apache.commons.lang3.StringUtils;

/**
 * Environment Variable with a default value.   The syntax is
 * ${envd:variable-name:default-value}.
 */
public class EnvironmentVariableWithDefaultLookup implements Lookup {

    @Override
    public String lookup(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }

        List<String> envWithDefault =
            Splitter.on(":").trimResults().omitEmptyStrings().splitToList(key);
        if (envWithDefault.isEmpty()) {
            return StringUtils.EMPTY;
        }

        String envVar = System.getenv(envWithDefault.get(0));
        if (StringUtils.isNotEmpty(envVar)) {
            return envVar;
        }

        if (envWithDefault.size() > 1) {
            return String.join(":", envWithDefault.subList(1, envWithDefault.size()));
        }

        return StringUtils.EMPTY;
    }
}
