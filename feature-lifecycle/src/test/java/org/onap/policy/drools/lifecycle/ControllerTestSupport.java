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
import java.util.Properties;
import org.kie.api.builder.ReleaseId;
import org.onap.policy.drools.properties.DroolsProperties;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.util.KieUtils;

/**
 * Controller Test Support.
 */
public class ControllerTestSupport {

    protected static final String JUNIT_ECHO_KMODULE_DRL_PATH = "src/test/resources/echo.drl";
    protected static final String JUNIT_ECHO_KMODULE_POM_PATH = "src/test/resources/echo.pom";
    protected static final String JUNIT_ECHO_KMODULE_PATH = "src/test/resources/echo.kmodule";
    protected static final String JUNIT_ECHO_KJAR_DRL_PATH =
        "src/main/resources/kbEcho/org/onap/policy/drools/test/echo.drl";

    /**
     * Create echo controller.
     */
    public static PolicyController createEchoController() throws IOException {
        ReleaseId coordinates =
            KieUtils.installArtifact(Paths.get(JUNIT_ECHO_KMODULE_PATH).toFile(),
                Paths.get(JUNIT_ECHO_KMODULE_POM_PATH).toFile(),
                JUNIT_ECHO_KJAR_DRL_PATH,
                Paths.get(JUNIT_ECHO_KMODULE_DRL_PATH).toFile());


        Properties controllerProps = new Properties();
        controllerProps.put(DroolsProperties.PROPERTY_CONTROLLER_NAME, "echo");
        controllerProps.put(DroolsProperties.RULES_GROUPID, coordinates.getGroupId());
        controllerProps.put(DroolsProperties.RULES_ARTIFACTID, coordinates.getArtifactId());
        controllerProps.put(DroolsProperties.RULES_VERSION, coordinates.getVersion());

        return PolicyController.factory.build("echo", controllerProps);
    }

    public static void destroyEchoController(PolicyController controller) {
        PolicyController.factory.destroy(controller);
    }

}
