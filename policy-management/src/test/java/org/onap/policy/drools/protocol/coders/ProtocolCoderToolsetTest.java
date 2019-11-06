/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.ReleaseId;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.controller.internal.MavenDroolsControllerTest;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.onap.policy.drools.util.KieUtils;
import org.onap.policy.drools.utils.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProtocolCoder Toolset Junits.
 */
public class ProtocolCoderToolsetTest {
    public static final String JUNIT_PROTOCOL_CODER_ARTIFACT_ID = "protocolcoder";
    public static final String JUNIT_PROTOCOL_CODER_TOPIC = JUNIT_PROTOCOL_CODER_ARTIFACT_ID;
    public static final String CONTROLLER_ID = "blah";

    private static Logger logger = LoggerFactory.getLogger(ProtocolCoderToolset.class);

    private static volatile ReleaseId releaseId;

    // customCoder has to be public to be accessed in tests below
    public static final Gson customCoder = new GsonBuilder().create();

    /**
     * Test Class Initialization.
     */
    @BeforeClass
    public static void setupClass() throws IOException {
        releaseId = KieUtils.installArtifact(
            Paths.get(MavenDroolsControllerTest.JUNIT_ECHO_KMODULE_PATH).toFile(),
            Paths.get(MavenDroolsControllerTest.JUNIT_ECHO_KMODULE_POM_PATH).toFile(),
            MavenDroolsControllerTest.JUNIT_ECHO_KJAR_DRL_PATH,
            Paths.get(MavenDroolsControllerTest.JUNIT_ECHO_KMODULE_DRL_PATH).toFile());
    }

    @Test
    public void testToolsets() {
        createController();
        testGsonToolset(createFilterSet());
    }

    /**
     * Test the Gson toolset.
     *
     * @param protocolFilter protocol filter
     */
    public void testGsonToolset(JsonProtocolFilter protocolFilter) {
        GsonProtocolCoderToolset gsonToolset = new GsonProtocolCoderToolset(
                EventProtocolParams.builder().topic(JUNIT_PROTOCOL_CODER_TOPIC)
                        .groupId(this.releaseId.getGroupId())
                        .artifactId(this.releaseId.getArtifactId())
                        .eventClass(Triple.class.getName())
                        .protocolFilter(protocolFilter)
                        .customGsonCoder(null)
                        .modelClassLoaderHash(12345678), CONTROLLER_ID);

        Assert.assertNotNull(gsonToolset.getEncoder());
        Assert.assertNotNull(gsonToolset.getDecoder());

        testToolset(protocolFilter, gsonToolset);

        Triple<String, String, String> triple = createTriple();
        gsonToolset.setCustomCoder(new CustomGsonCoder(this.getClass().getName(), "customCoder"));
        String tripleEncoded = encode(gsonToolset, triple);
        decode(protocolFilter, gsonToolset, triple, tripleEncoded);
    }

    private Triple<String, String, String> createTriple() {
        return new Triple<>("v1", "v2", "v3");
    }

    private void testToolset(JsonProtocolFilter protocolFilter, ProtocolCoderToolset coderToolset) {

        validateInitialization(protocolFilter, coderToolset);

        updateCoderFilterRule(coderToolset);

        addRemoveCoder(coderToolset);

        /* restore original filters */
        coderToolset.addCoder(Triple.class.getName(), protocolFilter, 654321);

        Triple<String, String, String> triple = createTriple();

        String tripleEncoded = encode(coderToolset, triple);

        decode(protocolFilter, coderToolset, triple, tripleEncoded);
    }

