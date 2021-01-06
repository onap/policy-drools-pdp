/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
 *  Modifications Copyright (C) 2021 Nordix Foundation.
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

import com.worldturner.medeia.api.ValidationFailedException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.coder.StandardValCoder;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts Tosca Policies into Domain policies.
 *
 * <p>
 * A Domain Policy is a specialized version of the Tosca Policy that
 * conform with a particular domain specification in json schema format.
 * A ToscaPolicy is a generic data structure where domain data is contained
 * in a Map[String, Object]. This class contains that generic information
 * into a concrete domain specific data model for the ToscaPolicy.
 * </p>
 */

@NoArgsConstructor
public class DomainMaker {

    private static final Logger logger = LoggerFactory.getLogger(DomainMaker.class);

    /**
     * policy-type -> schema validator map.
     */
    private final Map<ToscaConceptIdentifier, StandardValCoder> validators = new ConcurrentHashMap<>();

    /**
     * non-validation serialization coder.
     */
    private final StandardCoder nonValCoder = new StandardCoder();

    /**
     * Does this json conform to a registered policy type schema?.
     */
    public boolean isConformant(@NonNull ToscaConceptIdentifier policyType, @NonNull String json) {
        if (!isRegistered(policyType)) {
            return false;
        }

        return validators.get(policyType).isConformant(json);
    }

    /**
     * Does this policy conform to its domain specification?.
     */
    public boolean isConformant(@NonNull ToscaPolicy policy) {
        String rawPolicy = serialize(policy);
        if (StringUtils.isBlank(rawPolicy)) {
            return false;
        }

        return isConformant(policy.getTypeIdentifier(), rawPolicy);
    }

    /**
     * Does this domain policy conforms to its schema definition?.
     */
    public <T> boolean isDomainConformant(@NonNull ToscaConceptIdentifier policyType, @NonNull T domainPolicy) {
        if (!isRegistered(policyType)) {
            return false;
        }

        try {
            return validators.get(policyType).encode(domainPolicy) != null;
        } catch (CoderException e) {
            logger.info("policy {}:{} is not conformant", policyType, domainPolicy.getClass().getName(), e);
            return false;
        }
    }

    /**
     * Check policy conformance to its specification providing a list of errors
     * in a ValidationFailedException.
     */
    public boolean conformance(@NonNull ToscaPolicy policy) {
        if (!isRegistered(policy.getTypeIdentifier())) {
            return false;
        }

        String rawPolicy = serialize(policy);
        if (StringUtils.isBlank(rawPolicy)) {
            return false;
        }

        try {
            validators.get(policy.getTypeIdentifier()).conformance(rawPolicy);
        } catch (CoderException e) {
            logger.info("policy {}:{}:{} is not conformant",
                    policy.getTypeIdentifier(), policy.getName(), policy.getVersion(), e);
            if (e.getCause() instanceof ValidationFailedException) {
                throw (ValidationFailedException) e.getCause();
            }
            return false;
        }

        return true;
    }

    /**
     * Checks a domain policy conformance to its specification providing a list of errors
     * in a ValidationFailedException.
     */
    public <T> boolean conformance(@NonNull ToscaConceptIdentifier policyType, T domainPolicy) {

        if (!isRegistered(policyType)) {
            return false;
        }

        try {
            validators.get(policyType).encode(domainPolicy);
        } catch (CoderException e) {
            logger.info("policy {}:{} is not conformant", policyType, domainPolicy.getClass().getName(), e);
            if (e.getCause() instanceof ValidationFailedException) {
                throw (ValidationFailedException) e.getCause();
            }
            return false;
        }

        return true;
    }

    /**
     * Registers a known schema resource for validation.
     */
    public boolean registerValidator(@NonNull ToscaConceptIdentifier policyType) {
        //
        // A known schema is one that embedded in a .jar in the classpath as a resource
        // matching the following syntax: <policy-type-name>-<policy-type-version>.schema.json.
        //
        String schema =
                ResourceUtils
                        .getResourceAsString("schemas/"
                            + policyType.getName() + "-" + policyType.getVersion() + ".schema.json");
        if (schema == null) {
            return false;
        }

        return registerValidator(policyType, schema);
    }

    /**
     * Registers/Overrides a new/known schema for a policy type.
     */
    public boolean registerValidator(@NonNull ToscaConceptIdentifier policyType, @NonNull String schema) {
        try {
            validators.put(policyType, new StandardValCoder(schema, policyType.toString()));
        } catch (RuntimeException r) {
            logger.info("schema for {} is not valid", policyType, r);
            return false;
        }
        return true;
    }

    /**
     * Converts a ToscaPolicy into a Domain Policy.
     */
    public <T> T convertTo(@NonNull ToscaPolicy toscaPolicy, @NonNull Class<T> clazz) throws CoderException {
        return convertTo(toscaPolicy.getTypeIdentifier(), nonValCoder.encode(toscaPolicy), clazz);
    }

    /**
     * Converts a JSON policy into a Domain Policy.
     */
    public <T> T convertTo(@NonNull ToscaConceptIdentifier policyType, @NonNull String json, @NonNull Class<T> clazz)
            throws CoderException {
        if (isRegistered(policyType)) {
            return validators.get(policyType).decode(json, clazz);
        } else {
            return nonValCoder.decode(json, clazz);
        }
    }

    /**
     * Converts a Tosca Policy Type specification to a domain-specific json specification.
     */
    public String convertToSchema(@NonNull ToscaPolicyType policyType) {
        //
        // TODO:
        // 1. Convert Tosca Policy Type definition schema to suitable json schema.
        // 2. Call registerValidator to register
        throw new UnsupportedOperationException("schema generation from policy type is not supported");
    }

    public boolean isRegistered(@NonNull ToscaConceptIdentifier policyType) {
        return validators.containsKey(policyType) || registerValidator(policyType);
    }


    private String serialize(@NonNull ToscaPolicy policy) {
        String rawPolicy = null;
        try {
            rawPolicy = nonValCoder.encode(policy);
        } catch (CoderException e) {
            logger.debug("policy {}:{} is invalid json", policy.getTypeIdentifier(), policy.getIdentifier(), e);
        }
        return rawPolicy;
    }
}
