/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2021-2022 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021, 2023-2024 Nordix Foundation.
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
 * =============LICENSE_END========================================================
 */

package org.onap.policy.drools.lifecycle;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_NOOP_SINK_TOPICS;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_NOOP_SOURCE_TOPICS;

import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.message.bus.event.TopicEndpointManager;
import org.onap.policy.common.message.bus.event.noop.NoopTopicFactories;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.logging.LoggerUtils;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.common.utils.time.PseudoScheduledExecutorService;
import org.onap.policy.common.utils.time.TestTimeMulti;
import org.onap.policy.drools.domain.models.artifact.NativeArtifactPolicy;
import org.onap.policy.drools.domain.models.controller.ControllerPolicy;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.server.restful.TestConstants;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaServiceTemplate;

/**
 * Lifecycle FSM Updates Test.
 */
public class LifecycleFsmUpdateTest {

    private static final String EXAMPLE_NATIVE_CONTROLLER_POLICY_NAME = "example.controller";
    private static final String EXAMPLE_NATIVE_CONTROLLER_POLICY_JSON =
        "src/test/resources/tosca-policy-native-controller-example.json";

    private static final String EXAMPLE_NATIVE_ARTIFACT_POLICY_NAME = "example.artifact";
    private static final String EXAMPLE_NATIVE_ARTIFACT_POLICY_JSON =
        "src/test/resources/tosca-policy-native-artifact-example.json";

    private static final String EXAMPLE_OTHER_UNVAL_POLICY_NAME = "other-unvalidated";
    private static final String EXAMPLE_OTHER_UNVAL_POLICY_JSON =
        "src/test/resources/tosca-policy-other-unvalidated.json";

    private static final String EXAMPLE_OTHER_VAL_POLICY_NAME = "other-validated";
    private static final String EXAMPLE_OTHER_VAL_POLICY_JSON = "src/test/resources/tosca-policy-other-validated.json";

    private static final String FOO_NATIVE_CONTROLLER_POLICY_NAME = "foo.controller";
    private static final String FOO_NATIVE_CONTROLLER_POLICY_JSON =
        "src/test/resources/tosca-policy-native-controller-foo.json";

    private static final String FOO_NATIVE_ARTIFACT_POLICY_NAME = "foo.artifact";
    private static final String FOO_NATIVE_ARTIFACT_POLICY_JSON =
        "src/test/resources/tosca-policy-native-artifact-foo.json";

    private static final String VCPE_OP_POLICY_NAME = "operational.restart";
    private static final String VCPE_OPERATIONAL_DROOLS_POLICY_JSON =
        "policies/vCPE.policy.operational.input.tosca.json";

    private static final String VFW_OP_POLICY_NAME = "operational.modifyconfig";
    private static final String VFW_OPERATIONAL_DROOLS_POLICY_JSON =
        "policies/vFirewall.policy.operational.input.tosca.json";

    private static final StandardCoder coder = new StandardCoder();

    protected static LifecycleFsm savedFsm;
    protected LifecycleFsm fsm;

    private ToscaPolicy opPolicy;
    private ToscaPolicy op2Policy;
    private ToscaPolicy valPolicy;
    private ToscaPolicy unvalPolicy;
    private ToscaPolicy controllerPolicy;
    private ToscaPolicy controller2Policy;
    private ToscaPolicy artifactPolicy;
    private ToscaPolicy artifact2Policy;

