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

package org.onap.policy.drools.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.ReleaseId;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

/**
 * Kie Utils Tests.
 */
public class KieUtilsTest {

    private static KieContainer container;
    private static KieSession session;

    /**
     * Test class initialization.
     */
    @BeforeClass
    public static void createArtifact() throws Exception {
        ReleaseId releaseId =
            KieUtils.installArtifact(
                Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/META-INF/kmodule.xml").toFile(),
                Paths.get("src/test/resources/drools-artifact-1.1/pom.xml").toFile(),
                "src/main/resources/rules/org/onap/policy/drools/core/test/",
                Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/rules.drl").toFile());

        container = KieUtils.createContainer(releaseId);
        session = container.getKieBase("rules").newKieSession();
    }

    @Test
    public void testInstallArtifact() throws IOException {
        ReleaseId releaseId =
            KieUtils.installArtifact(
                Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/META-INF/kmodule.xml").toFile(),
                Paths.get("src/test/resources/drools-artifact-1.1/pom.xml").toFile(),
                "src/main/resources/rules/org/onap/policy/drools/core/test/",
                Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/rules.drl").toFile());

        assertNotNull(releaseId);
    }

    @Test
    public void testInstallArtifactList() throws IOException {
        ReleaseId releaseId =
            KieUtils.installArtifact(
                Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/META-INF/kmodule.xml").toFile(),
                Paths.get("src/test/resources/drools-artifact-1.1/pom.xml").toFile(),
                "src/main/resources/rules/org/onap/policy/drools/core/test/",
                Collections.singletonList(
                    Paths.get("src/test/resources/drools-artifact-1.1/src/main/resources/rules.drl").toFile()));

        assertNotNull(releaseId);
    }

    @Test
    public void getBases() {
        assertEquals(Collections.singletonList("rules"), KieUtils.getBases(container));
    }

    @Test
    public void getPackages() {
        assertEquals(Arrays.asList("java.util", "java.util.concurrent", "org.onap.policy.drools.core.test"),
            KieUtils.getPackages(container) .stream().map(KiePackage::getName).collect(Collectors.toList()));
    }

    @Test
    public void getPackageNames() {
        assertEquals(Arrays.asList("java.util", "java.util.concurrent", "org.onap.policy.drools.core.test"),
            new ArrayList<>(KieUtils.getPackageNames(container)));
    }

    @Test
    public void getRules() {
        assertEquals(Arrays.asList("Initialization", "Add elements of an int list"),
            KieUtils.getRules(container).stream().map(Rule::getName).collect(Collectors.toList()));
    }

    @Test
    public void getRuleNames() {
        assertEquals(Arrays.asList("Initialization", "Add elements of an int list"),
            KieUtils.getRuleNames(container).stream().collect(Collectors.toList()));
    }

    @Test
    public void getFacts() {
        assertEquals(0, KieUtils.getFacts(session).size());
    }

    @Test
    public void testResourceToPackages() {
        // Some minimal logging -- it would be nice to verify the 'KieUtils' logger messages
        StringBuffer log;

        // test IOException from ClassLoader
        log = new StringBuffer();
        assertNull(KieUtils.resourceToPackages(new BogusClassLoader(log), "BogusClassLoader").orElse(null));
        assertEquals("IOException(BogusClassLoader)", log.toString());

        // test 'null' return when no resources are found
        assertNull(KieUtils.resourceToPackages(ClassLoader.getSystemClassLoader(), "no/such/url").orElse(null));

        // test IOException in 'IOUtils.toByteArray()' -> 'InputStream.read()'
        log = new StringBuffer();
        assertNull(KieUtils.resourceToPackages(new BogusClassLoader(log), "BogusUrl").orElse(null));
        assertEquals("", log.toString());

        // don't know how to test 'KieBuilder' errors at this point

        // success legs are tested in 'DroolsContainerTest'
    }

    static class BogusClassLoader extends ClassLoader {
        StringBuffer log;

        BogusClassLoader(StringBuffer log) {
            this.log = log;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if ("BogusUrl".equals(name)) {
                return new Enumeration<URL>() {
                    @Override
                    public boolean hasMoreElements() {
                        return true;
                    }

                    @Override
                    public URL nextElement() {
                        try {
                            // when the following URL is used, an IOException will occur
                            return new URL("http://127.0.0.1:1");
                        } catch (IOException e) {
                            // this should never happen, as the URL above is syntactically valid
                            return null;
                        }
                    }
                };
            } else {
                log.append("IOException(BogusClassLoader)");
                throw new IOException("BogusClassLoader");
            }
        }
    }
}
