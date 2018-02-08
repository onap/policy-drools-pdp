/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.builder.ReleaseId;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.internal.MavenDroolsControllerTest;
import org.onap.policy.drools.event.comm.TopicEndpoint;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter.FilterRule;
import org.onap.policy.drools.util.KieUtils;
import org.onap.policy.drools.utils.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProtocolCoder Toolset JUNITs
 */
public class ProtocolCoderToolsetTest {
    public static final String JUNIT_PROTOCOL_CODER_ARTIFACT_ID = "protocolcoder";
    public static final String JUNIT_PROTOCOL_CODER_TOPIC = JUNIT_PROTOCOL_CODER_ARTIFACT_ID;

    private static Logger logger = LoggerFactory.getLogger(ProtocolCoderToolset.class);

    private volatile ReleaseId releaseId;

    @Before
    public void setUp() throws IOException {
        if (releaseId != null)
            return;

        String pom = new String(Files.readAllBytes
            (Paths.get(MavenDroolsControllerTest.JUNIT_ECHO_KMODULE_POM_PATH)));

        if (!pom.contains("<artifactId>echo</artifactId>"))
            throw new IllegalArgumentException("unexpected junit test pom");

        String newPom = pom.replace("echo",  JUNIT_PROTOCOL_CODER_ARTIFACT_ID);

        String kmodule = new String(Files.readAllBytes
            (Paths.get(MavenDroolsControllerTest.JUNIT_ECHO_KMODULE_PATH)));

        String drl = new String(Files.readAllBytes
            (Paths.get(MavenDroolsControllerTest.JUNIT_ECHO_KMODULE_DRL_PATH)));

        releaseId =
            KieUtils.installArtifact(kmodule, newPom,
                    MavenDroolsControllerTest.JUNIT_ECHO_KJAR_DRL_PATH, drl);
    }

    @Test
    public void testGsonToolset() {
        if (releaseId == null)
            throw new IllegalStateException("no prereq artifact installed in maven repository");

        JsonProtocolFilter protocolFilter = createFilterSet();

        GsonProtocolCoderToolset gsonToolset =
            new GsonProtocolCoderToolset(JUNIT_PROTOCOL_CODER_TOPIC,
                "blah",
                this.releaseId.getGroupId(),
                this.releaseId.getArtifactId(),
                Triple.class.getCanonicalName(),
                protocolFilter,
                null,
                12345678);

        Assert.assertNotNull(gsonToolset.getEncoder());
        Assert.assertNotNull(gsonToolset.getDecoder());

        testToolset(protocolFilter, gsonToolset);
    }

    @Test
    public void testJacksonToolset() {
        if (releaseId == null)
            throw new IllegalStateException("no prereq artifact installed in maven repository");

        JsonProtocolFilter protocolFilter = createFilterSet();

        JacksonProtocolCoderToolset jacksonToolset =
            new JacksonProtocolCoderToolset(JUNIT_PROTOCOL_CODER_TOPIC,
                "blah",
                this.releaseId.getGroupId(),
                this.releaseId.getArtifactId(),
                Triple.class.getCanonicalName(),
                protocolFilter,
                null,
                12345678);

        jacksonToolset.getEncoder().setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        jacksonToolset.getDecoder().setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

        testToolset(protocolFilter, jacksonToolset);
    }

    private JsonProtocolFilter createFilterSet() {
        List<FilterRule> filters = new ArrayList<>();
        filters.add(new FilterRule("first", ".*"));
        filters.add(new FilterRule("second", "^blah.*"));
        filters.add(new FilterRule("third", "^hello$"));

        return new JsonProtocolFilter(filters);
    }

