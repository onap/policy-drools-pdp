/*-
 * ============LICENSE_START=======================================================
 * feature-state-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.statemanagement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class audits the Maven repository.
 */
public class RepositoryAudit extends DroolsPdpIntegrityMonitor.AuditBase {
    // timeout in 60 seconds
    private static final long DEFAULT_TIMEOUT = 60;

    // get an instance of logger
    private static final Logger logger = LoggerFactory.getLogger(RepositoryAudit.class);

    // single global instance of this audit object
    private static RepositoryAudit instance = new RepositoryAudit();

    // Regex pattern used to find additional repos in the form "repository(number).id.url"
    private static final Pattern repoPattern = Pattern.compile("(repository([1-9][0-9]*))[.]audit[.]id");

    /**
     * Constructor - set the name to 'Repository'.
     */
    private RepositoryAudit() {
        super("Repository");
    }

    /**
     * Get the integrity monitor instance.
     *
     * @return the single 'RepositoryAudit' instance
     */
    public static DroolsPdpIntegrityMonitor.AuditBase getInstance() {
        return instance;
    }

    /**
     * First, get the names of each property from StateManagementProperties. For each property name, check if it is of
     * the form "repository(number).audit.id" If so, we extract the number and determine if there exists another
     * property in the form "repository(number).audit.url" with the same "number". Only the
     * 'repository(number).audit.id' and 'repository(number).audit.url" properties need to be specified. If both 'id'
     * and 'url' properties are found, we add it to our set. InvokeData.getProperty(String, boolean) will determine the
     * other 4 properties: '*.username', '*.password', '*.is.active', and '*.ignore.errors', or use default values.
     *
     * @return set of Integers representing a repository to support
     */
    private static TreeSet<Integer> countAdditionalNexusRepos() {
        TreeSet<Integer> returnIndices = new TreeSet<>();
        Properties properties = StateManagementProperties.getProperties();
        Set<String> propertyNames = properties.stringPropertyNames();

        for (String currName : propertyNames) {
            Matcher matcher = repoPattern.matcher(currName);

            if (matcher.matches()) {
                int currRepoNum = Integer.parseInt(matcher.group(2));
                if (propertyNames.contains(matcher.group(1) + ".audit.url")) {
                    returnIndices.add(currRepoNum);
                }
            }
        }
        return returnIndices;
    }

    /**
     * Invoke the audit.
     *
     * @param properties properties to be passed to the audit
     */
    @Override
    public void invoke(Properties properties) throws IOException, InterruptedException {
        logger.debug("Running 'RepositoryAudit.invoke'");

        InvokeData data = new InvokeData();

        logger.debug("RepositoryAudit.invoke: repoAuditIsActive = {}" + ", repoAuditIgnoreErrors = {}",
                data.repoAuditIsActive, data.repoAuditIgnoreErrors);

        data.initIsActive();

        if (!data.isActive) {
            logger.info("RepositoryAudit.invoke: exiting because isActive = {}", data.isActive);
            return;
        }

        // Run audit for first nexus repository
        logger.debug("Running read-only audit on first nexus repository: repository");
        runAudit(data);

        // set of indices for supported nexus repos (ex: repository2 -> 2)
        // TreeSet is used to maintain order so repos can be audited in numerical order
        TreeSet<Integer> repoIndices = countAdditionalNexusRepos();
        logger.debug("Additional nexus repositories: {}", repoIndices);

        // Run audit for remaining 'numNexusRepos' repositories
        for (int index : repoIndices) {
            logger.debug("Running read-only audit on nexus repository = repository{}", index);

            data = new InvokeData(index);
            data.initIsActive();

            if (data.isActive) {
                runAudit(data);
            }
        }
    }

    private void runAudit(InvokeData data) throws IOException, InterruptedException {
        data.initIgnoreErrors();
        data.initTimeout();

        /*
         * 1) create temporary directory
         */
        data.dir = Files.createTempDirectory("auditRepo");
        logger.info("RepositoryAudit: temporary directory = {}", data.dir);

        // nested 'pom.xml' file and 'repo' directory
        final Path pom = data.dir.resolve("pom.xml");
        final Path repo = data.dir.resolve("repo");

        /*
         * 2) Create test file, and upload to repository (only if repository information is specified)
         */
        if (data.upload) {
            data.uploadTestFile();
        }

        /*
         * 3) create 'pom.xml' file in temporary directory
         */
        data.createPomFile(repo, pom);

        /*
         * 4) Invoke external 'mvn' process to do the downloads
         */

        // output file = ${dir}/out (this supports step '4a')
        File output = data.dir.resolve("out").toFile();

        // invoke process, and wait for response
        int rval = data.runMaven(output);

        /*
         * 4a) Check attempted and successful downloads from output file Note: at present, this step just generates log
         * messages, but doesn't do any verification.
         */
        if (rval == 0 && output != null) {
            generateDownloadLogs(output);
        }

        /*
         * 5) Check the contents of the directory to make sure the downloads were successful
         */
        data.verifyDownloads(repo);

        /*
         * 6) Use 'curl' to delete the uploaded test file (only if repository information is specified)
         */
        if (data.upload) {
            data.deleteUploadedTestFile();
        }

        /*
         * 7) Remove the temporary directory
         */
        Files.walkFileTree(data.dir, new RecursivelyDeleteDirectory());
    }


