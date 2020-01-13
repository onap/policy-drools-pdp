/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Query;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.utils.services.OrderedServiceImpl;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.features.DroolsControllerFeatureApi;
import org.onap.policy.drools.features.DroolsControllerFeatureApiConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.coders.EventProtocolCoderConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolParams;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.PotentialCoderFilter;

public class MavenDroolsController2Test {
    private static final int FACT1_OBJECT = 1000;
    private static final int FACT3_OBJECT = 1001;

    private static final long FACT_COUNT = 200L;

    private static final String EXPECTED_EXCEPTION = "expected exception";
    private static final RuntimeException RUNTIME_EX = new RuntimeException(EXPECTED_EXCEPTION);
    private static final IllegalArgumentException ARG_EX = new IllegalArgumentException(EXPECTED_EXCEPTION);

    private static final String UNKNOWN_CLASS = "unknown class";

    private static final String GROUP = "my-group";
    private static final String ARTIFACT = "my-artifact";
    private static final String VERSION = "my-version";

    private static final String GROUP2 = "my-groupB";
    private static final String ARTIFACT2 = "my-artifactB";
    private static final String VERSION2 = "my-versionB";

    private static final String TOPIC = "my-topic";
    private static final String TOPIC2 = "my-topic";

    private static final ClassLoader CLASS_LOADER = MavenDroolsController2Test.class.getClassLoader();
    private static final int CLASS_LOADER_HASHCODE = CLASS_LOADER.hashCode();

    private static final String SESSION1 = "session-A";
    private static final String SESSION2 = "session-B";
    private static final String FULL_SESSION1 = "full-A";
    private static final String FULL_SESSION2 = "full-B";

    private static final String EVENT_TEXT = "my-event-text";
    private static final Object EVENT = new Object();

    private static final String QUERY = "my-query";
    private static final String QUERY2 = "my-query-B";
    private static final String ENTITY = "my-entity";
    private static final Object PARM1 = "parmA";
    private static final Object PARM2 = "parmB";

    @Mock
    private EventProtocolCoder coderMgr;
    @Mock
    private DroolsControllerFeatureApi prov1;
    @Mock
    private DroolsControllerFeatureApi prov2;
    @Mock
    private OrderedServiceImpl<DroolsControllerFeatureApi> droolsProviders;
    @Mock
    private TopicCoderFilterConfiguration decoder1;
    @Mock
    private TopicCoderFilterConfiguration decoder2;
    @Mock
    private TopicCoderFilterConfiguration encoder1;
    @Mock
    private TopicCoderFilterConfiguration encoder2;
    @Mock
    private PolicyContainer container;
    @Mock
    private CustomGsonCoder gson1;
    @Mock
    private CustomGsonCoder gson2;
    @Mock
    private PotentialCoderFilter filter1a;
    @Mock
    private JsonProtocolFilter jsonFilter1a;
    @Mock
    private PotentialCoderFilter filter1b;
    @Mock
    private JsonProtocolFilter jsonFilter1b;
    @Mock
    private PotentialCoderFilter filter2;
    @Mock
    private JsonProtocolFilter jsonFilter2;
    @Mock
    private PolicySession sess1;
    @Mock
    private PolicySession sess2;
    @Mock
    private KieSession kieSess;
    @Mock
    private KieSession kieSess2;
    @Mock
    private TopicSink sink;
    @Mock
    private FactHandle fact1;
    @Mock
    private FactHandle fact2;
    @Mock
    private FactHandle fact3;
    @Mock
    private FactHandle factex;
    @Mock
    private KieBase kieBase;
    @Mock
    private KiePackage pkg1;
    @Mock
    private KiePackage pkg2;
    @Mock
    private Query query1;
    @Mock
    private Query query2;
    @Mock
    private Query query3;
    @Mock
    private QueryResults queryResults;
    @Mock
    private QueryResultsRow row1;
    @Mock
    private QueryResultsRow row2;

    private List<TopicCoderFilterConfiguration> decoders;
    private List<TopicCoderFilterConfiguration> encoders;

    private MavenDroolsController drools;

