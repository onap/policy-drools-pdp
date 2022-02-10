/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2021-2022 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.protocol.coders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.ReleaseId;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.controller.internal.MavenDroolsControllerTest;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.onap.policy.drools.util.KieUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProtocolCoder Toolset Junits.
 */
public class ProtocolCoderToolsetTest {
    private static final String JUNIT_PROTOCOL_CODER_ARTIFACT_ID = "protocolcoder";
    private static final String JUNIT_PROTOCOL_CODER_TOPIC = JUNIT_PROTOCOL_CODER_ARTIFACT_ID;
    private static final String CONTROLLER_ID = "blah";

    private static final Logger logger = LoggerFactory.getLogger(ProtocolCoderToolset.class);

    private static volatile ReleaseId releaseId;

    // customCoder has to be public to be accessed in tests below
    public static final Gson customCoder = new GsonBuilder().create();

    private DroolsController controller;

    /**
     * Test Class Initialization.
     */
    @BeforeClass
    public static void setUpClass() throws IOException {
        releaseId = KieUtils.installArtifact(Paths.get(MavenDroolsControllerTest.JUNIT_ECHO_KMODULE_PATH).toFile(),
                        Paths.get(MavenDroolsControllerTest.JUNIT_ECHO_KMODULE_POM_PATH).toFile(),
                        MavenDroolsControllerTest.JUNIT_ECHO_KJAR_DRL_PATH,
                        Paths.get(MavenDroolsControllerTest.JUNIT_ECHO_KMODULE_DRL_PATH).toFile());
    }

    /**
     * Test Set Up.
     */
    @Before
    public void setUp() {
        controller = createController();
    }

    /**
     * Test Termination.
     */
    @After
    public void tearDown() {
        if (controller != null) {
            DroolsControllerConstants.getFactory().destroy(controller);
        }
    }

    @Test
    public void testToolsets() {
        testGsonToolset(createFilterSet());
    }

    /**
     * Test the Gson toolset.
     *
     * @param protocolFilter protocol filter
     */
    private void testGsonToolset(JsonProtocolFilter protocolFilter) {
        GsonProtocolCoderToolset gsonToolset =
                        new GsonProtocolCoderToolset(EventProtocolParams.builder().topic(JUNIT_PROTOCOL_CODER_TOPIC)
                                        .groupId(releaseId.getGroupId()).artifactId(releaseId.getArtifactId())
                                        .eventClass(ThreeStrings.class.getName()).protocolFilter(protocolFilter)
                                        .customGsonCoder(null).modelClassLoaderHash(12345678).build(), CONTROLLER_ID);

        assertNotNull(gsonToolset.getEncoder());
        assertNotNull(gsonToolset.getDecoder());
        assertThat(gsonToolset.toString()).startsWith("GsonProtocolCoderToolset [");
        assertThat(gsonToolset.toString()).contains("=ProtocolCoderToolset [");
        assertThat(gsonToolset.toString()).contains("[CoderFilters [");

        testToolset(protocolFilter, gsonToolset);

        ThreeStrings triple = createTriple();
        gsonToolset.setCustomCoder(new CustomGsonCoder(this.getClass().getName(), "customCoder"));
        String tripleEncoded = encode(gsonToolset, triple);
        decode(protocolFilter, gsonToolset, triple, tripleEncoded);
    }

    private ThreeStrings createTriple() {
        return new ThreeStrings("v1", "v2", "v3");
    }

    private void testToolset(JsonProtocolFilter protocolFilter, ProtocolCoderToolset coderToolset) {

        validateInitialization(protocolFilter, coderToolset);

        updateCoderFilterRule(coderToolset);

        addRemoveCoder(coderToolset);

        /* restore original filters */
        coderToolset.addCoder(ThreeStrings.class.getName(), protocolFilter, 654321);

        ThreeStrings triple = createTriple();

        String tripleEncoded = encode(coderToolset, triple);

        decode(protocolFilter, coderToolset, triple, tripleEncoded);
    }