    /**
     * Set the response string to the specified value. Overrides 'setResponse(String value)' from
     * DroolsPdpIntegrityMonitor This method prevents setting a response string that indicates whether the caller should
     * receive an error list from the audit. By NOT setting the response string to a value, this indicates that there
     * are no errors.
     *
     * @param value the new value of the response string (null = no errors)
     */
    @Override
    public void setResponse(String value) {
        // Do nothing, prevent the caller from receiving a list of errors.
    }

    private class InvokeData {
        private boolean isActive = true;

        // ignore errors by default
        private boolean ignoreErrors = true;

        private final String repoAuditIsActive;
        private final String repoAuditIgnoreErrors;

        private final String repositoryId;
        private final String repositoryUrl;
        private final String repositoryUsername;
        private final String repositoryPassword;
        private final boolean upload;

        // used to incrementally construct response as problems occur
        // (empty = no problems)
        private final StringBuilder response = new StringBuilder();

        private long timeoutInSeconds = DEFAULT_TIMEOUT;

        private Path dir;

        private String groupId = null;
        private String artifactId = null;
        private String version = null;

        // artifacts to be downloaded
        private final List<Artifact> artifacts = new LinkedList<>();

        // 0 = base repository, 2-n = additional repositories
        private final int index;

        public InvokeData() {
            this(0);
        }

        public InvokeData(int index) {
            this.index = index;
            repoAuditIsActive = getProperty("audit.is.active", true);
            repoAuditIgnoreErrors = getProperty("audit.ignore.errors", true);

            // Fetch repository information from 'IntegrityMonitorProperties'
            repositoryId = getProperty("audit.id", false);
            repositoryUrl = getProperty("audit.url", false);
            repositoryUsername = getProperty(getValue("audit.username"), true);
            repositoryPassword = getProperty(getValue("audit.password"), true);

            logger.debug("Nexus Repository Information retrieved from 'IntegrityMonitorProperties':");
            logger.debug("repositoryId: " + repositoryId);
            logger.debug("repositoryUrl: " + repositoryUrl);

            // Setting upload to be false so that files can no longer be created/deleted
            upload = false;
        }


            private String getProperty(String property, boolean useDefault) {
            String fullProperty = (index == 0 ? "repository." + property : "repository" + index + "." + property);
            String rval = StateManagementProperties.getProperty(fullProperty);
            if (rval == null && index != 0 && useDefault) {
                rval = StateManagementProperties.getProperty("repository." + property);
            }
            return rval;
        }

        private String getValue(final String value) {
            if (value != null && value.matches("[$][{].*[}]$")) {
                return System.getenv(value.substring(2, value.length() - 1));
            }
            return value;
        }

        public void initIsActive() {
            if (repoAuditIsActive != null) {
                try {
                    isActive = Boolean.parseBoolean(repoAuditIsActive.trim());
                } catch (NumberFormatException e) {
                    logger.warn("RepositoryAudit.invoke: Ignoring invalid property: repository.audit.is.active = {}",
                            repoAuditIsActive);
                }
            }
            if (repositoryId == null || repositoryUrl == null) {
                isActive = false;
            }
        }

        public void initIgnoreErrors() {
            if (repoAuditIgnoreErrors != null) {
                try {
                    ignoreErrors = Boolean.parseBoolean(repoAuditIgnoreErrors.trim());
                } catch (NumberFormatException e) {
                    ignoreErrors = true;
                    logger.warn(
                            "RepositoryAudit.invoke: Ignoring invalid property: repository.audit.ignore.errors = {}",
                            repoAuditIgnoreErrors);
                }
            } else {
                ignoreErrors = true;
            }
        }

        public void initTimeout() {
            String timeoutString = getProperty("audit.timeout", true);
            if (timeoutString != null && !timeoutString.isEmpty()) {
                try {
                    timeoutInSeconds = Long.valueOf(timeoutString);
                } catch (NumberFormatException e) {
                    logger.error("RepositoryAudit: Invalid 'repository.audit.timeout' value: '{}'", timeoutString, e);
                    if (!ignoreErrors) {
                        response.append("Invalid 'repository.audit.timeout' value: '").append(timeoutString)
                                .append("'\n");
                        setResponse(response.toString());
                    }
                }
            }
        }