    /**
     * Initializes objects, including the drools controller.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(droolsProviders.getList()).thenReturn(Arrays.asList(prov1, prov2));

        when(coderMgr.isDecodingSupported(GROUP, ARTIFACT, TOPIC)).thenReturn(true);
        when(coderMgr.decode(GROUP, ARTIFACT, TOPIC, EVENT_TEXT)).thenReturn(EVENT);

        when(kieSess.getFactCount()).thenReturn(FACT_COUNT);
        when(kieSess.getFactHandles()).thenReturn(Arrays.asList(fact1, fact2, factex, fact3));
        when(kieSess.getFactHandles(any())).thenReturn(Arrays.asList(fact1, fact3));
        when(kieSess.getKieBase()).thenReturn(kieBase);
        when(kieSess.getQueryResults(QUERY, PARM1, PARM2)).thenReturn(queryResults);

        when(kieSess.getObject(fact1)).thenReturn(FACT1_OBJECT);
        when(kieSess.getObject(fact2)).thenReturn("");
        when(kieSess.getObject(fact3)).thenReturn(FACT3_OBJECT);
        when(kieSess.getObject(factex)).thenThrow(RUNTIME_EX);

        when(kieSess2.getFactHandles()).thenReturn(Collections.emptyList());

        when(kieBase.getKiePackages()).thenReturn(Arrays.asList(pkg1, pkg2));

        when(pkg1.getQueries()).thenReturn(Arrays.asList(query3));
        when(pkg2.getQueries()).thenReturn(Arrays.asList(query2, query1));

        when(query1.getName()).thenReturn(QUERY);
        when(query2.getName()).thenReturn(QUERY2);

        when(queryResults.iterator()).thenReturn(Arrays.asList(row1, row2).iterator());

        when(row1.get(ENTITY)).thenReturn(FACT1_OBJECT);
        when(row2.get(ENTITY)).thenReturn(FACT3_OBJECT);

        when(row1.getFactHandle(ENTITY)).thenReturn(fact1);
        when(row2.getFactHandle(ENTITY)).thenReturn(fact3);

        when(sess1.getKieSession()).thenReturn(kieSess);
        when(sess2.getKieSession()).thenReturn(kieSess2);

        when(sess1.getName()).thenReturn(SESSION1);
        when(sess2.getName()).thenReturn(SESSION2);

        when(sess1.getFullName()).thenReturn(FULL_SESSION1);
        when(sess2.getFullName()).thenReturn(FULL_SESSION2);

        when(container.getClassLoader()).thenReturn(CLASS_LOADER);
        when(container.getPolicySessions()).thenReturn(Arrays.asList(sess1, sess2));
        when(container.insertAll(EVENT)).thenReturn(true);

        when(decoder1.getTopic()).thenReturn(TOPIC);
        when(decoder2.getTopic()).thenReturn(TOPIC2);

        when(encoder1.getTopic()).thenReturn(TOPIC);
        when(encoder2.getTopic()).thenReturn(TOPIC2);

        decoders = Arrays.asList(decoder1, decoder2);
        encoders = Arrays.asList(encoder1, encoder2);

        when(decoder1.getCustomGsonCoder()).thenReturn(gson1);
        when(encoder2.getCustomGsonCoder()).thenReturn(gson2);

        when(filter1a.getCodedClass()).thenReturn(Object.class.getName());
        when(filter1a.getFilter()).thenReturn(jsonFilter1a);

        when(filter1b.getCodedClass()).thenReturn(String.class.getName());
        when(filter1b.getFilter()).thenReturn(jsonFilter1b);

        when(filter2.getCodedClass()).thenReturn(Integer.class.getName());
        when(filter2.getFilter()).thenReturn(jsonFilter2);

        when(decoder1.getCoderFilters()).thenReturn(Arrays.asList(filter1a, filter1b));
        when(decoder2.getCoderFilters()).thenReturn(Collections.emptyList());

        when(encoder1.getCoderFilters()).thenReturn(Collections.emptyList());
        when(encoder2.getCoderFilters()).thenReturn(Arrays.asList(filter2));

        when(sink.getTopic()).thenReturn(TOPIC);
        when(sink.send(EVENT_TEXT)).thenReturn(true);

        drools = new MyDrools(GROUP, ARTIFACT, VERSION, null, null);

        when(coderMgr.encode(TOPIC, EVENT, drools)).thenReturn(EVENT_TEXT);
    }

    @Test
    public void testMavenDroolsController_InvalidArgs() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MyDrools(null, ARTIFACT, VERSION, null, null))
                        .withMessageContaining("group");
        assertThatIllegalArgumentException().isThrownBy(() -> new MyDrools("", ARTIFACT, VERSION, null, null))
                        .withMessageContaining("group");

        assertThatIllegalArgumentException().isThrownBy(() -> new MyDrools(GROUP, null, VERSION, null, null))
                        .withMessageContaining("artifact");
        assertThatIllegalArgumentException().isThrownBy(() -> new MyDrools(GROUP, "", VERSION, null, null))
                        .withMessageContaining("artifact");

        assertThatIllegalArgumentException().isThrownBy(() -> new MyDrools(GROUP, ARTIFACT, null, null, null))
                        .withMessageContaining("version");
        assertThatIllegalArgumentException().isThrownBy(() -> new MyDrools(GROUP, ARTIFACT, "", null, null))
                        .withMessageContaining("version");
    }

    @Test
    public void testUpdateToVersion() {
        // add coders
        drools.updateToVersion(GROUP, ARTIFACT, VERSION2, decoders, encoders);

        verify(container).updateToVersion(VERSION2);

        // nothing removed the first time
        verify(coderMgr, never()).removeDecoders(GROUP, ARTIFACT, TOPIC2);
        verify(coderMgr, never()).removeEncoders(GROUP, ARTIFACT, TOPIC);

        verify(coderMgr, times(2)).addDecoder(any());
        verify(coderMgr, times(1)).addEncoder(any());

        // remove coders
        when(container.getVersion()).thenReturn(VERSION2);
        drools.updateToVersion(GROUP, ARTIFACT, VERSION, null, null);

        verify(container).updateToVersion(VERSION);

        verify(coderMgr, times(2)).removeDecoders(GROUP, ARTIFACT, TOPIC2);
        verify(coderMgr, times(2)).removeEncoders(GROUP, ARTIFACT, TOPIC);

        // not added again
        verify(coderMgr, times(2)).addDecoder(any());
        verify(coderMgr, times(1)).addEncoder(any());
    }

    @Test
    public void testUpdateToVersion_Unchanged() {
        drools.updateToVersion(GROUP, ARTIFACT, VERSION, decoders, encoders);

        verify(coderMgr, never()).addDecoder(any());
        verify(coderMgr, never()).addEncoder(any());
    }

    @Test
    public void testUpdateToVersion_InvalidArgs() {
        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.updateToVersion(null, ARTIFACT, VERSION, null, null))
                        .withMessageContaining("group");
        assertThatIllegalArgumentException().isThrownBy(() -> drools.updateToVersion("", ARTIFACT, VERSION, null, null))
                        .withMessageContaining("group");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.updateToVersion(GROUP, null, VERSION, null, null))
                        .withMessageContaining("artifact");
        assertThatIllegalArgumentException().isThrownBy(() -> drools.updateToVersion(GROUP, "", VERSION, null, null))
                        .withMessageContaining("artifact");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.updateToVersion(GROUP, ARTIFACT, null, null, null))
                        .withMessageContaining("version");
        assertThatIllegalArgumentException().isThrownBy(() -> drools.updateToVersion(GROUP, ARTIFACT, "", null, null))
                        .withMessageContaining("version");

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.updateToVersion("no-group-id", ARTIFACT, VERSION, null, null))
                        .withMessageContaining("BRAINLESS");

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.updateToVersion(GROUP, "no-artifact-id", VERSION, null, null))
                        .withMessageContaining("BRAINLESS");

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.updateToVersion(GROUP, ARTIFACT, "no-version", null, null))
                        .withMessageContaining("BRAINLESS");

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.updateToVersion(GROUP2, ARTIFACT, VERSION, null, null))
                        .withMessageContaining("coordinates must be identical");

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.updateToVersion(GROUP, ARTIFACT2, VERSION, null, null))
                        .withMessageContaining("coordinates must be identical");
    }

    @Test
    public void testInitCoders_NullCoders() {
        // already constructed with null coders
        verify(coderMgr, never()).addDecoder(any());
        verify(coderMgr, never()).addEncoder(any());
    }

    @Test
    public void testInitCoders_NullOrEmptyFilters() {
        when(decoder1.getCoderFilters()).thenReturn(Collections.emptyList());
        when(decoder2.getCoderFilters()).thenReturn(null);

        when(encoder1.getCoderFilters()).thenReturn(null);
        when(encoder2.getCoderFilters()).thenReturn(Collections.emptyList());

        drools = new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders);

        verify(coderMgr, never()).addDecoder(any());
        verify(coderMgr, never()).addEncoder(any());
    }

    @Test
    public void testInitCoders_GsonClass() {
        when(gson1.getClassContainer()).thenReturn("");
        when(gson2.getClassContainer()).thenReturn(Long.class.getName());

        drools = new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders);

        // all should be added
        verify(coderMgr, times(2)).addDecoder(any());
        verify(coderMgr, times(1)).addEncoder(any());
    }

    @Test
    public void testInitCoders_InvalidGsonClass() {
        when(gson1.getClassContainer()).thenReturn(UNKNOWN_CLASS);

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders))
                        .withMessageContaining("cannot be retrieved");
    }

    @Test
    public void testInitCoders_InvalidFilterClass() {
        when(filter2.getCodedClass()).thenReturn(UNKNOWN_CLASS);

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders))
                        .withMessageContaining("cannot be retrieved");
    }

    @Test
    public void testInitCoders_Filters() {

        drools = new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders);

        ArgumentCaptor<EventProtocolParams> dec = ArgumentCaptor.forClass(EventProtocolParams.class);
        verify(coderMgr, times(2)).addDecoder(dec.capture());

        ArgumentCaptor<EventProtocolParams> enc = ArgumentCaptor.forClass(EventProtocolParams.class);
        verify(coderMgr, times(1)).addEncoder(enc.capture());

        // validate parameters
        EventProtocolParams params = dec.getAllValues().get(0);
        assertEquals(ARTIFACT, params.getArtifactId());
        assertEquals(gson1, params.getCustomCoder());
        assertEquals(Object.class.getName(), params.getEventClass());
        assertEquals(GROUP, params.getGroupId());
        assertEquals(CLASS_LOADER_HASHCODE, params.getModelClassLoaderHash());
        assertEquals(jsonFilter1a, params.getProtocolFilter());
        assertEquals(TOPIC, params.getTopic());

        params = dec.getAllValues().get(1);
        assertEquals(ARTIFACT, params.getArtifactId());
        assertEquals(gson1, params.getCustomCoder());
        assertEquals(String.class.getName(), params.getEventClass());
        assertEquals(GROUP, params.getGroupId());
        assertEquals(CLASS_LOADER_HASHCODE, params.getModelClassLoaderHash());
        assertEquals(jsonFilter1b, params.getProtocolFilter());
        assertEquals(TOPIC, params.getTopic());

        params = enc.getAllValues().get(0);
        assertEquals(ARTIFACT, params.getArtifactId());
        assertEquals(gson2, params.getCustomCoder());
        assertEquals(Integer.class.getName(), params.getEventClass());
        assertEquals(GROUP, params.getGroupId());
        assertEquals(CLASS_LOADER_HASHCODE, params.getModelClassLoaderHash());
        assertEquals(jsonFilter2, params.getProtocolFilter());
        assertEquals(TOPIC, params.getTopic());
    }

    @Test
    public void testOwnsCoder() {
        int hc = CLASS_LOADER_HASHCODE;

        // wrong hash code
        assertFalse(drools.ownsCoder(String.class, hc + 1));

        // correct hash code
        assertTrue(drools.ownsCoder(String.class, hc));

        // unknown class
        drools = new MyDrools(GROUP, ARTIFACT, VERSION, null, null) {
            @Override
            protected boolean isClass(String className) {
                return false;
            }
        };
        assertFalse(drools.ownsCoder(String.class, hc));
    }

    @Test
    public void testStart_testStop_testIsAlive() {
        drools = new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders);

        when(container.start()).thenReturn(true);
        when(container.stop()).thenReturn(true);

        assertFalse(drools.isAlive());

        // start it
        assertTrue(drools.start());
        verify(container).start();
        assertTrue(drools.isAlive());

        // repeat - no changes
        assertTrue(drools.start());
        verify(container).start();
        assertTrue(drools.isAlive());

        // stop it
        assertTrue(drools.stop());
        verify(container).stop();
        assertFalse(drools.isAlive());

        // repeat - no changes
        assertTrue(drools.stop());
        verify(container).stop();
        assertFalse(drools.isAlive());

        // now check with container returning false - should still be invoked
        when(container.start()).thenReturn(false);
        when(container.stop()).thenReturn(false);
        assertFalse(drools.start());
        assertTrue(drools.isAlive());
        assertFalse(drools.stop());
        assertFalse(drools.isAlive());
        verify(container, times(2)).start();
        verify(container, times(2)).stop();

        // coders should still be intact
        verify(coderMgr, never()).removeDecoders(any(), any(), any());
        verify(coderMgr, never()).removeEncoders(any(), any(), any());

        verify(container, never()).shutdown();
        verify(container, never()).destroy();
    }

    @Test
    public void testShutdown() {
        drools = new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders);

        // start it
        drools.start();

        // shut down
        drools.shutdown();

        verify(container).stop();
        assertFalse(drools.isAlive());

        // coders should have been removed
        verify(coderMgr, times(2)).removeDecoders(any(), any(), any());
        verify(coderMgr, times(2)).removeEncoders(any(), any(), any());

        verify(container).shutdown();
        verify(container, never()).destroy();
    }

    @Test
    public void testShutdown_Ex() {
        drools = new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders);

        // start it
        drools.start();

        when(container.stop()).thenThrow(RUNTIME_EX);

        // shut down
        drools.shutdown();

        assertFalse(drools.isAlive());

        verify(container).shutdown();
        verify(container, never()).destroy();
    }

    @Test
    public void testHalt() {
        drools = new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders);

        // start it
        drools.start();

        // halt
        drools.halt();

        verify(container).stop();
        assertFalse(drools.isAlive());

        // coders should have been removed
        verify(coderMgr, times(2)).removeDecoders(any(), any(), any());
        verify(coderMgr, times(2)).removeEncoders(any(), any(), any());

        verify(container).destroy();
    }

    @Test
    public void testHalt_Ex() {
        drools = new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders);

        // start it
        drools.start();

        when(container.stop()).thenThrow(RUNTIME_EX);

        // halt
        drools.halt();

        assertFalse(drools.isAlive());

        verify(container).destroy();
    }

    @Test
    public void testRemoveCoders_Ex() {
        drools = new MyDrools(GROUP, ARTIFACT, VERSION, decoders, encoders) {
            @Override
            protected void removeDecoders() {
                throw ARG_EX;
            }

            @Override
            protected void removeEncoders() {
                throw ARG_EX;
            }
        };

        drools.updateToVersion(GROUP, ARTIFACT, VERSION2, null, null);
    }

    @Test
    public void testOfferStringString() {
        drools.start();
        assertTrue(drools.offer(TOPIC, EVENT_TEXT));

        verify(container).insertAll(EVENT);
    }

    @Test
    public void testOfferStringString_NoDecode() {
        when(coderMgr.isDecodingSupported(GROUP, ARTIFACT, TOPIC)).thenReturn(false);

        drools.start();
        assertTrue(drools.offer(TOPIC, EVENT_TEXT));

        verify(container, never()).insertAll(EVENT);
    }

    @Test
    public void testOfferStringString_DecodeUnsupported() {
        when(coderMgr.decode(GROUP, ARTIFACT, TOPIC, EVENT_TEXT))
                        .thenThrow(new UnsupportedOperationException(EXPECTED_EXCEPTION));

        drools.start();
        assertTrue(drools.offer(TOPIC, EVENT_TEXT));

        verify(container, never()).insertAll(EVENT);
    }

    @Test
    public void testOfferStringString_DecodeEx() {
        when(coderMgr.decode(GROUP, ARTIFACT, TOPIC, EVENT_TEXT)).thenThrow(RUNTIME_EX);

        drools.start();
        assertTrue(drools.offer(TOPIC, EVENT_TEXT));

        verify(container, never()).insertAll(EVENT);
    }

    @Test
    public void testOfferStringString_Ignored() {
        drools.start();

        drools.lock();
        assertTrue(drools.offer(TOPIC, EVENT_TEXT));
        assertEquals(0, drools.getRecentSourceEvents().length);
        drools.unlock();

        drools.stop();
        assertTrue(drools.offer(TOPIC, EVENT_TEXT));
        assertEquals(0, drools.getRecentSourceEvents().length);
        drools.start();

        // no sessions
        when(container.getPolicySessions()).thenReturn(Collections.emptyList());
        assertTrue(drools.offer(TOPIC, EVENT_TEXT));
        assertEquals(0, drools.getRecentSourceEvents().length);
    }

    @Test
    public void testOfferT() {
        drools.start();
        assertTrue(drools.offer(EVENT));
        assertEquals(1, drools.getRecentSourceEvents().length);
        assertEquals(EVENT, drools.getRecentSourceEvents()[0]);
        verify(container).insertAll(EVENT);

        verify(prov1).beforeInsert(drools, EVENT);
        verify(prov2).beforeInsert(drools, EVENT);

        verify(prov1).afterInsert(drools, EVENT, true);
        verify(prov2).afterInsert(drools, EVENT, true);
    }

    @Test
    public void testOfferT_Ex() {
        when(prov1.beforeInsert(drools, EVENT)).thenThrow(RUNTIME_EX);
        when(prov1.afterInsert(drools, EVENT, true)).thenThrow(RUNTIME_EX);

        drools.start();
        assertTrue(drools.offer(EVENT));
        assertEquals(1, drools.getRecentSourceEvents().length);
        assertEquals(EVENT, drools.getRecentSourceEvents()[0]);
        verify(container).insertAll(EVENT);

        // should still invoke prov2
        verify(prov2).beforeInsert(drools, EVENT);

        verify(prov2).afterInsert(drools, EVENT, true);
    }

    @Test
    public void testOfferT_NotInserted() {
        when(container.insertAll(EVENT)).thenReturn(false);

        drools.start();
        assertTrue(drools.offer(EVENT));
        assertEquals(1, drools.getRecentSourceEvents().length);
        assertEquals(EVENT, drools.getRecentSourceEvents()[0]);
        verify(container).insertAll(EVENT);

        verify(prov1).beforeInsert(drools, EVENT);
        verify(prov2).beforeInsert(drools, EVENT);

        verify(prov1).afterInsert(drools, EVENT, false);
        verify(prov2).afterInsert(drools, EVENT, false);
    }

    @Test
    public void testOfferT_BeforeInsertIntercept() {
        drools.start();
        when(prov1.beforeInsert(drools, EVENT)).thenReturn(true);

        assertTrue(drools.offer(EVENT));
        assertEquals(1, drools.getRecentSourceEvents().length);
        assertEquals(EVENT, drools.getRecentSourceEvents()[0]);
        verify(container, never()).insertAll(EVENT);

        verify(prov1).beforeInsert(drools, EVENT);

        // nothing else invoked
        verify(prov2, never()).beforeInsert(drools, EVENT);
        verify(prov1, never()).afterInsert(drools, EVENT, true);
        verify(prov2, never()).afterInsert(drools, EVENT, true);
    }

    @Test
    public void testOfferT_AfterInsertIntercept() {
        drools.start();

        when(prov1.afterInsert(drools, EVENT, true)).thenReturn(true);

        assertTrue(drools.offer(EVENT));
        assertEquals(1, drools.getRecentSourceEvents().length);
        assertEquals(EVENT, drools.getRecentSourceEvents()[0]);
        verify(container).insertAll(EVENT);

        verify(prov1).beforeInsert(drools, EVENT);
        verify(prov2).beforeInsert(drools, EVENT);

        verify(prov1).afterInsert(drools, EVENT, true);

        // prov2 is never called
        verify(prov2, never()).afterInsert(drools, EVENT, true);
    }

    @Test
    public void testOfferT_Ignored() {
        drools.start();

        drools.lock();
        assertTrue(drools.offer(EVENT));
        assertEquals(0, drools.getRecentSourceEvents().length);
        drools.unlock();

        drools.stop();
        assertTrue(drools.offer(EVENT));
        assertEquals(0, drools.getRecentSourceEvents().length);
        drools.start();

        // no sessions
        when(container.getPolicySessions()).thenReturn(Collections.emptyList());
        assertTrue(drools.offer(EVENT));
        assertEquals(0, drools.getRecentSourceEvents().length);
    }

    @Test
    public void testDeliver() {
        drools.start();
        assertTrue(drools.deliver(sink, EVENT));
        assertEquals(1, drools.getRecentSinkEvents().length);
        assertEquals(EVENT_TEXT, drools.getRecentSinkEvents()[0]);

        verify(sink).send(EVENT_TEXT);

        verify(prov1).beforeDeliver(drools, sink, EVENT);
        verify(prov2).beforeDeliver(drools, sink, EVENT);

        verify(prov1).afterDeliver(drools, sink, EVENT, EVENT_TEXT, true);
        verify(prov2).afterDeliver(drools, sink, EVENT, EVENT_TEXT, true);
    }

    @Test
    public void testDeliver_InvalidArgs() {
        drools.start();

        assertThatIllegalArgumentException().isThrownBy(() -> drools.deliver(null, EVENT))
                        .withMessageContaining("sink");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.deliver(sink, null))
                        .withMessageContaining("event");

        drools.lock();
        assertThatIllegalStateException().isThrownBy(() -> drools.deliver(sink, EVENT)).withMessageContaining("locked");
        drools.unlock();

        drools.stop();
        assertThatIllegalStateException().isThrownBy(() -> drools.deliver(sink, EVENT))
                        .withMessageContaining("stopped");
        drools.start();

        assertEquals(0, drools.getRecentSinkEvents().length);
    }

    @Test
    public void testDeliver_BeforeIntercept() {
        when(prov1.beforeDeliver(drools, sink, EVENT)).thenReturn(true);

        drools.start();
        assertTrue(drools.deliver(sink, EVENT));
        assertEquals(0, drools.getRecentSinkEvents().length);

        verify(prov1).beforeDeliver(drools, sink, EVENT);

        // nothing else should have been invoked
        verify(sink, never()).send(EVENT_TEXT);
        verify(prov2, never()).beforeDeliver(drools, sink, EVENT);
        verify(prov1, never()).afterDeliver(drools, sink, EVENT, EVENT_TEXT, true);
        verify(prov2, never()).afterDeliver(drools, sink, EVENT, EVENT_TEXT, true);
    }

    @Test
    public void testDeliver_AfterIntercept() {
        when(prov1.afterDeliver(drools, sink, EVENT, EVENT_TEXT, true)).thenReturn(true);

        drools.start();
        assertTrue(drools.deliver(sink, EVENT));
        assertEquals(1, drools.getRecentSinkEvents().length);
        assertEquals(EVENT_TEXT, drools.getRecentSinkEvents()[0]);

        verify(prov1).beforeDeliver(drools, sink, EVENT);
        verify(prov2).beforeDeliver(drools, sink, EVENT);

        verify(sink).send(EVENT_TEXT);

        verify(prov1).afterDeliver(drools, sink, EVENT, EVENT_TEXT, true);

        // nothing else should have been invoked
        verify(prov2, never()).afterDeliver(drools, sink, EVENT, EVENT_TEXT, true);
    }

    @Test
    public void testDeliver_InterceptEx() {
        when(prov1.beforeDeliver(drools, sink, EVENT)).thenThrow(RUNTIME_EX);
        when(prov1.afterDeliver(drools, sink, EVENT, EVENT_TEXT, true)).thenThrow(RUNTIME_EX);

        drools.start();
        assertTrue(drools.deliver(sink, EVENT));

        verify(sink).send(EVENT_TEXT);

        // should still invoke prov2
        verify(prov2).beforeDeliver(drools, sink, EVENT);
        verify(prov2).afterDeliver(drools, sink, EVENT, EVENT_TEXT, true);
    }

    @Test
    public void testGetXxx() {
        assertEquals(VERSION, drools.getVersion());
        assertEquals(ARTIFACT, drools.getArtifactId());
        assertEquals(GROUP, drools.getGroupId());
        assertEquals(CLASS_LOADER_HASHCODE, drools.getModelClassLoaderHash());
        assertSame(container, drools.getContainer());
        assertEquals(Arrays.asList(sess1, sess2), drools.getSessions());

        // test junit methods - need a controller with fewer overrides
        drools = new MavenDroolsController(GROUP, ARTIFACT, VERSION, null, null) {
            @Override
            protected PolicyContainer makePolicyContainer(String groupId, String artifactId, String version) {
                return container;
            }
        };

        assertSame(EventProtocolCoderConstants.getManager(), drools.getCoderManager());
        assertSame(DroolsControllerFeatureApiConstants.getProviders(), drools.getDroolsProviders());
    }

    @Test
    public void testLock_testUnlock_testIsLocked() {
        assertFalse(drools.isLocked());

        assertTrue(drools.lock());
        assertTrue(drools.isLocked());

        assertTrue(drools.unlock());
        assertFalse(drools.isLocked());

        // repeat
        assertTrue(drools.lock());
        assertTrue(drools.isLocked());

        assertTrue(drools.unlock());
        assertFalse(drools.isLocked());
    }

    @Test
    public void testGetSessionNames_testGetCanonicalSessionNames() {
        assertEquals("[session-A, session-B]", drools.getSessionNames(true).toString());
        assertEquals("[full-A, full-B]", drools.getSessionNames(false).toString());

        assertEquals("[session-A, session-B]", drools.getSessionNames().toString());

        assertEquals("[full-A, full-B]", drools.getCanonicalSessionNames().toString());

        // exception case
        when(container.getPolicySessions()).thenThrow(RUNTIME_EX);
        assertEquals("[expected exception]", drools.getSessionNames().toString());
    }

    @Test
    public void testGetBaseDomainNames() {
        KieContainer kiecont = mock(KieContainer.class);
        when(kiecont.getKieBaseNames()).thenReturn(Arrays.asList("kieA", "kieB"));
        when(container.getKieContainer()).thenReturn(kiecont);

        assertEquals("[kieA, kieB]", drools.getBaseDomainNames().toString());
    }

    @Test
    public void testGetSession() {
        assertThatIllegalArgumentException().isThrownBy(() -> drools.getSession(null))
                        .withMessageContaining("must be provided");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.getSession(""))
                        .withMessageContaining("must be provided");

        assertSame(sess1, drools.getSession(SESSION1));
        assertSame(sess1, drools.getSession(FULL_SESSION1));

        assertSame(sess2, drools.getSession(SESSION2));

        assertThatIllegalArgumentException().isThrownBy(() -> drools.getSession("unknown session"))
                        .withMessageContaining("Invalid Session Name");
    }

    @Test
    public void testFactClassNames() {
        // copy to a sorted map so the order remains unchanged
        Map<String, Integer> map = new TreeMap<>(drools.factClassNames(SESSION1));
        assertEquals("{java.lang.Integer=2, java.lang.String=1}", map.toString());

        assertThatIllegalArgumentException().isThrownBy(() -> drools.factClassNames(null))
                        .withMessageContaining("Invalid Session Name");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.factClassNames(""))
                        .withMessageContaining("Invalid Session Name");
    }

    @Test
    public void testFactCount() {
        assertEquals(FACT_COUNT, drools.factCount(SESSION1));

        assertThatIllegalArgumentException().isThrownBy(() -> drools.factCount(null))
                        .withMessageContaining("Invalid Session Name");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.factCount(""))
                        .withMessageContaining("Invalid Session Name");
    }

    @Test
    public void testFactsStringStringBoolean() {
        assertEquals("[1000, 1001]", drools.facts(SESSION1, Integer.class.getName(), false).toString());
        verify(kieSess, never()).delete(fact1);
        verify(kieSess, never()).delete(fact2);
        verify(kieSess, never()).delete(fact3);
        verify(kieSess, never()).delete(factex);

        // now delete - but should only delete 1 & 3
        assertEquals("[1000, 1001]", drools.facts(SESSION1, Integer.class.getName(), true).toString());
        verify(kieSess).delete(fact1);
        verify(kieSess, never()).delete(fact2);
        verify(kieSess).delete(fact3);
        verify(kieSess, never()).delete(factex);

        assertThatIllegalArgumentException().isThrownBy(() -> drools.facts(null, Integer.class.getName(), false))
                        .withMessageContaining("Invalid Session Name");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.facts("", Integer.class.getName(), false))
                        .withMessageContaining("Invalid Session Name");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.facts(SESSION1, null, false))
                        .withMessageContaining("Invalid Class Name");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.facts(SESSION1, "", false))
                        .withMessageContaining("Invalid Class Name");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.facts(SESSION1, UNKNOWN_CLASS, false))
                        .withMessageContaining("classloader");
    }

    @Test
    public void testFactsStringStringBoolean_DeleteEx() {
        doThrow(RUNTIME_EX).when(kieSess).delete(fact1);

        assertEquals("[1000, 1001]", drools.facts(SESSION1, Integer.class.getName(), true).toString());

        // should still have deleted #3
        verify(kieSess).delete(fact3);
    }

    @Test
    public void testFactsStringClassOfT() {
        assertEquals("[1000, 1001]", drools.facts(SESSION1, Integer.class).toString());
    }

    @Test
    public void testFactQuery() {
        assertEquals("[1000, 1001]", drools.factQuery(SESSION1, QUERY, ENTITY, false, PARM1, PARM2).toString());

        verify(kieSess, never()).delete(fact1);
        verify(kieSess, never()).delete(fact3);

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.factQuery(null, QUERY, ENTITY, false, PARM1, PARM2))
                        .withMessageContaining("Invalid Session Name");

        assertThatIllegalArgumentException().isThrownBy(() -> drools.factQuery("", QUERY, ENTITY, false, PARM1, PARM2))
                        .withMessageContaining("Invalid Session Name");

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.factQuery(SESSION1, null, ENTITY, false, PARM1, PARM2))
                        .withMessageContaining("Invalid Query Name");

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.factQuery(SESSION1, "", ENTITY, false, PARM1, PARM2))
                        .withMessageContaining("Invalid Query Name");

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.factQuery(SESSION1, QUERY, null, false, PARM1, PARM2))
                        .withMessageContaining("Invalid Queried Entity");

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> drools.factQuery(SESSION1, QUERY, "", false, PARM1, PARM2))
                        .withMessageContaining("Invalid Queried Entity");

        assertThatIllegalArgumentException().isThrownBy(
            () -> drools.factQuery(SESSION1, QUERY + "-unknown-query", ENTITY, false, PARM1, PARM2))
            .withMessageContaining("Invalid Query Name");
    }

    @Test
    public void testFactQuery_Delete() {
        doThrow(RUNTIME_EX).when(kieSess).delete(fact1);

        assertEquals("[1000, 1001]", drools.factQuery(SESSION1, QUERY, ENTITY, true, PARM1, PARM2).toString());

        // should still delete fact #3
        verify(kieSess).delete(fact3);
    }

    @Test
    public void testDeleteStringT() {
        assertTrue(drools.delete(SESSION1, FACT3_OBJECT));

        verify(kieSess, never()).delete(fact1);
        verify(kieSess).delete(fact3);

        // not found
        assertFalse(drools.delete(SESSION1, "hello"));

        // repeat, but generate exception while getting the first object
        when(kieSess.getObject(fact1)).thenThrow(RUNTIME_EX);
        assertTrue(drools.delete(SESSION1, FACT3_OBJECT));

        verify(kieSess, never()).delete(fact1);

        // should still delete fact #3
        verify(kieSess, times(2)).delete(fact3);
    }

    @Test
    public void testDeleteT() {
        assertTrue(drools.delete(FACT3_OBJECT));

        verify(kieSess).delete(fact3);
    }

    @Test
    public void testDeleteStringClassOfT() {
        assertTrue(drools.delete(SESSION1, Integer.class));

        verify(kieSess).delete(fact1);
        verify(kieSess).delete(fact3);
    }

    @Test
    public void testDeleteStringClassOfT_Ex() {
        doThrow(RUNTIME_EX).when(kieSess).delete(fact1);

        assertFalse(drools.delete(SESSION1, Integer.class));

        // should still delete fact #3
        verify(kieSess).delete(fact3);
    }

    @Test
    public void testDeleteClassOfT() {
        assertTrue(drools.delete(Integer.class));

        verify(kieSess).delete(fact1);
        verify(kieSess).delete(fact3);
    }

    @Test
    public void testFetchModelClass() {
        assertSame(Long.class, drools.fetchModelClass(Long.class.getName()));
    }

    @Test
    public void testIsBrained() {
        assertTrue(drools.isBrained());
    }

    @Test
    public void testToString() {
        assertNotNull(drools.toString());
    }

    private class MyDrools extends MavenDroolsController {

        public MyDrools(String groupId, String artifactId, String version,
                        List<TopicCoderFilterConfiguration> decoderConfigurations,
                        List<TopicCoderFilterConfiguration> encoderConfigurations) {

            super(groupId, artifactId, version, decoderConfigurations, encoderConfigurations);
        }

        @Override
        protected EventProtocolCoder getCoderManager() {
            return coderMgr;
        }

        @Override
        protected OrderedServiceImpl<DroolsControllerFeatureApi> getDroolsProviders() {
            return droolsProviders;
        }

        @Override
        protected PolicyContainer makePolicyContainer(String groupId, String artifactId, String version) {
            when(container.getGroupId()).thenReturn(groupId);
            when(container.getArtifactId()).thenReturn(artifactId);
            when(container.getVersion()).thenReturn(version);

            return container;
        }
    }
}
