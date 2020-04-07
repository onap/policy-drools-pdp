/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;

/**
 * no-op Drools Controller.
 */
public class NullDroolsController implements DroolsController {

    @Override
    public boolean start() {
        return false;
    }

    @Override
    public boolean stop() {
        return false;
    }

    @Override
    public void shutdown() {
        // do nothing
    }

    @Override
    public void halt() {
        // do nothing
    }

    @Override
    public boolean isAlive() {
        return false;
    }

    @Override
    public boolean lock() {
        return false;
    }

    @Override
    public boolean unlock() {
        return false;
    }

    @Override
    public boolean isLocked() {
        return false;
    }

    @Override
    public String getGroupId() {
        return DroolsControllerConstants.NO_GROUP_ID;
    }

    @Override
    public String getArtifactId() {
        return DroolsControllerConstants.NO_ARTIFACT_ID;
    }

    @Override
    public String getVersion() {
        return DroolsControllerConstants.NO_VERSION;
    }

    @Override
    public List<String> getSessionNames() {
        return new ArrayList<>();
    }

    @Override
    public List<String> getCanonicalSessionNames() {
        return new ArrayList<>();
    }

    @Override
    public List<String> getBaseDomainNames() {
        return Collections.emptyList();
    }

    @Override
    public boolean offer(String topic, String event) {
        return false;
    }

    @Override
    public <T> boolean offer(T event) {
        return false;
    }

    @Override
    public boolean deliver(TopicSink sink, Object event) {
        throw new IllegalStateException(makeInvokeMsg());
    }

    @Override
    public Object[] getRecentSourceEvents() {
        return new String[0];
    }

    @Override
    public PolicyContainer getContainer() {
        return null;
    }

    @Override
    public String[] getRecentSinkEvents() {
        return new String[0];
    }

    @Override
    public boolean ownsCoder(Class<?> coderClass, int modelHash) {
        throw new IllegalStateException(makeInvokeMsg());
    }

    @Override
    public Class<?> fetchModelClass(String className) {
        throw new IllegalArgumentException(makeInvokeMsg());
    }

    @Override
    public boolean isBrained() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NullDroolsController []");
        return builder.toString();
    }

    @Override
    public void updateToVersion(String newGroupId, String newArtifactId, String newVersion,
            List<TopicCoderFilterConfiguration> decoderConfigurations,
            List<TopicCoderFilterConfiguration> encoderConfigurations)
                    throws LinkageError {
        throw new IllegalArgumentException(makeInvokeMsg());
    }

    @Override
    public Map<String, Integer> factClassNames(String sessionName) {
        return new HashMap<>();
    }

    @Override
    public long factCount(String sessionName) {
        return 0;
    }

    @Override
    public List<Object> facts(String sessionName, String className, boolean delete) {
        return new ArrayList<>();
    }

    @Override
    public <T> List<T> facts(@NonNull String sessionName, @NonNull Class<T> clazz) {
        return new ArrayList<>();
    }

    @Override
    public List<Object> factQuery(String sessionName, String queryName,
            String queriedEntity,
            boolean delete, Object... queryParams) {
        return new ArrayList<>();
    }

    @Override
    public <T> boolean delete(@NonNull String sessionName, @NonNull T fact) {
        return false;
    }

    @Override
    public <T> boolean delete(@NonNull T fact) {
        return false;
    }

    @Override
    public <T> boolean delete(@NonNull String sessionName, @NonNull Class<T> fact) {
        return false;
    }

    @Override
    public <T> boolean delete(@NonNull Class<T> fact) {
        return false;
    }

    private String makeInvokeMsg() {
        return this.getClass().getName() + " invoked";
    }
}