        private void uploadTestFile() throws IOException, InterruptedException {
            groupId = "org.onap.policy.audit";
            artifactId = "repository-audit";
            version = "0." + System.currentTimeMillis();

            if (repositoryUrl.toLowerCase().contains("snapshot")) {
                // use SNAPSHOT version
                version += "-SNAPSHOT";
            }

            // create text file to write
            try (FileOutputStream fos = new FileOutputStream(dir.resolve("repository-audit.txt").toFile())) {
                fos.write(version.getBytes());
            }

            // try to install file in repository
            if (runProcess(timeoutInSeconds, dir.toFile(), null, "mvn", "deploy:deploy-file",
                    "-DrepositoryId=" + repositoryId, "-Durl=" + repositoryUrl, "-Dfile=repository-audit.txt",
                    "-DgroupId=" + groupId, "-DartifactId=" + artifactId, "-Dversion=" + version, "-Dpackaging=txt",
                    "-DgeneratePom=false") != 0) {
                logger.error("RepositoryAudit: 'mvn deploy:deploy-file' failed");
                if (!ignoreErrors) {
                    response.append("'mvn deploy:deploy-file' failed\n");
                    setResponse(response.toString());
                }
            } else {
                logger.info("RepositoryAudit: 'mvn deploy:deploy-file succeeded");

                // we also want to include this new artifact in the download
                // test (steps 3 and 4)
                artifacts.add(new Artifact(groupId, artifactId, version, "txt"));
            }
        }

        private void createPomFile(final Path repo, final Path pom) throws IOException {

            artifacts.add(new Artifact("org.apache.maven/maven-embedder/3.2.2"));

            StringBuilder sb = new StringBuilder();
            sb.append(
                    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                            + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n"
                            + "\n" + "  <modelVersion>4.0.0</modelVersion>\n" + "  <groupId>empty</groupId>\n"
                            + "  <artifactId>empty</artifactId>\n" + "  <version>1.0-SNAPSHOT</version>\n"
                            + "  <packaging>pom</packaging>\n" + "\n" + "  <build>\n" + "    <plugins>\n"
                            + "      <plugin>\n" + "         <groupId>org.apache.maven.plugins</groupId>\n"
                            + "         <artifactId>maven-dependency-plugin</artifactId>\n"
                            + "         <version>2.10</version>\n" + "         <executions>\n"
                            + "           <execution>\n" + "             <id>copy</id>\n" + "             <goals>\n"
                            + "               <goal>copy</goal>\n" + "             </goals>\n"
                            + "             <configuration>\n" + "               <localRepositoryDirectory>")
                    .append(repo).append("</localRepositoryDirectory>\n").append("               <artifactItems>\n");

            for (Artifact artifact : artifacts) {
                // each artifact results in an 'artifactItem' element
                sb.append("                 <artifactItem>\n" + "                   <groupId>").append(artifact.groupId)
                        .append("</groupId>\n" + "                   <artifactId>").append(artifact.artifactId)
                        .append("</artifactId>\n" + "                   <version>").append(artifact.version)
                        .append("</version>\n" + "                   <type>").append(artifact.type)
                        .append("</type>\n" + "                 </artifactItem>\n");
            }
            sb.append("               </artifactItems>\n" + "             </configuration>\n"
                    + "           </execution>\n" + "         </executions>\n" + "      </plugin>\n"
                    + "    </plugins>\n" + "  </build>\n" + "</project>\n");

            try (FileOutputStream fos = new FileOutputStream(pom.toFile())) {
                fos.write(sb.toString().getBytes());
            }
        }

        private int runMaven(File output) throws IOException, InterruptedException {
            int rval = runProcess(timeoutInSeconds, dir.toFile(), output, "mvn", "compile");
            logger.info("RepositoryAudit: 'mvn' return value = {}", rval);
            if (rval != 0) {
                logger.error("RepositoryAudit: 'mvn compile' invocation failed");
                if (!ignoreErrors) {
                    response.append("'mvn compile' invocation failed\n");
                    setResponse(response.toString());
                }
            }
            return rval;
        }

        private void verifyDownloads(final Path repo) {
            for (Artifact artifact : artifacts) {
                if (repo.resolve(artifact.groupId.replace('.', '/')).resolve(artifact.artifactId)
                        .resolve(artifact.version)
                        .resolve(artifact.artifactId + "-" + artifact.version + "." + artifact.type).toFile()
                        .exists()) {
                    // artifact exists, as expected
                    logger.info("RepositoryAudit: {} : exists", artifact.toString());
                } else {
                    // Audit ERROR: artifact download failed for some reason
                    logger.error("RepositoryAudit: {}: does not exist", artifact.toString());
                    if (!ignoreErrors) {
                        response.append("Failed to download artifact: ").append(artifact).append('\n');
                        setResponse(response.toString());
                    }
                }
            }
        }

