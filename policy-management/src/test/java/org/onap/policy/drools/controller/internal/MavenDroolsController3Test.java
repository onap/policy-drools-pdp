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

package org.onap.policy.drools.controller.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.ReleaseId;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.util.KieUtils;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenDroolsController3Test {
    private static final String DROOLS_RESOURCES_DIR = "src/test/resources/";
    private static final String DROOLS_KJAR_RESOURCES_DIR = "src/main/resources/";
    private static final String DRL_EXT = ".drl";
    private static final String POM_EXT = ".pom";
    private static final String KMODULE_EXT = ".kmodule";

    private static final String RULES_BASE = "rules";
    private static final String KBNAME_RULES = "kbRules";
    private static final String KBSESSION_RULES = RULES_BASE;
    private static final String KBPACKAGE_RULES = RULES_BASE;

    public static volatile CountDownLatch running1a = new CountDownLatch(1);
    public static volatile CountDownLatch running1b = new CountDownLatch(1);
    public static volatile CountDownLatch running2a = new CountDownLatch(1);
    public static volatile CountDownLatch running2b = new CountDownLatch(1);

    private static final Logger logger = LoggerFactory.getLogger(MavenDroolsController3Test.class);

    private static ReleaseId install(String name, List<File> drls) throws IOException {
        return
            KieUtils.installArtifact(
                Paths.get(DROOLS_RESOURCES_DIR + RULES_BASE + KMODULE_EXT).toFile(),
                Paths.get(DROOLS_RESOURCES_DIR + name + POM_EXT).toFile(),
               DROOLS_KJAR_RESOURCES_DIR + KBNAME_RULES + "/" + KBPACKAGE_RULES + "/",
                drls);
    }

    public static ReleaseId rulesDescriptor1;
    public static ReleaseId rulesDescriptor2;

    /**
     * Test Class Initialization.
     */
    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        rulesDescriptor1 =
            install("rules1",
                Stream.of(Paths.get(DROOLS_RESOURCES_DIR + "rules1" + DRL_EXT).toFile()).collect(Collectors.toList()));

        rulesDescriptor2 =
            install("rules2",
                Stream.of(Paths.get(DROOLS_RESOURCES_DIR + "rules1" + DRL_EXT).toFile(),
                          Paths.get(DROOLS_RESOURCES_DIR + "rules2" + DRL_EXT).toFile())
                      .collect(Collectors.toList()));

        LoggerUtil.setLevel("ROOT", "WARN");
        LoggerUtil.setLevel("org.onap.policy.drools.controller.internal", "INFO");
    }

    @Test
    public void upgrades() throws InterruptedException {
        DroolsController rules =
            new MavenDroolsController(
                rulesDescriptor1.getGroupId(), rulesDescriptor1.getArtifactId(), rulesDescriptor1.getVersion(),
                null, null);

        assertTrue(rules.start());
        assertTrue(running1a.await(30, TimeUnit.SECONDS));
        summary(rules);
        assertKie(rules, Arrays.asList("SETUP.1", "VERSION.12"), 1);

        rules.updateToVersion(
            rulesDescriptor2.getGroupId(),
            rulesDescriptor2.getArtifactId(),
            rulesDescriptor2.getVersion(),
            null, null);

        assertTrue(running2a.await(30, TimeUnit.SECONDS));
        assertTrue(running2b.await(30, TimeUnit.SECONDS));
        summary(rules);
        assertKie(rules, Arrays.asList("SETUP.1", "VERSION.12", "SETUP.2", "VERSION.2"), 2);

        rules.updateToVersion(
            rulesDescriptor1.getGroupId(),
            rulesDescriptor1.getArtifactId(),
            rulesDescriptor1.getVersion(),
            null, null);

        assertTrue(running1b.await(30, TimeUnit.SECONDS));
        summary(rules);
        assertKie(rules, Arrays.asList("SETUP.1", "VERSION.12"), 1);
    }

    private void summary(DroolsController rules) {
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.info("Controller: " + rules.getGroupId() + ":" + rules.getArtifactId() + ":" + rules.getVersion());
        logger.info(".....................................................................");
        logger.info("KIE-BASES: " + KieUtils.getBases(rules.getContainer().getKieContainer()));
        logger.info("KIE-PACKAGE-NAMES: " + KieUtils.getPackageNames(rules.getContainer().getKieContainer()));
        logger.info("KIE-RULE-NAMES: " + KieUtils.getRuleNames(rules.getContainer().getKieContainer()));
        logger.info("FACTS: " + rules.facts(KBSESSION_RULES, Object.class));
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }

    private void assertKie(DroolsController controller, List<String> expectedRuleNames, long expectedFactCount) {
        assertEquals(Arrays.asList("kbRules"), KieUtils.getBases(controller.getContainer().getKieContainer()));
        assertEquals(expectedRuleNames, KieUtils.getRuleNames(controller.getContainer().getKieContainer()));
        assertEquals(expectedFactCount, controller.factCount(controller.getSessionNames().get(0)));
    }
}
