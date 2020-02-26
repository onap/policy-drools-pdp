/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.core;

import java.util.concurrent.Executor;
import org.drools.core.WorkingMemory;

/**
 * An executor that executes its tasks by inserting {@link DroolsRunnable} objects into
 * the working memory of a running session.
 */
public class DroolsExecutor implements Executor {
    private final WorkingMemory workingMemory;

    /**
     * Constructs the object.
     *
     * @param workingMemory where tasks should be injected
     */
    public DroolsExecutor(WorkingMemory workingMemory) {
        this.workingMemory = workingMemory;
    }

    @Override
    public void execute(Runnable command) {
        DroolsRunnable runnable = command::run;
        workingMemory.insert(runnable);
    }
}
