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

package org.onap.policy.drools.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kproject.models.KieModuleModelImpl;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.scanner.MavenRepository;

/**
 * Kie related utilities
 */
public class KieUtils {

    /**
     * Installs a rules artifact in the local maven repository
     *
     * @param kmodule kmodule specification
     * @param pom pom
     * @param drlKJarPath path used in kjar drl to the actual drl
     * @param drl rules in drl language
     *
     * @return releaseId result o a sucessful installation
     * @throws IOException error accessing necessary resources
     */
    public static ReleaseId installArtifact(String kmodule, String pom, String drlKJarPath, String drl) throws IOException {
        KieModuleModel kieModule = KieModuleModelImpl.fromXML(kmodule);

        final KieFileSystem kieFileSystem = KieServices.Factory.get().newKieFileSystem();
        kieFileSystem.writeKModuleXML(kieModule.toXML());
        kieFileSystem.writePomXML(pom);
        kieFileSystem.write(drlKJarPath, drl);

        KieBuilder kieBuilder = kieBuild(kieFileSystem);

        Path pomPath = Files.createTempFile("policy-core-", ".pom");
        Files.write(pomPath, pom.getBytes(StandardCharsets.UTF_8));
        File pomFile = pomPath.toFile();
        pomFile.deleteOnExit();

        ReleaseId releaseId = kieBuilder.getKieModule().getReleaseId();
        MavenRepository.getMavenRepository().
            installArtifact(releaseId,
                            (InternalKieModule) kieBuilder.getKieModule(),
                            pomFile);
        return releaseId;
    }

    /**
     * Installs a rules artifact in the local maven repository
     *
     * @param kmodule kmodule specification
     * @param pom pom
     * @param drlKJarPath path used in kjar drl to the actual drl
     * @param drl rules in drl language
     *
     * @return releaseId result o a sucessful installation
     * @throws IOException error accessing necessary resources
     */
    public static ReleaseId installArtifact(File kmodule, File pom, String drlKJarPath, File drl) throws IOException {
        KieModuleModel kieModule = KieModuleModelImpl.fromXML(kmodule);

        final KieFileSystem kieFileSystem = KieServices.Factory.get().newKieFileSystem();
        kieFileSystem.writeKModuleXML(kieModule.toXML());
        kieFileSystem.writePomXML(new String(Files.readAllBytes(pom.toPath())));
        kieFileSystem.write(drlKJarPath, new String(Files.readAllBytes(drl.toPath())));

        KieBuilder kieBuilder = kieBuild(kieFileSystem);

        ReleaseId releaseId = kieBuilder.getKieModule().getReleaseId();
        MavenRepository.getMavenRepository().
            installArtifact(releaseId, (InternalKieModule) kieBuilder.getKieModule(), pom);
        return releaseId;
    }

    private static KieBuilder kieBuild(KieFileSystem kieFileSystem) {
        KieBuilder kieBuilder = KieServices.Factory.get().newKieBuilder(kieFileSystem);
        List<Message> messages = kieBuilder.buildAll().getResults().getMessages();
        if (messages != null && !messages.isEmpty())
            throw new IllegalArgumentException(messages.toString());
        return kieBuilder;
    }
}
