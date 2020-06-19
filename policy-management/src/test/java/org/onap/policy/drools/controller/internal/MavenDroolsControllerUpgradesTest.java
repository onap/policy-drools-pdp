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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.ReleaseId;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.AgendaGroupPoppedEvent;
import org.kie.api.event.rule.AgendaGroupPushedEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.util.KieUtils;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenDroolsControllerUpgradesTest {
    public static final CountDownLatch running1a = new CountDownLatch(1);
    public static final CountDownLatch running1b = new CountDownLatch(1);
    public static final CountDownLatch running2a = new CountDownLatch(1);
    public static final CountDownLatch running2b = new CountDownLatch(1);

    private static final String DROOLS_RESOURCES_DIR = "src/test/resources/";
    private static final String DROOLS_KJAR_RESOURCES_DIR = "src/main/resources/";
    private static final String DRL_EXT = ".drl";
    private static final String POM_EXT = ".pom";
    private static final String KMODULE_EXT = ".kmodule";

    private static final String RULES_BASE = "rules";
    private static final String KBNAME_RULES = "kbRules";
    private static final String KBSESSION_RULES = RULES_BASE;
    private static final String KBPACKAGE_RULES = RULES_BASE;

    private static ReleaseId rulesDescriptor1;
    private static ReleaseId rulesDescriptor2;

    private DroolsController controller;

    private static final Logger logger = LoggerFactory.getLogger(MavenDroolsControllerUpgradesTest.class);

    private static ReleaseId install(String name, List<File> drls) throws IOException {
        return
            KieUtils.installArtifact(
                Paths.get(DROOLS_RESOURCES_DIR + RULES_BASE + KMODULE_EXT).toFile(),
                Paths.get(DROOLS_RESOURCES_DIR + name + POM_EXT).toFile(),
               DROOLS_KJAR_RESOURCES_DIR + KBNAME_RULES + "/" + KBPACKAGE_RULES + "/",
                drls);
    }

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

    /**
     * Creates a controller before each test.
     */
    @Before
    public void beforeTest() {
        controller =
            new MavenDroolsController(
                rulesDescriptor1.getGroupId(), rulesDescriptor1.getArtifactId(), rulesDescriptor1.getVersion(),
                null, null);
    }

    /**
     * Shuts down the controller after each test.
     */
    @After
    public void afterTest() {
        if (controller != null) {
            controller.halt();
        }
    }

    /**
     * Upgrades test.
     */
    @Test
    public void upgrades() throws InterruptedException {
        assertTrue(controller.start());
        logKieEvents();

        assertTrue(running1a.await(30, TimeUnit.SECONDS));
        summary();
        assertKie(Arrays.asList("run-drools-runnable", "SETUP.1", "VERSION.12"), 1);

        controller.updateToVersion(
            rulesDescriptor2.getGroupId(),
            rulesDescriptor2.getArtifactId(),
            rulesDescriptor2.getVersion(),
            null, null);

        assertTrue(running2a.await(30, TimeUnit.SECONDS));
        assertTrue(running2b.await(30, TimeUnit.SECONDS));
        assertTrue(running1b.await(30, TimeUnit.SECONDS));
        summary();
        assertKie(Arrays.asList("run-drools-runnable", "SETUP.1", "VERSION.12", "SETUP.2", "VERSION.2"), 1);

        controller.updateToVersion(
            rulesDescriptor1.getGroupId(),
            rulesDescriptor1.getArtifactId(),
            rulesDescriptor1.getVersion(),
            null, null);

        summary();
        assertKie(Arrays.asList("run-drools-runnable", "SETUP.1", "VERSION.12"), 1);
    }

    private void summary() {
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.info("Controller: " + controller.getGroupId() + ":" + controller.getArtifactId()
            + ":" + controller.getVersion());
        logger.info(".....................................................................");
        logger.info("KIE-BASES: " + KieUtils.getBases(controller.getContainer().getKieContainer()));
        logger.info("KIE-PACKAGE-NAMES: " + KieUtils.getPackageNames(controller.getContainer().getKieContainer()));
        logger.info("KIE-RULE-NAMES: " + KieUtils.getRuleNames(controller.getContainer().getKieContainer()));
        logger.info("FACTS: " + controller.facts(KBSESSION_RULES, Object.class));
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }

    private void assertKie(List<String> expectedRuleNames, long expectedFactCount) {
        assertEquals(Collections.singletonList("kbRules"),
            KieUtils.getBases(controller.getContainer().getKieContainer()));
        assertEquals(expectedRuleNames, KieUtils.getRuleNames(controller.getContainer().getKieContainer()));
        assertEquals(expectedFactCount, controller.factCount(controller.getSessionNames().get(0)));
    }

    private void logKieEvents() {
        controller.getContainer()
            .getPolicySession(KBSESSION_RULES)
            .getKieSession()
                .addEventListener(new RuleRuntimeEventListener() {
                    @Override
                    public void objectInserted(ObjectInsertedEvent objectInsertedEvent) {
                        logger.info("RULE {}: inserting {}",
                            objectInsertedEvent.getRule().getName(), objectInsertedEvent.getObject());
                    }

                    @Override
                    public void objectUpdated(ObjectUpdatedEvent objectUpdatedEvent) {
                        logger.info("RULE {}: updating {}",
                            objectUpdatedEvent.getRule().getName(), objectUpdatedEvent.getObject());
                    }

                    @Override
                    public void objectDeleted(ObjectDeletedEvent objectDeletedEvent) {
                        logger.info("RULE {}: deleting {}",
                            objectDeletedEvent.getRule().getName(), objectDeletedEvent.getOldObject());
                    }
                });

        controller.getContainer()
            .getPolicySession(KBSESSION_RULES)
            .getKieSession()
            .addEventListener(new AgendaEventListener() {
                @Override
                public void matchCreated(MatchCreatedEvent matchCreatedEvent) {
                    logger.info("RULE {}: matchCreated", matchCreatedEvent.getMatch().getRule().getName());
                }

                @Override
                public void matchCancelled(MatchCancelledEvent matchCancelledEvent) {
                    logger.info("RULE {}: matchCancelled", matchCancelledEvent.getMatch().getRule().getName());
                }

                @Override
                public void beforeMatchFired(BeforeMatchFiredEvent beforeMatchFiredEvent) {
                    logger.info("RULE {}: beforeMatchFired", beforeMatchFiredEvent.getMatch().getRule().getName());
                }

                @Override
                public void afterMatchFired(AfterMatchFiredEvent afterMatchFiredEvent) {
                    logger.info("RULE {}: afterMatchFired", afterMatchFiredEvent.getMatch().getRule().getName());
                }

                @Override
                public void agendaGroupPopped(AgendaGroupPoppedEvent agendaGroupPoppedEvent) {
                    /* do nothing */
                }

                @Override
                public void agendaGroupPushed(AgendaGroupPushedEvent agendaGroupPushedEvent) {
                    /* do nothing */

                }

                @Override
                public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent ruleFlowGroupActivatedEvent) {
                    /* do nothing */

                }

                @Override
                public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent ruleFlowGroupActivatedEvent) {
                    /* do nothing */

                }

                @Override
                public void beforeRuleFlowGroupDeactivated(
                    RuleFlowGroupDeactivatedEvent ruleFlowGroupDeactivatedEvent) {
                    /* do nothing */

                }

                @Override
                public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent ruleFlowGroupDeactivatedEvent) {
                    /* do nothing */
                }
            });
    }
}
