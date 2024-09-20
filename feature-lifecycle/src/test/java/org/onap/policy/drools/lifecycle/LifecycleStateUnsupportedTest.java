/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Lifecycle State Unsupported Test.
 */
public abstract class LifecycleStateUnsupportedTest {

    protected final LifecycleState state;

    @BeforeAll
    public static void setUp() {
        SystemPersistenceConstants.getManager().setConfigurationDir("src/test/resources");
    }

    @AfterAll
    public static void tearDown() {
        SystemPersistenceConstants.getManager().setConfigurationDir(null);
    }

    public LifecycleStateUnsupportedTest(LifecycleState state) {
        this.state = state;
    }

    public abstract void create(LifecycleFsm fsm);

    @Test
    public void constructor() {
        assertThatIllegalArgumentException().isThrownBy(() -> create(null));
    }

    @Test
    public void start() {
        assertThatThrownBy(state::start)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void stop() {
        assertThatThrownBy(state::stop)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void shutdown() {
        assertThatThrownBy(state::shutdown)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void isAlive() {
        assertThatThrownBy(state::isAlive)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void status() {
        assertThatThrownBy(state::status)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void update() {
        PdpUpdate update = new PdpUpdate();
        assertThatThrownBy(() -> state.update(update))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void stateChange() {
        PdpStateChange change = new PdpStateChange();
        assertThatThrownBy(() -> state.stateChange(change))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void changeState() {
        LifecycleStateActive active = new LifecycleStateActive(new LifecycleFsm());
        assertThatThrownBy(() -> state.transitionToState(active))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void updatePolicies() {
        var list = new ArrayList<ToscaPolicy>();
        assertThatThrownBy(() -> state.updatePolicies(list))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