        private void deleteUploadedTestFile() throws IOException, InterruptedException {
            if (runProcess(timeoutInSeconds, dir.toFile(), null, "curl", "--request", "DELETE", "--user",
                    repositoryUsername + ":" + repositoryPassword,
                    repositoryUrl + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version) != 0) {
                logger.error("RepositoryAudit: delete of uploaded artifact failed");
                if (!ignoreErrors) {
                    response.append("delete of uploaded artifact failed\n");
                    setResponse(response.toString());
                }
            } else {
                logger.info("RepositoryAudit: delete of uploaded artifact succeeded");
                artifacts.add(new Artifact(groupId, artifactId, version, "txt"));
            }
        }
    }

    private void generateDownloadLogs(File output) throws IOException {
        // place output in 'fileContents' (replacing the Return characters
        // with Newline)
        byte[] outputData = new byte[(int) output.length()];
        String fileContents;
        try (FileInputStream fis = new FileInputStream(output)) {
            //
            // Ideally this should be in a loop or even better use
            // Java 8 nio functionality.
            //
            int bytesRead = fis.read(outputData);
            logger.info("fileContents read {} bytes", bytesRead);
            fileContents = new String(outputData).replace('\r', '\n');
        }

        // generate log messages from 'Downloading' and 'Downloaded'
        // messages within the 'mvn' output
        int index = 0;
        while ((index = fileContents.indexOf("\nDown", index)) > 0) {
            index += 5;
            if (fileContents.regionMatches(index, "loading: ", 0, 9)) {
                index += 9;
                int endIndex = fileContents.indexOf('\n', index);
                logger.info("RepositoryAudit: Attempted download: '{}'", fileContents.substring(index, endIndex));
                index = endIndex;
            } else if (fileContents.regionMatches(index, "loaded: ", 0, 8)) {
                index += 8;
                int endIndex = fileContents.indexOf(' ', index);
                logger.info("RepositoryAudit: Successful download: '{}'", fileContents.substring(index, endIndex));
                index = endIndex;
            }
        }
    }

    /**
     * Run a process, and wait for the response.
     *
     * @param timeoutInSeconds the number of seconds to wait for the process to terminate
     * @param directory the execution directory of the process (null = current directory)
     * @param stdout the file to contain the standard output (null = discard standard output)
     * @param command command and arguments
     * @return the return value of the process
     * @throws IOException InterruptedException
     */
    static int runProcess(long timeoutInSeconds, File directory, File stdout, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (directory != null) {
            pb.directory(directory);
        }
        if (stdout != null) {
            pb.redirectOutput(stdout);
        }

        Process process = pb.start();
        if (process.waitFor(timeoutInSeconds, TimeUnit.SECONDS)) {
            // process terminated before the timeout
            return process.exitValue();
        }

        // process timed out -- kill it, and return -1
        process.destroyForcibly();
        return -1;
    }

    /**
     * This class is used to recursively delete a directory and all of its contents.
     */
    private final class RecursivelyDeleteDirectory extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            file.toFile().delete();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path file, IOException ex) throws IOException {
            if (ex == null) {
                file.toFile().delete();
                return FileVisitResult.CONTINUE;
            } else {
                throw ex;
            }
        }
    }

    /* ============================================================ */

    /**
     * An instance of this class exists for each artifact that we are trying to download.
     */
    static class Artifact {
        String groupId;
        String artifactId;
        String version;
        String type;

        /**
         * Constructor - populate the 'Artifact' instance.
         *
         * @param groupId groupId of artifact
         * @param artifactId artifactId of artifact
         * @param version version of artifact
         * @param type type of the artifact (e.g. "jar")
         */
        Artifact(String groupId, String artifactId, String version, String type) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.type = type;
        }

        /**
         * Constructor - populate an 'Artifact' instance.
         *
         * @param artifact a string of the form: {@code"<groupId>/<artifactId>/<version>[/<type>]"}
         * @throws IllegalArgumentException if 'artifact' has the incorrect format
         */
        Artifact(String artifact) {
            String[] segments = artifact.split("/");
            if (segments.length != 4 && segments.length != 3) {
                throw new IllegalArgumentException("groupId/artifactId/version/type");
            }
            groupId = segments[0];
            artifactId = segments[1];
            version = segments[2];
            type = segments.length == 4 ? segments[3] : "jar";
        }

        /**
         * Returns string representation.
         *
         * @return the artifact id in the form: {@code"<groupId>/<artifactId>/<version>/<type>"}
         */
        @Override
        public String toString() {
            return groupId + "/" + artifactId + "/" + version + "/" + type;
        }
    }
}