    /**
     * Set up.
     */
    @BeforeAll
    public static void setUp() throws IOException {
        LoggerUtils.setLevel(LoggerUtils.ROOT_LOGGER, "INFO");
        LoggerUtils.setLevel("org.onap.policy.common.endpoints", "WARN");
        LoggerUtils.setLevel("org.onap.policy.drools", "INFO");

        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");

        // @formatter:off
        ControllerSupport.installArtifact(
                Paths.get(ControllerSupport.JUNIT_KMODULE_PATH).toFile(),
                Paths.get(ControllerSupport.JUNIT_KMODULE_POM_PATH).toFile(),
                ControllerSupport.JUNIT_KJAR_DRL_PATH,
                List.of(Paths.get(ControllerSupport.JUNIT_KMODULE_DRL_PATH).toFile()));

        ControllerSupport.installArtifact(
                Paths.get("src/test/resources/echo.kmodule").toFile(),
                Paths.get("src/test/resources/echo.pom").toFile(),
                "src/main/resources/kbecho/org/onap/policy/drools/test/",
                List.of(Paths.get("src/test/resources/echo.drl").toFile()));
        // @formatter:on

        Properties noopTopicProperties = new Properties();
        noopTopicProperties.put(PROPERTY_NOOP_SOURCE_TOPICS, TestConstants.DCAE_TOPIC);
        noopTopicProperties.put(PROPERTY_NOOP_SINK_TOPICS, TestConstants.APPC_CL_TOPIC);
        TopicEndpointManager.getManager().addTopics(noopTopicProperties);

        savedFsm = LifecycleFeature.getFsm();
    }

