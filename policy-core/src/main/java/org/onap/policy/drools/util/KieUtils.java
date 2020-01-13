/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import lombok.NonNull;

import org.apache.commons.io.IOUtils;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kproject.models.KieModuleModelImpl;
import org.drools.core.impl.KnowledgeBaseImpl;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.scanner.KieMavenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kie related utilities.
 */
public class KieUtils {

    private static final Logger logger = LoggerFactory.getLogger(KieUtils.class);

    // resource names used by 'resourceToPackages'
    private static final String RESOURCE_PREFIX = "src/main/resources/drools";
    private static final String RESOURCE_SUFFIX = ".drl";

    private KieUtils() {
        // Utility class
    }

    /**
     * Installs a rules artifact in the local maven repository.
     */
    public static ReleaseId installArtifact(File kmodule, File pom, String drlKJarPath, @NonNull List<File> drls)
        throws IOException {
        KieModuleModel kieModule = KieModuleModelImpl.fromXML(kmodule);

        final KieFileSystem kieFileSystem = KieServices.Factory.get().newKieFileSystem();
        kieFileSystem.writeKModuleXML(kieModule.toXML());
        kieFileSystem.writePomXML(new String(Files.readAllBytes(pom.toPath())));
        for (File drl : drls) {
            kieFileSystem.write(drlKJarPath + drl.getName(), new String(Files.readAllBytes(drl.toPath())));
        }

        KieBuilder kieBuilder = build(kieFileSystem);
        return getReleaseId(kieBuilder, pom);
    }

    /**
     * Installs a rules artifact in the local maven repository.
     */
    public static ReleaseId installArtifact(File kmodule, File pom, String drlKJarPath, File drl)
        throws IOException {
        return installArtifact(kmodule, pom, drlKJarPath, Collections.singletonList(drl));
    }

    private static ReleaseId getReleaseId(KieBuilder kieBuilder, File pomFile) {
        ReleaseId releaseId = kieBuilder.getKieModule().getReleaseId();
        KieMavenRepository
            .getKieMavenRepository()
            .installArtifact(releaseId,
                (InternalKieModule) kieBuilder.getKieModule(),
                pomFile);
        return releaseId;
    }

    /**
     * Get Knowledge Bases.
     */
    public static List<String> getBases(KieContainer container) {
        return new ArrayList<>(container.getKieBaseNames());
    }

    /**
     * Get Packages.
     */
    public static List<KiePackage> getPackages(KieContainer container) {
        return getBases(container).stream()
            .flatMap(base -> container.getKieBase(base).getKiePackages().stream())
            .collect(Collectors.toList());
    }

    /**
     * Get Package Names.
     */
    public static List<String> getPackageNames(KieContainer container) {
        return getPackages(container).stream()
            .map(KiePackage::getName)
            .collect(Collectors.toList());
    }

    /**
     * Get Rules.
     */
    public static List<Rule> getRules(KieContainer container) {
        return getPackages(container).stream()
            .flatMap(kiePackage -> kiePackage.getRules().stream())
            .collect(Collectors.toList());
    }

    /**
     * Get Rule Names.
     */
    public static List<String> getRuleNames(KieContainer container) {
        return getRules(container).stream()
            .map(Rule::getName)
            .collect(Collectors.toList());
    }

    /**
     * Get Facts.
     */
    public static List<Object> getFacts(KieSession session) {
        return session.getFactHandles().stream()
            .map(session::getObject)
            .collect(Collectors.toList());
    }

    /**
     * Create Container.
     */
    public static KieContainer createContainer(ReleaseId releaseId) {
        return KieServices.Factory.get().newKieContainer(releaseId);
    }

    private static KieBuilder build(KieFileSystem kieFileSystem) {
        KieBuilder kieBuilder = KieServices.Factory.get().newKieBuilder(kieFileSystem);
        List<Message> messages = kieBuilder.buildAll().getResults().getMessages();
        if (messages != null && !messages.isEmpty()) {
            throw new IllegalArgumentException(messages.toString());
        }
        return kieBuilder;
    }

    /**
     * Find all Drools resources matching a specified name, and generate a
     * collection of 'KiePackage' instances from those resources.
     *
     * @param classLoader the class loader to use when finding resources, or
     *     when building the 'KiePackage' collection
     * @param resourceName the resource name, without a leading '/' character
     * @return a collection of 'KiePackage' instances, or 'null' in case of
     *     failure
     */
    public static Collection<KiePackage> resourceToPackages(ClassLoader classLoader, String resourceName) {

        // find all resources matching 'resourceName'
        Enumeration<URL> resources;
        try {
            resources = classLoader.getResources(resourceName);
        } catch (IOException e) {
            logger.error("Exception fetching resources: " + resourceName, e);
            return null;
        }
        if (!resources.hasMoreElements()) {
            // no resources found
            return null;
        }

        // generate a 'KieFileSystem' from these resources
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();
        int index = 1;
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (InputStream is = url.openStream()) {
                // convert a resource to a byte array
                byte[] drl = IOUtils.toByteArray(is);

                // add a new '.drl' entry to the KieFileSystem
                kfs.write(RESOURCE_PREFIX + index++ + RESOURCE_SUFFIX, drl);
            } catch (IOException e) {
                logger.error("Couldn't read in " + url, e);
                return null;
            }
        }

        // do a build of the 'KieFileSystem'
        KieBuilder builder = kieServices.newKieBuilder(kfs, classLoader);
        builder.buildAll();
        List<Message> results = builder.getResults().getMessages();
        if (!results.isEmpty()) {
            logger.error("Kie build failed:\n" + results);
            return null;
        }

        // generate a KieContainer, and extract the package list
        return kieServices.newKieContainer(builder.getKieModule().getReleaseId(), classLoader)
               .getKieBase().getKiePackages();
    }

    /**
     * Add a collection of 'KiePackage' instances to the specified 'KieBase'.
     *
     * @param kieBase the 'KieBase' instance to add the packages to
     * @param kiePackages the collection of packages to add
     */
    public static void addKiePackages(KieBase kieBase, Collection<KiePackage> kiePackages) {
        HashSet<KiePackage> stillNeeded = new HashSet<>(kiePackages);

        // update 'stillNeeded' by removing any packages we already have
        stillNeeded.removeAll(kieBase.getKiePackages());

        if (!stillNeeded.isEmpty()) {
            // there are still packages we need to add --
            // this code makes use of an internal class and method
            ((KnowledgeBaseImpl)kieBase).addPackages(stillNeeded);
        }
    }
}
