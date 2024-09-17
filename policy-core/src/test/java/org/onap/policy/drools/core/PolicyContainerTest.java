/*-
 * ============LICENSE_START================================================
 * policy-core
 * =========================================================================
 * Copyright (C) 2024 Nordix Foundation.
 * =========================================================================
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
 * ============LICENSE_END==================================================
 */

package org.onap.policy.drools.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.builder.ReleaseId;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyContainerTest {

    private static final String VERSION = "1.0.0";

    @Test
    void adoptKieSession_Exceptions() {
        var mockKieSession = mock(KieSession.class);
        var policyContainer = mock(PolicyContainer.class);

        when(policyContainer.getName()).thenReturn("kieReleaseName");
        when(policyContainer.adoptKieSession(any(), eq(mockKieSession))).thenCallRealMethod();
        when(policyContainer.adoptKieSession("name", null)).thenCallRealMethod();

        assertThatThrownBy(() -> policyContainer.adoptKieSession("", mockKieSession))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("KieSession input name is null kieReleaseName");

        assertThatThrownBy(() -> policyContainer.adoptKieSession(null, mockKieSession))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("KieSession input name is null kieReleaseName");

        assertThatThrownBy(() -> policyContainer.adoptKieSession("name", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("KieSession 'name' is null kieReleaseName");
    }

    @Test
    void testAdoptKieSession() {
        var mockKieSession = mock(KieSession.class);
        doNothing().when(mockKieSession).addEventListener(any(AgendaEventListener.class));
        doNothing().when(mockKieSession).addEventListener(any(RuleRuntimeEventListener.class));

        var policyContainer = mock(PolicyContainer.class);
        when(policyContainer.adoptKieSession("name", mockKieSession)).thenCallRealMethod();
        when(policyContainer.getName()).thenReturn("kieReleaseName");

        var mockKieBase = mock(KieBase.class);
        when(mockKieSession.getKieBase()).thenReturn(mockKieBase);

        var mockKieContainer = mock(KieContainer.class);
        when(policyContainer.getKieContainer()).thenReturn(mockKieContainer);
        when(mockKieContainer.getKieBase("baseName")).thenReturn(mockKieBase);
        when(mockKieContainer.getKieBaseNames()).thenReturn(List.of("baseName"));
        when(mockKieContainer.getKieBase()).thenReturn(mockKieBase);

        HashMap<String, PolicySession> sessions = new HashMap<>();
        ReflectionTestUtils.setField(policyContainer, "sessions", sessions);
        ReflectionTestUtils.setField(policyContainer, "kieContainer", mockKieContainer);

        assertNotNull(policyContainer.adoptKieSession("name", mockKieSession));
        assertThatThrownBy(() -> policyContainer.adoptKieSession("name", mockKieSession))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PolicySession 'name' already exists");
    }

    @Test
    void testAdoptKieSession_KieBaseDoesntMatch() {
        var mockKieSession = mock(KieSession.class);

        var policyContainer = mock(PolicyContainer.class);
        when(policyContainer.adoptKieSession("name", mockKieSession)).thenCallRealMethod();
        when(policyContainer.getName()).thenReturn("kieReleaseName");

        var mockKieBase = mock(KieBase.class);
        when(mockKieSession.getKieBase()).thenReturn(mockKieBase);
        var mockKieBase2 = mock(KieBase.class);

        var mockKieContainer = mock(KieContainer.class);
        when(policyContainer.getKieContainer()).thenReturn(mockKieContainer);
        when(mockKieContainer.getKieBase("baseName")).thenReturn(mockKieBase2);
        when(mockKieContainer.getKieBaseNames()).thenReturn(List.of("baseName"));
        when(mockKieContainer.getKieBase()).thenReturn(mockKieBase2);

        ReflectionTestUtils.setField(policyContainer, "kieContainer", mockKieContainer);

        assertThatThrownBy(() -> policyContainer.adoptKieSession("name", mockKieSession))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("KieSession 'name' does not reside within container kieReleaseName");
    }

    @Test
    void startScanner_Exceptions() {
        var policyContainer = mock(PolicyContainer.class);
        doCallRealMethod().when(policyContainer).startScanner(any(ReleaseId.class));
        doCallRealMethod().when(policyContainer).startScanner(isNull());
        when(policyContainer.isScannerStarted()).thenCallRealMethod();

        assertThatThrownBy(() -> policyContainer.startScanner(null))
            .hasMessageContaining("releaseId is marked non-null but is null");
        assertFalse(policyContainer.isScannerStarted());

        // shouldn't throw exception, but won't start scanner as version is null
        var mockVersionNull = mock(ReleaseId.class);
        when(mockVersionNull.getVersion()).thenReturn(null);
        when(policyContainer.isValidVersion(isNull())).thenCallRealMethod();
        assertDoesNotThrow(() -> policyContainer.startScanner(mockVersionNull));
        assertFalse(policyContainer.isScannerStarted());

        var mockVersionSnapshot = mock(ReleaseId.class);
        when(mockVersionSnapshot.getVersion()).thenReturn(VERSION);
        when(policyContainer.isValidVersion(VERSION)).thenCallRealMethod();
        assertDoesNotThrow(() -> policyContainer.startScanner(mockVersionSnapshot));
        assertFalse(policyContainer.isScannerStarted());
    }

    @Test
    void startScanner_SnapshotVersion() {
        var policyContainer = mock(PolicyContainer.class);
        when(policyContainer.isScannerStarted()).thenCallRealMethod();
        when(policyContainer.isValidVersion(VERSION + "-SNAPSHOT")).thenCallRealMethod();

        var mockVersionSnapshot = mock(ReleaseId.class);
        when(mockVersionSnapshot.getVersion()).thenReturn(VERSION + "-SNAPSHOT");

        doCallRealMethod().when(policyContainer).startScanner(mockVersionSnapshot);

        assertDoesNotThrow(() -> policyContainer.startScanner(mockVersionSnapshot));
        assertTrue(policyContainer.isScannerStarted());
    }

    @Test
    void startScanner_LatestVersion() {
        var policyContainer = mock(PolicyContainer.class);
        when(policyContainer.isScannerStarted()).thenCallRealMethod();
        when(policyContainer.isValidVersion(anyString())).thenCallRealMethod();

        var mockLatestVersion = mock(ReleaseId.class);
        when(mockLatestVersion.getVersion()).thenReturn(VERSION + "LATEST");

        doCallRealMethod().when(policyContainer).startScanner(mockLatestVersion);

        assertDoesNotThrow(() -> policyContainer.startScanner(mockLatestVersion));
        assertTrue(policyContainer.isScannerStarted());
    }

    @Test
    void startScanner_ReleaseVersion() {
        var mockKieServices = mock(KieServices.class);
        when(mockKieServices.newKieScanner(any(KieContainer.class))).thenReturn(mock(KieScanner.class));

        try (MockedStatic<KieServices.Factory> factory = Mockito.mockStatic(KieServices.Factory.class)) {
            factory.when(KieServices.Factory::get).thenReturn(mockKieServices);
            assertEquals(mockKieServices, KieServices.Factory.get());

            var policyContainer = mock(PolicyContainer.class);
            when(policyContainer.isScannerStarted()).thenCallRealMethod();
            when(policyContainer.isValidVersion(VERSION + "RELEASE")).thenCallRealMethod();

            var mockLatestVersion = mock(ReleaseId.class);
            when(mockLatestVersion.getVersion()).thenReturn(VERSION + "RELEASE");

            doCallRealMethod().when(policyContainer).startScanner(mockLatestVersion);

            assertDoesNotThrow(() -> policyContainer.startScanner(mockLatestVersion));
            assertTrue(policyContainer.isScannerStarted());

            // try again, but should come out at checking if scanner is already started.
            assertDoesNotThrow(() -> policyContainer.startScanner(mockLatestVersion));
        }
    }

    @Test
    void insert() {
        var policyContainer = mock(PolicyContainer.class);
        var object = new Object();
        when(policyContainer.insert("name", object)).thenCallRealMethod();

        HashMap<String, PolicySession> sessions = new HashMap<>();
        ReflectionTestUtils.setField(policyContainer, "sessions", sessions);

        assertFalse(policyContainer.insert("name", object));
    }

    @Test
    void deactivate() {
        assertDoesNotThrow(PolicyContainer::deactivate);
    }
}