    /**
     * Tear Down.
     */
    @AfterAll
    public static void tearDown() throws NoSuchFieldException, IllegalAccessException {
        PolicyControllerConstants.getFactory().destroy();

        NoopTopicFactories.getSourceFactory().destroy();
        NoopTopicFactories.getSinkFactory().destroy();
        try {
            Files.deleteIfExists(Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                "lifecycle-controller.properties.bak"));
            Files.deleteIfExists(Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                "foo-controller.properties.bak"));
        } catch (IOException ignored) { // NOSONAR
            // checkstyle
        }

        ControllerSupport.setStaticField(LifecycleFeature.class, "fsm", savedFsm);
    }

    /**
     * Test initialization.
     */
    @BeforeEach
    public void init() throws CoderException, IOException, NoSuchFieldException, IllegalAccessException {
        fsm = new LifecycleFsm() {
            @Override
            protected ScheduledExecutorService makeExecutor() {
                return new PseudoScheduledExecutorService(new TestTimeMulti());
            }
        };
        ControllerSupport.setStaticField(LifecycleFeature.class, "fsm", fsm);

        fsm.setStatusTimerSeconds(15);
        assertTrue(fsm.start());

        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup("A");
        change.setPdpSubgroup("a");
        change.setState(PdpState.ACTIVE);
        change.setName(fsm.getPdpName());

        fsm.setSubGroup("a");
        fsm.source.offer(new StandardCoder().encode(change));

        assertEquals(0, fsm.getPoliciesMap().size());
        assertEquals("ACTIVE", fsm.state().toString());
        assertEquals(2, fsm.getPolicyTypesMap().size());

        opPolicy = getExamplesPolicy(VFW_OPERATIONAL_DROOLS_POLICY_JSON, VFW_OP_POLICY_NAME);
        op2Policy = getExamplesPolicy(VCPE_OPERATIONAL_DROOLS_POLICY_JSON, VCPE_OP_POLICY_NAME);
        controllerPolicy =
            getPolicyFromFile(EXAMPLE_NATIVE_CONTROLLER_POLICY_JSON, EXAMPLE_NATIVE_CONTROLLER_POLICY_NAME);
        controller2Policy = getPolicyFromFile(FOO_NATIVE_CONTROLLER_POLICY_JSON, FOO_NATIVE_CONTROLLER_POLICY_NAME);
        artifactPolicy = getPolicyFromFile(EXAMPLE_NATIVE_ARTIFACT_POLICY_JSON, EXAMPLE_NATIVE_ARTIFACT_POLICY_NAME);
        artifact2Policy = getExamplesPolicy(FOO_NATIVE_ARTIFACT_POLICY_JSON, FOO_NATIVE_ARTIFACT_POLICY_NAME);
        valPolicy = getPolicyFromFile(EXAMPLE_OTHER_VAL_POLICY_JSON, EXAMPLE_OTHER_VAL_POLICY_NAME);
        unvalPolicy = getPolicyFromFile(EXAMPLE_OTHER_UNVAL_POLICY_JSON, EXAMPLE_OTHER_UNVAL_POLICY_NAME);
    }

    @Test
    void testUpdate() throws CoderException {
        verifyInitState();

        // native controller policy - deploy
        // Delta: +controllerPolicy
        deltaUpdate(List.of(controllerPolicy), List.of(), List.of(controllerPolicy));

        // no policies - undeploy
        // Delta: -controllerPolicy
        deltaUpdate(List.of(), List.of(controllerPolicy), List.of());

        // native controller + artifact policy (out of order) - deploy
        // Delta: +artifactPolicy, +controllerPolicy
        deltaUpdate(List.of(artifactPolicy, controllerPolicy), List.of(),
            List.of(artifactPolicy, controllerPolicy));

        // attempt to deploy opPolicy but invalid controller
        // Delta: +opPolicy
        assertFalse(fsm.update(getPdpUpdate(List.of(opPolicy), List.of())));
        assertEquals(1, PolicyControllerConstants.getFactory().inventory().size());
        assertFalse(fsm.getActivePolicies().contains(opPolicy));
        verifyExists(false, "lifecycle", List.of(opPolicy));
        verifyDeploy(List.of(artifactPolicy, controllerPolicy));

        // Delta: +opPolicy
        opPolicy.getProperties().remove("controllerName");
        deltaUpdate(List.of(opPolicy), List.of(), List.of(opPolicy, artifactPolicy, controllerPolicy));
        verifyExists(true, "lifecycle", List.of(opPolicy));

        // Delta: -opPolicy
        deltaUpdate(List.of(), List.of(opPolicy), List.of(controllerPolicy, artifactPolicy));
        assertFalse(PolicyControllerConstants.getFactory().get("lifecycle").getDrools().exists(opPolicy));

        // Delta: -artifactPolicy
        deltaUpdate(List.of(), List.of(artifactPolicy), List.of(controllerPolicy));
        assertFalse(PolicyControllerConstants.getFactory().get("lifecycle").getDrools().isBrained());

        // Delta: -controllerPolicy
        deltaUpdate(List.of(), List.of(controllerPolicy), List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> PolicyControllerConstants.getFactory().get("lifecycle"));

        // Delta: +controllerPolicy, +artifactPolicy, and +opPolicy
        deltaUpdate(List.of(opPolicy, artifactPolicy, controllerPolicy), List.of(),
            List.of(opPolicy, artifactPolicy, controllerPolicy));
        verifyExists(true, "lifecycle", List.of(opPolicy));

        // Delta: -artifactPolicy
        assertTrue(fsm.update(getPdpUpdate(List.of(), List.of(artifactPolicy))));
        assertEquals(1, PolicyControllerConstants.getFactory().inventory().size());
        assertFalse(PolicyControllerConstants.getFactory().get("lifecycle").getDrools().isBrained());

        // Delta: +artifactPolicy
        // from deltas, all delta updates should be successfully applied
        deltaUpdate(List.of(artifactPolicy), List.of(), List.of(opPolicy, artifactPolicy, controllerPolicy));
        verifyExists(true, "lifecycle", List.of(opPolicy));

        // Delta: -controllerPolicy
        // from deltas, all delta updates should be successfully applied
        assertTrue(fsm.update(getPdpUpdate(List.of(), List.of(controllerPolicy))));
        assertEquals(0, PolicyControllerConstants.getFactory().inventory().size());

        // Delta: +controllerPolicy
        // from deltas, all delta updates should be successfully applied
        deltaUpdate(List.of(controllerPolicy), List.of(), List.of(opPolicy, artifactPolicy, controllerPolicy));
        verifyExists(true, "lifecycle", List.of(opPolicy));

        // Delta: +op2Policy, +controller2Policy
        // from deltas, all delta updates should be successfully applied
        op2Policy.getProperties().put("controllerName", "lifecycle");
        deltaUpdate(List.of(op2Policy, controller2Policy), List.of(),
            List.of(opPolicy, artifactPolicy, controllerPolicy, op2Policy, controller2Policy));
        verifyExists(true, "lifecycle", List.of(opPolicy, op2Policy));
        assertFalse(PolicyControllerConstants.getFactory().get("foo").getDrools().isBrained());

        // same operation with duplicates - idempotent operation
        deltaUpdate(List.of(op2Policy, controller2Policy, opPolicy), List.of(valPolicy, unvalPolicy),
            List.of(opPolicy, artifactPolicy, controllerPolicy, op2Policy, controller2Policy));
        verifyExists(true, "lifecycle", List.of(opPolicy, op2Policy));
        assertFalse(PolicyControllerConstants.getFactory().get("foo").getDrools().isBrained());

        // Delta: +artifact2policy, +valPolicy, +unvalPolicy
        // from deltas, all delta updates should be successfully applied
        deltaUpdate(List.of(valPolicy, unvalPolicy, artifact2Policy), List.of(), List.of(opPolicy, artifactPolicy,
            controllerPolicy, op2Policy, controller2Policy, valPolicy, unvalPolicy, artifact2Policy));
        verifyExists(true, "lifecycle", List.of(opPolicy, op2Policy, valPolicy, unvalPolicy));
        verifyExists(true, "foo", List.of(valPolicy, unvalPolicy));
        verifyExists(false, "foo", List.of(opPolicy, op2Policy));

        // Delta: -artifact2Policy, -unvalPolicy
        // from deltas, all delta updates should be successfully applied, and unvalPolicy disabled
        deltaUpdate(List.of(), List.of(artifact2Policy, unvalPolicy),
            List.of(opPolicy, artifactPolicy, controllerPolicy, op2Policy, controller2Policy, valPolicy));
        verifyExists(true, "lifecycle", List.of(opPolicy, op2Policy, valPolicy));
        verifyExists(false, "lifecycle", List.of(unvalPolicy));
        assertFalse(PolicyControllerConstants.getFactory().get("foo").getDrools().isBrained());

        // Delta: +artifact2Policy
        // from deltas, all delta updates should be successfully applied, opPolicy, op2Policy and unvalPolicy
        // should be re-applied.
        assertTrue(fsm.update(getPdpUpdate(List.of(artifact2Policy), List.of())));
        deltaUpdate(List.of(artifact2Policy), List.of(), List.of(opPolicy, artifactPolicy, controllerPolicy, op2Policy,
            controller2Policy, valPolicy, artifact2Policy));
        verifyExists(true, "lifecycle", List.of(opPolicy, op2Policy, valPolicy));
        verifyExists(false, "lifecycle", List.of(unvalPolicy));
        verifyExists(true, "foo", List.of(valPolicy));
        verifyExists(false, "foo", List.of(opPolicy, op2Policy, unvalPolicy));

        // Delta: -controllerPolicy, -artifactPolicy, +unvalPolicy
        // from deltas, all delta updates should be successful
        assertTrue(fsm.update(getPdpUpdate(List.of(unvalPolicy), List.of(controllerPolicy, artifactPolicy))));
        assertThatIllegalArgumentException().isThrownBy(() -> PolicyControllerConstants.getFactory().get("lifecycle"));
        verifyExists(true, "foo", List.of(valPolicy, unvalPolicy));
        verifyExists(false, "foo", List.of(opPolicy, op2Policy));
        // since -controllerPolicy delta => the existing artifact2Policy, opPolicy, op2Policy become disabled as no
        // policy type is readily available to host them until there is a host.
        verifyActivePoliciesWithDisables(
            List.of(opPolicy, op2Policy, controller2Policy, valPolicy, artifact2Policy, unvalPolicy),
            List.of(opPolicy.getIdentifier(), op2Policy.getIdentifier()));

        // Delta: -opPolicy, -op2Policy, -controller2Policy, -valPolicy, -artifact2Policy, -unvalPolicy
        // from deltas, -opPolicy and -op2Policy undeploys will fail since there is no controller with that
        // policy type supported
        assertFalse(fsm.update(getPdpUpdate(List.of(),
            List.of(opPolicy, op2Policy, controller2Policy, valPolicy, artifact2Policy, unvalPolicy))));
        assertThatIllegalArgumentException().isThrownBy(() -> PolicyControllerConstants.getFactory().get("lifecycle"));
        assertThatIllegalArgumentException().isThrownBy(() -> PolicyControllerConstants.getFactory().get("foo"));
        assertEquals(0, PolicyControllerConstants.getFactory().inventory().size());
        verifyDeploy(List.of());

        fsm.shutdown();
    }

    private void verifyInitState() {
        assertEquals(0, fsm.getPoliciesMap().size());
        assertEquals("ACTIVE", fsm.state().toString());
        assertEquals(0, PolicyControllerConstants.getFactory().inventory().size());
    }

    protected PdpUpdate getPdpUpdate(List<ToscaPolicy> policiesToDeploy, List<ToscaPolicy> policiesToUndeploy) {
        PdpUpdate update = new PdpUpdate();
        update.setName(PolicyEngineConstants.getManager().getPdpName());
        update.setPdpGroup("A");
        update.setPdpSubgroup("a");
        update.setPoliciesToBeDeployed(policiesToDeploy);
        update.setPoliciesToBeUndeployed(fsm.getPolicyIds(policiesToUndeploy));
        return update;
    }

    protected void deltaUpdate(List<ToscaPolicy> deploy, List<ToscaPolicy> undeploy, List<ToscaPolicy> active)
        throws CoderException {
        assertTrue(fsm.update(getPdpUpdate(deploy, undeploy)));
        verifyDeploy(active);
    }

    private void verifyDeploy(List<ToscaPolicy> active) throws CoderException {
        verifyActivePolicies(active);
    }

    protected void verifyExists(boolean exists, String controller, List<ToscaPolicy> policies) {
        assertTrue(PolicyControllerConstants.getFactory().get(controller).getDrools().isBrained());
        for (ToscaPolicy policy : policies) {
            assertEquals(exists, PolicyControllerConstants.getFactory().get(controller).getDrools().exists(policy),
                "ID: " + controller + ":" + policy.getIdentifier());
        }
    }

    protected void verifyActivePolicies(List<ToscaPolicy> testPolicies) throws CoderException {
        verifyActivePoliciesWithDisables(testPolicies, Collections.emptyList());
    }

    protected void verifyActivePoliciesWithDisables(List<ToscaPolicy> testPolicies,
                                                    List<ToscaConceptIdentifier> nativeDisables) throws CoderException {
        // verify that each policy is tracked in the active lists

        for (ToscaPolicy policy : testPolicies) {
            assertTrue(fsm.getActivePolicies().contains(policy), policy.getIdentifier().toString());
            if (!nativeDisables.contains(policy.getIdentifier())) {
                assertTrue(fsm.getPolicyTypesMap().containsKey(policy.getTypeIdentifier()),
                    policy.getIdentifier().toString());
            }
        }
        assertEquals(testPolicies.size(), fsm.getActivePolicies().size());

        Map<String, List<ToscaPolicy>> testPoliciesMap = fsm.groupPoliciesByPolicyType(testPolicies);
        Map<String, List<ToscaPolicy>> activePolicyMap = fsm.groupPoliciesByPolicyType(fsm.getActivePolicies());
        assertEquals(new HashSet<>(activePolicyMap.keySet()), new HashSet<>(testPoliciesMap.keySet()));
        for (String key : fsm.groupPoliciesByPolicyType(fsm.getActivePolicies()).keySet()) {
            assertEquals(new HashSet<>(activePolicyMap.get(key)), new HashSet<>(testPoliciesMap.get(key)));
        }

        // verify that a controller exists for each controller policy
        assertEquals(fsm.getNativeControllerPolicies(testPoliciesMap).size(),
            PolicyControllerConstants.getFactory().inventory().size());

        // verify that a brained controller with same application coordinates exist for
        // each native artifact policy

        verifyNativeArtifactPolicies(fsm.getNativeArtifactPolicies(testPoliciesMap));

        // verify non-native policies are attached to the right set of brained controllers

        for (ToscaPolicy policy : fsm.getNonNativePolicies(testPoliciesMap)) {
            verifyNonNativePolicy(policy, nativeDisables);
        }
    }

    protected void verifyNativeArtifactPolicies(List<ToscaPolicy> policies) throws CoderException {
        // check that a brained controller exists for each native artifact policy
        for (ToscaPolicy policy : policies) {
            NativeArtifactPolicy naPolicy = fsm.getDomainMaker().convertTo(policy, NativeArtifactPolicy.class);
            String controllerName = naPolicy.getProperties().getController().getName();
            assertTrue(PolicyControllerConstants.getFactory().get(controllerName).getDrools().isBrained());
            assertEquals(naPolicy.getProperties().getRulesArtifact().getGroupId(),
                PolicyControllerConstants.getFactory().get(controllerName).getDrools().getGroupId());
            assertEquals(naPolicy.getProperties().getRulesArtifact().getArtifactId(),
                PolicyControllerConstants.getFactory().get(controllerName).getDrools().getArtifactId());
            assertEquals(naPolicy.getProperties().getRulesArtifact().getVersion(),
                PolicyControllerConstants.getFactory().get(controllerName).getDrools().getVersion());
        }
    }

    protected void verifyNonNativePolicy(ToscaPolicy testPolicy, List<ToscaConceptIdentifier> nativeDisables)
        throws CoderException {
        List<ToscaPolicy> nativeArtifactPolicies =
            fsm.getNativeArtifactPolicies(fsm.groupPoliciesByPolicyType(fsm.getActivePolicies()));

        List<ToscaPolicy> nativeControllerPolicies =
            fsm.getNativeControllerPolicies(fsm.groupPoliciesByPolicyType(fsm.getActivePolicies()));

        String controllerName = (String) testPolicy.getProperties().get("controllerName");

        if (Strings.isNullOrEmpty(controllerName)) {
            // this non-native policy applies to all controllers that are brained

            // verify the policy is present as a fact in all brained controllers
            // and there is a controller policy for each controllerName

            for (ToscaPolicy nativePolicy : nativeArtifactPolicies) {
                NativeArtifactPolicy naPolicy =
                    fsm.getDomainMaker().convertTo(nativePolicy, NativeArtifactPolicy.class);
                String artifactControllerName = naPolicy.getProperties().getController().getName();

                // brained controller check
                assertTrue(PolicyControllerConstants.getFactory().get(artifactControllerName).getDrools().isBrained(),
                    artifactControllerName + ":" + testPolicy.getIdentifier());

                // non-native tosca policy as a fact in drools
                if (PolicyControllerConstants.getFactory().get(artifactControllerName).getPolicyTypes()
                    .contains(testPolicy.getTypeIdentifier())) {
                    assertTrue(PolicyControllerConstants.getFactory()
                        .get(artifactControllerName).getDrools().exists(testPolicy),
                        artifactControllerName + ":" + testPolicy.getIdentifier());
                } else {
                    assertFalse(PolicyControllerConstants.getFactory()
                        .get(artifactControllerName).getDrools().exists(testPolicy),
                        artifactControllerName + ":" + testPolicy.getIdentifier());
                }

                // there should always be a controller policy for each artifact policy
                assertEquals(1, getNativeControllerPolicies(nativeControllerPolicies, artifactControllerName).size());
            }

            return;
        }

        // this non-native policy applies only to the specified native controller
        // which could be brained or brainless

        // there should always be a controller policy

        if (nativeDisables.contains(testPolicy.getIdentifier())) {
            // skip evaluating next section
            return;
        }

        assertEquals(1, getNativeControllerPolicies(nativeControllerPolicies, controllerName).size());

        // verify the policy is present as a fact if there is matching artifact policy

        List<NativeArtifactPolicy> candidateNativeArtifactPolicies =
            getNativeArtifactPolicies(nativeArtifactPolicies, controllerName);

        if (candidateNativeArtifactPolicies.size() == 1) {
            assertTrue(PolicyControllerConstants.getFactory().get(controllerName).getDrools().isBrained(),
                controllerName + ":" + testPolicy.getIdentifier());
            assertTrue(PolicyControllerConstants.getFactory().get(controllerName).getDrools().exists(testPolicy),
                controllerName + ":" + testPolicy.getIdentifier());

            // verify that the other brained controllers don't have this non-native policy

            // @formatter:off
            for (NativeArtifactPolicy nativePolicy :
                    getNativeArtifactPoliciesBut(nativeArtifactPolicies, controllerName)) {
                assertTrue(PolicyControllerConstants.getFactory()
                                   .get(nativePolicy.getProperties().getController().getName())
                                   .getDrools().isBrained());
                assertFalse(PolicyControllerConstants.getFactory()
                    .get(nativePolicy.getProperties().getController().getName()).getDrools().exists(testPolicy),
                    controllerName + ":" + testPolicy.getIdentifier());
            }
            // @formatter:on

            return;
        }

        // at this point the only valid possibility is that there is no native artifact policies

        assertTrue(candidateNativeArtifactPolicies.isEmpty(),
            "There is more than 1 native artifact policy for " + controllerName);
    }

    protected List<NativeArtifactPolicy> getNativeArtifactPoliciesBut(List<ToscaPolicy> nativePolicies,
                                                                      String controllerName) {
        return nativePolicies.stream().map(nativePolicy -> {
            try {
                return fsm.getDomainMaker().convertTo(nativePolicy, NativeArtifactPolicy.class);
            } catch (CoderException ex) {
                throw new RuntimeException(nativePolicy.getIdentifier().toString(), ex);
            }
        }).filter(nativeArtifactPolicy -> !controllerName
            .equals(nativeArtifactPolicy.getProperties().getController().getName())).collect(Collectors.toList());
    }

    protected List<NativeArtifactPolicy> getNativeArtifactPolicies(List<ToscaPolicy> nativePolicies,
                                                                   String controllerName) {
        return nativePolicies.stream().map(nativePolicy -> {
            try {
                return fsm.getDomainMaker().convertTo(nativePolicy, NativeArtifactPolicy.class);
            } catch (CoderException ex) {
                throw new RuntimeException(nativePolicy.getIdentifier().toString(), ex);
            }
        }).filter(nativeArtifactPolicy -> controllerName
            .equals(nativeArtifactPolicy.getProperties().getController().getName())).collect(Collectors.toList());
    }

    protected List<ControllerPolicy> getNativeControllerPolicies(List<ToscaPolicy> nativePolicies,
                                                                 String controllerName) {
        return nativePolicies.stream().map(nativePolicy -> {
            try {
                return fsm.getDomainMaker().convertTo(nativePolicy, ControllerPolicy.class);
            } catch (CoderException ex) {
                throw new RuntimeException(nativePolicy.getIdentifier().toString(), ex);
            }
        }).filter(nativePolicy -> controllerName.equals(nativePolicy.getProperties().getControllerName()))
                .collect(Collectors.toList());
    }

    protected ToscaPolicy getPolicyFromFile(String filePath, String policyName) throws CoderException, IOException {
        String policyJson = Files.readString(Paths.get(filePath));
        ToscaServiceTemplate serviceTemplate = coder.decode(policyJson, ToscaServiceTemplate.class);
        return serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(policyName);
    }

    protected ToscaPolicy getExamplesPolicy(String resourcePath, String policyName) throws CoderException {
        String policyJson = ResourceUtils.getResourceAsString(resourcePath);
        ToscaServiceTemplate serviceTemplate = new StandardCoder().decode(policyJson, ToscaServiceTemplate.class);
        return serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(policyName);
    }

}
