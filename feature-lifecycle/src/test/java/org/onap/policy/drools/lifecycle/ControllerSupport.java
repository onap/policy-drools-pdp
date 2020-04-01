/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.lifecycle;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import lombok.Getter;
import lombok.NonNull;
import org.kie.api.builder.ReleaseId;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.util.KieUtils;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Controller Test Support.
 */
public class ControllerSupport {

    protected static final String JUNIT_KMODULE_DRL_PATH = "src/test/resources/lifecycle.drl";
    protected static final String JUNIT_KMODULE_POM_PATH = "src/test/resources/lifecycle.pom";
    protected static final String JUNIT_KMODULE_PATH = "src/test/resources/lifecycle.kmodule";
    protected static final String JUNIT_KJAR_DRL_PATH =
        "src/main/resources/kbLifecycle/org/onap/policy/drools/test/";

    protected static final String POLICY_TYPE_LEGACY_OP = "onap.policies.controlloop.Operational";
    protected static final String POLICY_TYPE_COMPLIANT_OP = "onap.policies.controlloop.operational.common.Drools";
    protected static final String POLICY_TYPE_VERSION = "1.0.0";

    protected static final String SESSION_NAME = "junits";

    @Getter
    private final String name;

    public ControllerSupport(@NonNull String name) {
        this.name = name;
    }

    /**
     * Create controller.
     */
    public PolicyController createController() throws IOException {
        try {
            PolicyController controller = getController();
            controller.getDrools().delete(ToscaPolicy.class);
            return controller;
        } catch (IllegalArgumentException e) {
            ;
        }

        ReleaseId coordinates = installArtifact();

        Properties controllerProps = new Properties();
        controllerProps.put(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, name);
        controllerProps.put(DroolsPropertyConstants.PROPERTY_CONTROLLER_POLICY_TYPES, getPolicyType());
        controllerProps.put(DroolsPropertyConstants.RULES_GROUPID, coordinates.getGroupId());
        controllerProps.put(DroolsPropertyConstants.RULES_ARTIFACTID, coordinates.getArtifactId());
        controllerProps.put(DroolsPropertyConstants.RULES_VERSION, coordinates.getVersion());

        return PolicyControllerConstants.getFactory().build(name, controllerProps);
    }

    /**
     * install artifact.
     */
    public ReleaseId installArtifact() throws IOException {
        return
            KieUtils.installArtifact(Paths.get(JUNIT_KMODULE_PATH).toFile(),
                Paths.get(JUNIT_KMODULE_POM_PATH).toFile(),
                        JUNIT_KJAR_DRL_PATH,
                        Paths.get(JUNIT_KMODULE_DRL_PATH).toFile());
    }

    /**
     * Destroy the echo controller.
     */
    public void destroyController() {
        PolicyControllerConstants.getFactory().destroy(name);
    }

    /**
     * Get controller.
     */
    public PolicyController getController() {
        return PolicyControllerConstants.getFactory().get(name);
    }

    /**
     * Get Policy Type.
     */
    public static String getPolicyType() {
        return POLICY_TYPE_LEGACY_OP + ":" + POLICY_TYPE_VERSION + ","
                       + POLICY_TYPE_COMPLIANT_OP + ":" + POLICY_TYPE_VERSION;
    }

    /**
     * Get facts.
     */
    public <T> List<T> getFacts(Class<T> clazz) {
        return PolicyControllerConstants.getFactory().get(name)
            .getDrools()
            .facts(SESSION_NAME, clazz);
    }
}
