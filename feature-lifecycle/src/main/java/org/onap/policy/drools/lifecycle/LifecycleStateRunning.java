/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2019 Bell Canada.
 * Modifications Copyright (C) 2021 Nordix Foundation.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.drools.domain.models.artifact.NativeArtifactPolicy;
import org.onap.policy.drools.policies.DomainMaker;
import org.onap.policy.models.pdp.concepts.PdpResponseDetails;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpResponseStatus;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for default functionality for running states.
 */
public abstract class LifecycleStateRunning extends LifecycleStateDefault {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleStateRunning.class);

    protected LifecycleStateRunning(LifecycleFsm manager) {
        super(manager);
    }

    protected abstract boolean stateChangeToPassive(@NonNull PdpStateChange change);

    protected abstract boolean stateChangeToActive(@NonNull PdpStateChange change);

    protected abstract boolean deployPolicy(@NonNull PolicyTypeController controller, @NonNull ToscaPolicy policy);

    protected abstract boolean undeployPolicy(@NonNull PolicyTypeController controller, @NonNull ToscaPolicy policy);

    @Override
    public boolean start() {
        logger.warn("{}: start", this);
        return false;
    }

    @Override
    public boolean stop() {
        synchronized (fsm) {
            boolean success = fsm.statusAction(PdpState.TERMINATED, null);
            success = fsm.stopAction() && success;
            return transitionToState(new LifecycleStateTerminated(fsm)) && success;
        }
    }

    @Override
    public void shutdown() {
        synchronized (fsm) {
            stop();
            fsm.shutdownAction();
        }
    }

    @Override
    public boolean status() {
        synchronized (fsm) {
            if (!fsm.isMandatoryPolicyTypesCompliant()) {
                logger.info("Not all expected policy types are registered yet, current={}, expected={}",
                        fsm.getCurrentPolicyTypes(), fsm.getMandatoryPolicyTypes());
                return false;
            }

            return fsm.statusAction();
        }
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public boolean stateChange(@NonNull PdpStateChange change) {
        synchronized (fsm) {
            if (change.getState() == PdpState.PASSIVE) {
                return stateChangeToPassive(change);
            }

            if (change.getState() == PdpState.ACTIVE) {
                return stateChangeToActive(change);
            }

            logger.warn("{}: state-change: {}", this, change);

            invalidStateChange(change);
            return false;
        }
    }

    @Override
    public boolean update(@NonNull PdpUpdate update) {
        synchronized (fsm) {
            if (update.getPdpHeartbeatIntervalMs() != null
                    && !fsm.setStatusIntervalAction(update.getPdpHeartbeatIntervalMs() / 1000)) {
                fsm.statusAction(response(update.getRequestId(), PdpResponseStatus.FAIL,
                    "invalid interval: " + update.getPdpHeartbeatIntervalMs() + " seconds"));
                return false;
            }

            // update subgroup if applicable per update message

            fsm.setSubGroup(update.getPdpSubgroup());

            // Compute the desired final policy set after processing this update.
            // Delta policies allows for the PAP to send us just the policies to deploy and undeploy
            // Note that in this mode of operation, there may be dependent policies in the
            // active inventory.  For example a request to remove a controller policy in a
            // delta request, may affect operational or artifact policies in use.

            List<ToscaPolicy> desiredPolicyInventory =
                fsm.mergePolicies(update.getPoliciesToBeDeployed(), update.getPoliciesToBeUndeployed());

            // snapshot the active policies previous to apply the new set of active
            // policies as given by the PAP in the update message

            List<ToscaPolicy> activePoliciesPreUpdate = fsm.getActivePolicies();
            Map<String, List<ToscaPolicy>> activePoliciesPreUpdateMap =
                    fsm.groupPoliciesByPolicyType(activePoliciesPreUpdate);

            Pair<List<ToscaPolicy>, List<ToscaPolicy>> results = updatePoliciesWithResults(desiredPolicyInventory);

            // summary message to return in the update response to the PAP

            List<ToscaPolicy> failedPolicies = new ArrayList<>(results.getLeft());
            failedPolicies.addAll(results.getRight());

            // If there are *new* native controller policies deployed, there may
            // existing native artifact policies (previous to the update event
            // processing) that would need to be reapplied.   This requires
            // going through the list of those native artifact policies that
            // were neither deployed or undeployed and re-apply them on top
            // of the controllers.

            failedPolicies.addAll(reApplyNativeArtifactPolicies(activePoliciesPreUpdate, activePoliciesPreUpdateMap));

            // If there are *new* native artifact policies deployed, there may be existing
            // non-native policies (previous to the update event processing)
            // that will need to be reapplied as the new controllers don't know about them.
            // This requires going through the list of those non-native policies
            // which neither were undeployed or deployed and re-apply them on top of the
            // new "brained" controllers.

            failedPolicies.addAll(reApplyNonNativePolicies(activePoliciesPreUpdateMap));

            PdpResponseDetails response =
                response(update.getRequestId(),
                        (failedPolicies.isEmpty()) ? PdpResponseStatus.SUCCESS : PdpResponseStatus.FAIL,
                        fsm.getPolicyIdsMessage(failedPolicies));

            return fsm.statusAction(response) && failedPolicies.isEmpty();
        }
    }

    @Override
    public boolean updatePolicies(List<ToscaPolicy> policies) {
        Pair<List<ToscaPolicy>, List<ToscaPolicy>> results = updatePoliciesWithResults(policies);
        return results.getLeft().isEmpty() && results.getRight().isEmpty();
    }

    protected Pair<List<ToscaPolicy>, List<ToscaPolicy>> updatePoliciesWithResults(List<ToscaPolicy> policies) {
        if (policies == null) {
            return Pair.of(Collections.emptyList(), Collections.emptyList());
        }

        // Note that PAP sends the list of all ACTIVE policies with every
        // UPDATE message.   First, we will undeploy all policies that are
        // running but are not present in this list.  This will include
        // policies that are overridden by a different version.   Second,
        // we will deploy those policies that are not installed but
        // included in this list.

        List<ToscaPolicy> failedUndeployPolicies = undeployPolicies(policies);
        if (!failedUndeployPolicies.isEmpty()) {
            logger.warn("update-policies: undeployment failures: {}", fsm.getPolicyIdsMessage(failedUndeployPolicies));
            failedUndeployPolicies.forEach(fsm::failedUndeployPolicyAction);
        }

        List<ToscaPolicy> failedDeployPolicies = deployPolicies(policies);
        if (!failedDeployPolicies.isEmpty()) {
            logger.warn("update-policies: deployment failures: {}", fsm.getPolicyIdsMessage(failedDeployPolicies));
            failedDeployPolicies.forEach(fsm::failedDeployPolicyAction);
        }

        return Pair.of(failedUndeployPolicies, failedDeployPolicies);
    }

    protected List<ToscaPolicy> reApplyNonNativePolicies(Map<String, List<ToscaPolicy>> preActivePoliciesMap) {
        // only need to re-apply non native policies if there are new native artifact policies

        Map<String, List<ToscaPolicy>> activePoliciesByType = fsm.groupPoliciesByPolicyType(fsm.getActivePolicies());
        List<ToscaPolicy> activeNativeArtifactPolicies = fsm.getNativeArtifactPolicies(activePoliciesByType);

        activeNativeArtifactPolicies.removeAll(fsm.getNativeArtifactPolicies(preActivePoliciesMap));
        if (activeNativeArtifactPolicies.isEmpty()) {
            logger.info("reapply-non-native-policies: nothing to reapply, no new native artifact policies");
            return Collections.emptyList();
        }

        // need to re-apply non native policies

        // get the non-native policies to be reapplied, this is just the intersection of
        // the original active set, and the new active set (i.e policies that have not changed,
        // or in other words, have not been neither deployed or undeployed.

        List<ToscaPolicy> preNonNativePolicies = fsm.getNonNativePolicies(preActivePoliciesMap);
        preNonNativePolicies.retainAll(fsm.getNonNativePolicies(activePoliciesByType));

        logger.info("re-applying non-native policies {} because new native artifact policies have been found: {}",
                fsm.getPolicyIdsMessage(preNonNativePolicies), fsm.getPolicyIdsMessage(activeNativeArtifactPolicies));

        List<ToscaPolicy> failedPolicies = syncPolicies(preNonNativePolicies, this::deployPolicy);
        logger.info("re-applying non-native policies failures: {}", fsm.getPolicyIdsMessage(failedPolicies));

        return failedPolicies;
    }

    protected List<ToscaPolicy> reApplyNativeArtifactPolicies(List<ToscaPolicy> preActivePolicies,
            Map<String, List<ToscaPolicy>> preActivePoliciesMap) {
        // only need to re-apply native artifact policies if there are new native controller policies

        Map<String, List<ToscaPolicy>> activePoliciesByType = fsm.groupPoliciesByPolicyType(fsm.getActivePolicies());
        List<ToscaPolicy> activeNativeControllerPolicies = fsm.getNativeControllerPolicies(activePoliciesByType);

        activeNativeControllerPolicies.removeAll(fsm.getNativeControllerPolicies(preActivePoliciesMap));
        if (activeNativeControllerPolicies.isEmpty()) {
            logger.info("reapply-native-artifact-policies: nothing to reapply, no new native controller policies");
            return Collections.emptyList();
        }

        // need to re-apply native artifact policies

        // get the native artifact policies to be reapplied, this is just the intersection of
        // the original active set, and the new active set (i.e policies that have not changed,
        // or in other words, have not been neither deployed or undeployed).

        List<ToscaPolicy> preNativeArtifactPolicies = fsm.getNativeArtifactPolicies(preActivePoliciesMap);
        preNativeArtifactPolicies.retainAll(fsm.getNativeArtifactPolicies(activePoliciesByType));

        logger.info("reapply candidate native artifact policies {} as new native controller policies {} were found",
                fsm.getPolicyIdsMessage(preNativeArtifactPolicies),
                fsm.getPolicyIdsMessage(activeNativeControllerPolicies));

        // from the intersection, only need to reapply those for which there is a new native
        // controller policy

        List<ToscaPolicy> preNativeArtifactPoliciesToApply = new ArrayList<>();
        for (ToscaPolicy preNativeArtifactPolicy : preNativeArtifactPolicies) {
            NativeArtifactPolicy nativeArtifactPolicy;
            try {
                nativeArtifactPolicy =
                    fsm.getDomainMaker().convertTo(preNativeArtifactPolicy, NativeArtifactPolicy.class);
            } catch (CoderException | RuntimeException ex) {
                logger.warn("reapply-native-artifact-policy {}: (unexpected) non conformant: ignoring",
                        preNativeArtifactPolicy.getIdentifier(), ex);
                continue;
            }

            String controllerName = nativeArtifactPolicy.getProperties().getController().getName();
            for (ToscaPolicy policy : activeNativeControllerPolicies) {
                if (controllerName.equals(policy.getProperties().get("controllerName"))) {
                    preNativeArtifactPoliciesToApply.add(preNativeArtifactPolicy);
                }
            }
        }

        logger.info("reapply set of native artifact policies {} as new native controller policies {} were found",
                fsm.getPolicyIdsMessage(preNativeArtifactPoliciesToApply),
                fsm.getPolicyIdsMessage(activeNativeControllerPolicies));

        List<ToscaPolicy> failedPolicies = syncPolicies(preNativeArtifactPoliciesToApply, this::deployPolicy);
        logger.info("re-applying native artifact policies failures: {}", fsm.getPolicyIdsMessage(failedPolicies));

        // since we want non-native policies to be reapplied when a new native artifact policy has been
        // reapplied here, remove it from the preActivePolicies, so it is detected as new.

        if (!preNativeArtifactPoliciesToApply.isEmpty()) {
            preActivePolicies.removeAll(preNativeArtifactPoliciesToApply);
            preActivePoliciesMap
                    .getOrDefault(LifecycleFsm.POLICY_TYPE_DROOLS_NATIVE_RULES.getName(), Collections.emptyList())
                    .removeAll(preNativeArtifactPoliciesToApply);
        }
        return failedPolicies;
    }

    protected List<ToscaPolicy> deployPolicies(List<ToscaPolicy> policies) {
        return syncPolicies(fsm.getDeployablePoliciesAction(policies), this::deployPolicy);
    }

    protected List<ToscaPolicy> undeployPolicies(List<ToscaPolicy> policies) {
        return syncPolicies(fsm.getUndeployablePoliciesAction(policies), this::undeployPolicy);
    }

    protected List<ToscaPolicy> syncPolicies(List<ToscaPolicy> policies,
                                   BiPredicate<PolicyTypeController, ToscaPolicy> sync) {
        List<ToscaPolicy> failedPolicies = new ArrayList<>();
        DomainMaker domain = fsm.getDomainMaker();
        for (ToscaPolicy policy : policies) {
            ToscaConceptIdentifier policyType = policy.getTypeIdentifier();
            PolicyTypeController controller = fsm.getController(policyType);
            if (controller == null) {
                logger.warn("no controller found for {}", policyType);
                failedPolicies.add(policy);
                continue;
            }

            if (domain.isRegistered(policy.getTypeIdentifier())) {
                if (!domain.isConformant(policy) || !sync.test(controller, policy)) {
                    failedPolicies.add(policy);
                }
            } else {
                logger.info("no validator registered for policy type {}", policy.getTypeIdentifier());
                if (!sync.test(controller, policy)) {
                    failedPolicies.add(policy);
                }
            }
        }
        return failedPolicies;
    }

    private void invalidStateChange(PdpStateChange change) {
        logger.warn("{}: state-change: {}", this, change);
        fsm.statusAction(response(change.getRequestId(), PdpResponseStatus.FAIL,
                 "invalid state change to " + change.getState()));
    }

    protected PdpResponseDetails response(String requestId, PdpResponseStatus responseStatus, String message) {
        PdpResponseDetails response = new PdpResponseDetails();
        response.setResponseTo(requestId);
        response.setResponseStatus(responseStatus);
        if (message != null) {
            response.setResponseMessage(message);
        }

        return response;
    }
}