    private void testToolset(JsonProtocolFilter protocolFilter, ProtocolCoderToolset coderToolset) {

        Assert.assertTrue("blah".equals(coderToolset.getControllerId()));
        Assert.assertTrue(this.releaseId.getGroupId().equals(coderToolset.getGroupId()));
        Assert.assertTrue(this.releaseId.getArtifactId().equals(coderToolset.getArtifactId()));
        Assert.assertNull(coderToolset.getCustomCoder());

        Assert.assertTrue(coderToolset.getCoders().size() == 1);

        CoderFilters coderFilters = coderToolset.getCoder("blah");
        Assert.assertTrue(coderFilters == null);

        coderFilters = coderToolset.getCoder(Triple.class.getCanonicalName());
        Assert.assertNotNull(coderFilters);

        Assert.assertEquals(coderFilters.getFilter(), protocolFilter);

        List<FilterRule> filters = new ArrayList<>();
        filters.add(new FilterRule("third", ".*"));
        coderToolset.addCoder(Triple.class.getCanonicalName(),
            new JsonProtocolFilter(filters), 654321);

        Assert.assertTrue(coderToolset.getCoders().size() == 1);

        Assert.assertTrue
            (coderToolset.getCoder(Triple.class.getCanonicalName()).
                getModelClassLoaderHash() == 654321);

        Assert.assertTrue
            (coderToolset.getCoder(Triple.class.getCanonicalName()).
                getFilter().getRules("third").size() == 1);

        Assert.assertTrue
            (coderToolset.getCoder(Triple.class.getCanonicalName()).
                getFilter().getRules("third").size() == 1);

        Assert.assertTrue
            (".*".equals(coderToolset.getCoder(Triple.class.getCanonicalName()).
                getFilter().getRules("third").get(0).getRegex()));

        coderToolset.addCoder("blah", new JsonProtocolFilter(filters),654321);
        Assert.assertTrue(coderToolset.getCoders().size() == 2);

        coderToolset.removeCoders("blah");
        Assert.assertTrue(coderToolset.getCoders().size() == 1);

        /* restore original filters */
        coderToolset.addCoder(Triple.class.getCanonicalName(), protocolFilter, 654321);

        Triple<String, String, String> triple =
            new Triple<>("v1", "v2", "v3");

        String tripleEncoded = coderToolset.encode(triple);
        Assert.assertTrue(!tripleEncoded.isEmpty());

        Properties sinkConfig = new Properties();
        sinkConfig.put(PolicyProperties.PROPERTY_NOOP_SINK_TOPICS, JUNIT_PROTOCOL_CODER_TOPIC);
        List<? extends TopicSink> noopTopics =
            TopicEndpoint.manager.addTopicSinks(sinkConfig);

        Properties droolsControllerConfig = new Properties();
        droolsControllerConfig.put(PolicyProperties.RULES_GROUPID, releaseId.getGroupId());
        droolsControllerConfig.put(PolicyProperties.RULES_ARTIFACTID, releaseId.getArtifactId());
        droolsControllerConfig.put(PolicyProperties.RULES_VERSION, releaseId.getVersion());
        droolsControllerConfig.put(PolicyProperties.PROPERTY_NOOP_SINK_TOPICS + "." +
                JUNIT_PROTOCOL_CODER_TOPIC + PolicyProperties.PROPERTY_TOPIC_EVENTS_SUFFIX,
                 Triple.class.getCanonicalName());

        DroolsController droolsController =
            DroolsController.factory.build(droolsControllerConfig, null, noopTopics);

        Triple<String, String, String> tripleDecoded = null;
        try {
            tripleDecoded =
                (Triple<String, String, String>) coderToolset.decode(tripleEncoded);
        } catch(UnsupportedOperationException e){
            logger.trace("Junit expected exception - decode does not pass filtering", e);
        }

        coderFilters = coderToolset.getCoder(Triple.class.getCanonicalName());
        Assert.assertTrue(coderFilters.getCodedClass() == Triple.class.getCanonicalName());
        Assert.assertTrue(coderFilters.getFilter() == protocolFilter);
        Assert.assertTrue(coderFilters.getFilter().getRules("second").size() == 1);
        Assert.assertTrue(coderFilters.getFilter().getRules("third").size() == 1);

        coderFilters.getFilter().getRules("second").get(0).setRegex("^v2$");
        coderFilters.getFilter().getRules("third").get(0).setRegex(".*v3.*");

        tripleDecoded =
            (Triple<String, String, String>) coderToolset.decode(tripleEncoded);

        Assert.assertTrue(tripleDecoded.first().equals(triple.first()));
        Assert.assertTrue(tripleDecoded.second().equals(triple.second()));
        Assert.assertTrue(tripleDecoded.third().equals(triple.third()));

        coderFilters.getFilter().deleteRules("third");
        Assert.assertTrue(coderFilters.getFilter().getRules("third").isEmpty());

        tripleDecoded =
            (Triple<String, String, String>) coderToolset.decode(tripleEncoded);

        Assert.assertTrue(tripleDecoded.first().equals(triple.first()));
        Assert.assertTrue(tripleDecoded.second().equals(triple.second()));
        Assert.assertTrue(tripleDecoded.third().equals(triple.third()));
    }
}