    private void decode(JsonProtocolFilter protocolFilter, ProtocolCoderToolset coderToolset,
                    ThreeStrings triple, String tripleEncoded) {

        try {
            coderToolset.decode(tripleEncoded);
        } catch (UnsupportedOperationException e) {
            /* OK */
            logger.trace("Junit expected exception - decode does not pass filtering", e);
        }

        CoderFilters coderFilters = coderToolset.getCoder(ThreeStrings.class.getName());
        assertSame(coderFilters.getFactClass(), ThreeStrings.class.getName());
        assertSame(coderFilters.getFilter(), protocolFilter);
        assertNotNull(coderFilters.getFilter().getRule());

        coderFilters.getFilter().setRule("[?($.second =~ /^v2$/ && $.third =~ /.*v3.*/)]");

        ThreeStrings tripleDecoded = (ThreeStrings) coderToolset.decode(tripleEncoded);

        assertEquals(triple.getFirst(), tripleDecoded.getFirst());
        assertEquals(triple.getSecond(), tripleDecoded.getSecond());
        assertEquals(triple.getThird(), tripleDecoded.getThird());

        coderFilters.getFilter().setRule(null);
        assertEquals("[?($ =~ /.*/)]", coderFilters.getFilter().getRule());

        tripleDecoded = (ThreeStrings) coderToolset.decode(tripleEncoded);

        assertEquals(tripleDecoded.getFirst(), triple.getFirst());
        assertEquals(tripleDecoded.getSecond(), triple.getSecond());
        assertEquals(tripleDecoded.getThird(), triple.getThird());

        coderFilters.getFilter().setRule("[?($.third =~ /.*v3.*/)]");
    }

    private String encode(ProtocolCoderToolset coderToolset, ThreeStrings triple) {
        String tripleEncoded = coderToolset.encode(triple);
        assertFalse(tripleEncoded.isEmpty());
        return tripleEncoded;
    }

    private void addRemoveCoder(ProtocolCoderToolset coderToolset) {
        coderToolset.addCoder(this.getClass().getName(), new JsonProtocolFilter("[?($.second =~ /.*/)]"), 654321);
        assertEquals(2, coderToolset.getCoders().size());

        coderToolset.removeCoders(this.getClass().getName());
        assertEquals(1, coderToolset.getCoders().size());
    }

    private void updateCoderFilterRule(ProtocolCoderToolset coderToolset) {
        coderToolset.addCoder(ThreeStrings.class.getName(), new JsonProtocolFilter("[?($.third =~ /.*/)]"), 654321);

        assertEquals(1, coderToolset.getCoders().size());

        assertEquals(654321, coderToolset.getCoder(ThreeStrings.class.getName()).getModelClassLoaderHash());

        assertNotNull(coderToolset.getCoder(ThreeStrings.class.getName()).getFilter().getRule());

        assertEquals("[?($.third =~ /.*/)]",
                        coderToolset.getCoder(ThreeStrings.class.getName()).getFilter().getRule());
    }

    private void validateInitialization(JsonProtocolFilter protocolFilter, ProtocolCoderToolset coderToolset) {
        assertEquals(CONTROLLER_ID, coderToolset.getControllerId());
        assertEquals(releaseId.getGroupId(), coderToolset.getGroupId());
        assertEquals(releaseId.getArtifactId(), coderToolset.getArtifactId());
        assertNull(coderToolset.getCustomCoder());

        assertEquals(1, coderToolset.getCoders().size());

        CoderFilters coderFilters = coderToolset.getCoder(CONTROLLER_ID);
        assertNull(coderFilters);

        coderFilters = coderToolset.getCoder(ThreeStrings.class.getName());
        assertNotNull(coderFilters);

        assertEquals(coderFilters.getFilter(), protocolFilter);
    }

    private DroolsController createController() {
        if (releaseId == null) {
            throw new IllegalStateException("no prereq artifact installed in maven repository");
        }

        Properties sinkConfig = new Properties();
        sinkConfig.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS, JUNIT_PROTOCOL_CODER_TOPIC);
        final List<TopicSink> noopTopics = TopicEndpointManager.getManager().addTopicSinks(sinkConfig);

        Properties droolsControllerConfig = new Properties();
        droolsControllerConfig.put(DroolsPropertyConstants.RULES_GROUPID, releaseId.getGroupId());
        droolsControllerConfig.put(DroolsPropertyConstants.RULES_ARTIFACTID, releaseId.getArtifactId());
        droolsControllerConfig.put(DroolsPropertyConstants.RULES_VERSION, releaseId.getVersion());
        droolsControllerConfig.put(
                        PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS + "." + JUNIT_PROTOCOL_CODER_TOPIC
                                        + PolicyEndPointProperties.PROPERTY_TOPIC_EVENTS_SUFFIX,
                        ThreeStrings.class.getName());

        return DroolsControllerConstants.getFactory().build(droolsControllerConfig, null, noopTopics);
    }

    private JsonProtocolFilter createFilterSet() {
        return new JsonProtocolFilter("[?($.first =~ /.*/ && $.second =~ /^blah.*/ && $.third =~ /^hello$/)]");
    }

    /**
     * Note: We need an object that can be constructed, but the apache Triple cannot, thus
     * we create our own class just for these tests.
     */
    @Getter
    @AllArgsConstructor
    public static class ThreeStrings {
        private String first;
        private String second;
        private String third;
    }
}