    @SuppressWarnings("unchecked")
    private void decode(JsonProtocolFilter protocolFilter, ProtocolCoderToolset coderToolset,
            Triple<String, String, String> triple, String tripleEncoded) {

        Triple<String, String, String> tripleDecoded = null;
        try {
            tripleDecoded = (Triple<String, String, String>) coderToolset.decode(tripleEncoded);
        } catch (UnsupportedOperationException e) {
            /* OK */
            logger.trace("Junit expected exception - decode does not pass filtering", e);
        }

        CoderFilters coderFilters = coderToolset.getCoder(Triple.class.getName());
        Assert.assertTrue(coderFilters.getCodedClass() == Triple.class.getName());
        Assert.assertTrue(coderFilters.getFilter() == protocolFilter);
        Assert.assertTrue(coderFilters.getFilter().getRule() != null);

        coderFilters.getFilter().setRule("[?($.second =~ /^v2$/ && $.third =~ /.*v3.*/)]");

        tripleDecoded = (Triple<String, String, String>) coderToolset.decode(tripleEncoded);

        Assert.assertTrue(tripleDecoded.first().equals(triple.first()));
        Assert.assertTrue(tripleDecoded.second().equals(triple.second()));
        Assert.assertTrue(tripleDecoded.third().equals(triple.third()));

        coderFilters.getFilter().setRule(null);
        Assert.assertEquals("[?($ =~ /.*/)]", coderFilters.getFilter().getRule());

        tripleDecoded = (Triple<String, String, String>) coderToolset.decode(tripleEncoded);

        Assert.assertTrue(tripleDecoded.first().equals(triple.first()));
        Assert.assertTrue(tripleDecoded.second().equals(triple.second()));
        Assert.assertTrue(tripleDecoded.third().equals(triple.third()));

        coderFilters.getFilter().setRule("[?($.third =~ /.*v3.*/)]");
    }

    private String encode(ProtocolCoderToolset coderToolset, Triple<String, String, String> triple) {
        String tripleEncoded = coderToolset.encode(triple);
        Assert.assertTrue(!tripleEncoded.isEmpty());
        return tripleEncoded;
    }

    private void addRemoveCoder(ProtocolCoderToolset coderToolset) {
        coderToolset.addCoder(this.getClass().getName(),
                new JsonProtocolFilter("[?($.second =~ /.*/)]"), 654321);
        Assert.assertTrue(coderToolset.getCoders().size() == 2);

        coderToolset.removeCoders(this.getClass().getName());
        Assert.assertTrue(coderToolset.getCoders().size() == 1);
    }

    private void updateCoderFilterRule(ProtocolCoderToolset coderToolset) {
        coderToolset.addCoder(Triple.class.getName(), new JsonProtocolFilter("[?($.third =~ /.*/)]"), 654321);

        Assert.assertTrue(coderToolset.getCoders().size() == 1);

        Assert.assertTrue(coderToolset.getCoder(Triple.class.getName()).getModelClassLoaderHash() == 654321);

        Assert.assertTrue(
                coderToolset.getCoder(
                        Triple.class.getName()).getFilter().getRule() != null);

        Assert.assertTrue("[?($.third =~ /.*/)]".equals(coderToolset.getCoder(Triple.class.getName())
                .getFilter().getRule()));
    }

    private void validateInitialization(JsonProtocolFilter protocolFilter, ProtocolCoderToolset coderToolset) {
        Assert.assertTrue(CONTROLLER_ID.equals(coderToolset.getControllerId()));
        Assert.assertTrue(this.releaseId.getGroupId().equals(coderToolset.getGroupId()));
        Assert.assertTrue(this.releaseId.getArtifactId().equals(coderToolset.getArtifactId()));
        Assert.assertNull(coderToolset.getCustomCoder());

        Assert.assertTrue(coderToolset.getCoders().size() == 1);

        CoderFilters coderFilters = coderToolset.getCoder(CONTROLLER_ID);
        Assert.assertTrue(coderFilters == null);

        coderFilters = coderToolset.getCoder(Triple.class.getName());
        Assert.assertNotNull(coderFilters);

        Assert.assertEquals(coderFilters.getFilter(), protocolFilter);
    }

    private void createController() {
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
        droolsControllerConfig.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS + "." + JUNIT_PROTOCOL_CODER_TOPIC
                + PolicyEndPointProperties.PROPERTY_TOPIC_EVENTS_SUFFIX, Triple.class.getName());

        DroolsControllerConstants.getFactory().build(droolsControllerConfig, null, noopTopics);
    }

    private JsonProtocolFilter createFilterSet() {
        return new JsonProtocolFilter("[?($.first =~ /.*/ && $.second =~ /^blah.*/ && $.third =~ /^hello$/)]");
    }
}
