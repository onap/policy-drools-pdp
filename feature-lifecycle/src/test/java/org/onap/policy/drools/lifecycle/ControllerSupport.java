/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019, 2021 AT&T Intellectual Property. All rights reserved.
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
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.lifecycle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller Test Support.
 */
@Getter
public class ControllerSupport {

    private static final Logger logger = LoggerFactory.getLogger(ControllerSupport.class);

    protected static final String JUNIT_KMODULE_DRL_PATH = "src/test/resources/lifecycle.drl";
    protected static final String JUNIT_KMODULE_POM_PATH = "src/test/resources/lifecycle.pom";
    protected static final String JUNIT_KMODULE_PATH = "src/test/resources/lifecycle.kmodule";
    protected static final String JUNIT_KJAR_DRL_PATH =
        "src/main/resources/kbLifecycle/org/onap/policy/drools/test/";

    protected static final String POLICY_TYPE_COMPLIANT_OP = "onap.policies.controlloop.operational.common.Drools";
    protected static final String POLICY_TYPE_VERSION = "1.0.0";

    protected static final String SESSION_NAME = "junits";

    private final String name;

    public ControllerSupport(@NonNull String name) {
        this.name = name;
    }

    /**
     * Create controller.
     */
    public void createController() throws IOException {
        try {
            PolicyController controller = getController();
            controller.getDrools().delete(ToscaPolicy.class);
        } catch (IllegalArgumentException e) {
            logger.debug("error when creating controller", e);
        }

        ReleaseId coordinates = installArtifact();

        Properties controllerProps = getControllerProps(coordinates);

        PolicyControllerConstants.getFactory().build(name, controllerProps);
    }

    private Properties getControllerProps(ReleaseId coordinates) {
        Properties controllerProps = new Properties();
        controllerProps.put(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, name);
        controllerProps.put(DroolsPropertyConstants.PROPERTY_CONTROLLER_POLICY_TYPES, getPolicyType());
        controllerProps.put(DroolsPropertyConstants.RULES_GROUPID, coordinates.getGroupId());
        controllerProps.put(DroolsPropertyConstants.RULES_ARTIFACTID, coordinates.getArtifactId());
        controllerProps.put(DroolsPropertyConstants.RULES_VERSION, coordinates.getVersion());
        return controllerProps;
    }

    /**
     * Install a maven artifact.
     */
    public static ReleaseId installArtifact(File kmodule, File pom,
                                            String drlKjarPath, List<File> drls) throws IOException {
        return KieUtils.installArtifact(kmodule, pom, drlKjarPath, drls);
    }

    /**
     * install artifact.
     */
    public ReleaseId installArtifact() throws IOException {
        return ControllerSupport.installArtifact(Paths.get(JUNIT_KMODULE_PATH).toFile(),
            Paths.get(JUNIT_KMODULE_POM_PATH).toFile(),
            JUNIT_KJAR_DRL_PATH,
            List.of(Paths.get(JUNIT_KMODULE_DRL_PATH).toFile()));
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
        return POLICY_TYPE_COMPLIANT_OP + ":" + POLICY_TYPE_VERSION;
    }

    /**
     * Get facts.
     */
    public <T> List<T> getFacts(Class<T> clazz) {
        return PolicyControllerConstants.getFactory().get(name)
            .getDrools()
            .facts(SESSION_NAME, clazz);
    }

    /**
     * Change final marker in static field.
     */
    public static <T> Field unsetFinalStaticAccess(Class<T> clazz, String fieldName)
        throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);

        return field;
    }

    /*
     * Reassign static field.
     */
    public static <T, E> void setStaticField(Class<T> clazz, String fieldName, E newValue)
        throws NoSuchFieldException, IllegalAccessException {
        unsetFinalStaticAccess(clazz, fieldName).set(null, newValue);
    }
}
