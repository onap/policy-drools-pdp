/*-
 * ============LICENSE_START=======================================================
 * feature-state-management
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class audits the Maven repository
 */
public class RepositoryAudit extends DroolsPDPIntegrityMonitor.AuditBase
{
  private static final long DEFAULT_TIMEOUT = 60;  // timeout in 60 seconds
  private static short failCount = 0;              // Number of consecutive total audit failures
  private static int failThreshold = 3;            // Failure threshold is 3 by default
  private static Date lastAudit = null;            // Time of lastAudit run
  private static short numReposFail = 0;
  // used to incrementally construct response as problems occur
  // (empty = no problems)
  private static StringBuilder response = new StringBuilder();

  // get an instance of logger
  private static Logger  logger = LoggerFactory.getLogger(RepositoryAudit.class);		
  // single global instance of this audit object
  static private RepositoryAudit instance = new RepositoryAudit();

  /**
   * @return the single 'RepositoryAudit' instance
   */
  public static DroolsPDPIntegrityMonitor.AuditBase getInstance()
  {
	return instance;
  }

  /**
   * Constructor - set the name to 'Repository'
   */
  private RepositoryAudit()
  {
	super("Repository");
  }
  
  
  /**
	 * Only when we exceed fail threshold, set the response string to the specified value
	 * This implies a failure has occurred
	 */
	public void auditFail(String value)
	{
	  failCount++;
	  if(failCount >= failThreshold){
		  if(logger.isDebugEnabled()){
			  logger.debug("RepositoryAudit: Threshold failures reached. Total audit failure");
		  }
		  super.setResponse(value);
		  //Note: We don't reset the fail count until the audit passes. See end of audit loop.

	  }
	}
  
  private void repoFail(String msg, int len){
	  // If all repos urls failed, then the audit failed
	  numReposFail++;
	  response.append(msg);

	  if(numReposFail == len){
		  if(logger.isDebugEnabled()){
			  logger.debug("RepositoryAudit: All repos failed. Setting response... single audit failure");
		  }
		  auditFail(response.toString());
		  numReposFail = 0;
		  response = new StringBuilder();
	  }
  }
  
  /**
   * Grab a property and trim it.
   *
   * @param properties properties to be passed to the audit
   */
  private String trimmedProp(String key){
	  String valueStr = null;
	  try
	  {
		  valueStr = StateManagementProperties.getProperty(key);
	  }
	  catch (Exception e)
	  {
		  logger.warn("RepositoryAudit.readProp: Invalid property = {}", key);
		  return null;
	  }
	  if(valueStr != null){
		  valueStr = valueStr.trim();
	  }
	  return valueStr;
  }

  /**
   * Invoke the audit
   *
   * @param properties properties to be passed to the audit
   */
  @Override
  public void invoke(Properties properties)
		  throws IOException, InterruptedException
  {
	  boolean ignoreErrors = true;		// ignore errors by default

	  String repoAuditIgnoreErrors = trimmedProp("repository.audit.ignore.errors");

	  if (repoAuditIgnoreErrors != null)
	  {
		  try
		  {
			  ignoreErrors = Boolean.parseBoolean(repoAuditIgnoreErrors);
		  }
		  catch (Exception e)
		  {
			  ignoreErrors = true;
			  logger.warn("RepositoryAudit.invoke: Ignoring invalid property: repository.audit.ignore.errors = {}", repoAuditIgnoreErrors);
		  }
	  }

	  // Check if repo audit is enabled
	  boolean isActive = false;
	  String repoAuditIsActive = trimmedProp("repository.audit.is.active");
	  if (repoAuditIsActive != null) {
		  try {
			  isActive = Boolean.parseBoolean(repoAuditIsActive);
		  } catch (Exception e) {
			  if(ignoreErrors){
				  logger.warn("RepositoryAudit.invoke: Ignoring invalid property: repository.audit.is.active = {} and using default value of 'false'", repoAuditIsActive);
			  }else{
				  logger.error("RepositoryAudit.invoke: Invalid property: repository.audit.is.active = {}, exiting audit", repoAuditIsActive);
				  return;
			  }
		  }
	  }

	  logger.debug("RepositoryAudit.invoke: repoAuditIsActive = {}" 
			  + ", repoAuditIgnoreErrors = {}",repoAuditIsActive, repoAuditIgnoreErrors);

	  if(!isActive){
		  logger.info("RepositoryAudit.invoke: exiting because isActive = {}", isActive);
		  return;
	  }

	  long interval_ms = 86400000L; //Default to 1 day.
	  String intervalString = trimmedProp("repository.audit.interval_sec");
	  try{
		  interval_ms = 1000 * Long.valueOf(intervalString);
	  }
	  catch (NumberFormatException e){
		  if(ignoreErrors){
			  logger.warn("RepositoryAudit: Invalid 'repository.audit.interval_sec' value {}: using default value {}", intervalString, interval_ms/1000, e);
		  }else{
			  logger.error("RepositoryAudit: Invalid 'repository.audit.interval_sec' value: '{}'   .Exiting audit.", intervalString, e);
			  return;
		  }
	  }

	  // Check interval
	  if(lastAudit != null){
		  long now_ms = new Date().getTime();

		  // Skip audit if interval amount of seconds have not passed
		  if((now_ms - lastAudit.getTime()) < interval_ms){
			  // If the last audit was a failure, continue failing
			  if(failCount >= failThreshold){
				  super.setResponse("RepositoryAudit: Continuing previous failure. Next interval not reached yet."); 
			  }
			  return;
		  }
	  }

	  if(logger.isDebugEnabled()){  
		  logger.debug("Running 'RepositoryAudit.invoke'");
	  }

	  class Repo{
		  String id;
		  String url;
		  String user;
		  String pass;

		  Repo(String id, String url, String user, String pass){
			  this.id = id;
			  this.url = url;
			  this.user = user;
			  this.pass = pass;
		  }
		  boolean notNull(){
			  return (id   != null && 
					  url   != null && 
					  user  != null && 
					  pass  != null);
		  }
	  }

	  //
	  // Fetch repository information from 'IntegrityMonitorProperties'
	  //

	  String thresholdString =trimmedProp("repository.audit.failure.threshold");
	  try{
		  failThreshold = Integer.valueOf(thresholdString);
	  }
	  catch (NumberFormatException e){
		  // Use the default value
		  if(ignoreErrors){
			  logger.warn("RepositoryAudit: Invalid 'repository.audit.failure.threshold' value: '{}'  .using default value of {}.", thresholdString, failThreshold, e);
		  }else{	
			  logger.error("RepositoryAudit: Invalid 'repository.audit.failure.threshold' value: '{}'  .Exiting audit.", thresholdString, e);
			  return;
		  }
	  }

	  // Repo 1
	  Repo repo1 = null;
	  try{
		  repo1 = new Repo(trimmedProp("repository.audit.id"),
				  trimmedProp("repository.audit.url"),
				  trimmedProp("repository.audit.username"),
				  trimmedProp("repository.audit.password"));
	  }catch (Exception e){
		  logger.warn("RepositoryAudit: Invalid or no repository 1 found: ", e);
	  }
	  
	  // Repo 2
	  Repo repo2 = null;
	  try{
		  repo2 = new Repo(trimmedProp("repository2.audit.id"),
				  trimmedProp("repository2.audit.url"),
				  trimmedProp("repository2.audit.username"),
				  trimmedProp("repository2.audit.password"));
	  }catch (Exception e){
		  logger.warn("RepositoryAudit: Invalid or no repository 2 found: ", e);
	  }

	  // List of repos to audit
	  ArrayList<Repo> repos = new ArrayList<Repo>();
	  
	  int cnt = 0;
	  if(repo1 == null){
		  cnt++;
	  }else if(!repo1.notNull()){
		  cnt++;
	  }else{
		  repos.add(repo1);
	  }
	  
	  if(repo2 == null){
		  cnt++;
	  }else if(!repo2.notNull()){
		  cnt++;
	  }else{
		  repos.add(repo2);
	  }

	  if(cnt == 2){
		  //nothing to audit
		  logger.info("RepositoryAudit: No repository defined to audit");
		  return;
	  }

	  long timeoutInSeconds = DEFAULT_TIMEOUT;
	  String timeoutString = trimmedProp("repository.audit.timeout");
	  if (timeoutString != null && !timeoutString.isEmpty())
	  {
		  try
		  {
			  timeoutInSeconds = Long.valueOf(timeoutString);
		  }
		  catch (NumberFormatException e)
		  {
			  if(ignoreErrors){
				  logger.warn("RepositoryAudit: Invalid 'repository.audit.timeout' value: '{}' using default value of {}", timeoutString, timeoutInSeconds, e);
			  }else{
				  logger.error("RepositoryAudit: Invalid 'repository.audit.timeout' value: '{}'. Exiting Audit", timeoutString, e);
				  return;
			  }
		  }
	  }

	  lastAudit = new Date();

	  // Perform audit on each repository

	  for(Repo repository : repos)
	  {
		  if(logger.isDebugEnabled()){ 
			  logger.debug("RepositoryAudit: Starting audit of repo {}", repository.url);
		  }
		  StringBuilder failMsg = new StringBuilder();

		  // artifacts to be downloaded
		  LinkedList<Artifact> artifacts = new LinkedList<>();

		  /*
		   * 1) create temporary directory
		   */
		  Path dir = Files.createTempDirectory("auditRepo");
		  logger.info("RepositoryAudit: temporary directory = {}", dir);

		  // nested 'pom.xml' file and 'repo' directory
		  Path pom = dir.resolve("pom.xml");
		  Path repo = dir.resolve("repo");

		  /*
		   * 2) Create test file, and upload to repository
		   *    (only if repository information is specified)
		   */
		  String groupId = null;
		  String artifactId = null;
		  String version = null;
		  groupId = "org.onap.policy.audit";
		  artifactId = "repository-audit";
		  version = "0." + System.currentTimeMillis();

		  if (repository.url.toLowerCase().contains("snapshot"))
		  {
			  // use SNAPSHOT version
			  version += "-SNAPSHOT";
		  }

		  // create text file to write
		  FileOutputStream fos =
				  new FileOutputStream(dir.resolve("repository-audit.txt").toFile());
		  try
		  {
			  fos.write(version.getBytes());
		  }
		  finally
		  {
			  fos.close();
		  }

		  // try to install file in repository
		  if (runProcess
				  (timeoutInSeconds, dir.toFile(), null,
						  "mvn", "deploy:deploy-file",
						  "-DrepositoryId=" + repository.id,
						  "-Durl=" + repository.url,
						  "-Dfile=repository-audit.txt",
						  "-DgroupId=" + groupId,
						  "-DartifactId=" + artifactId,
						  "-Dversion=" + version,
						  "-Dpackaging=txt",
						  "-DgeneratePom=false") != 0)
		  {
			  logger.error
			  ("RepositoryAudit: 'mvn deploy:deploy-file' failed");
			  if (!ignoreErrors)
			  {
				  failMsg.append("'mvn deploy:deploy-file' failed\n");
			  }
		  }
		  else
		  {
			  logger.info
			  ("RepositoryAudit: 'mvn deploy:deploy-file succeeded");

			  // we also want to include this new artifact in the download
			  // test (steps 3 and 4)
			  artifacts.add(new Artifact(groupId, artifactId, version, "txt"));
		  }

		  /*
		   * 3) create 'pom.xml' file in temporary directory
		   */
		  artifacts.add(new Artifact("org.apache.maven/maven-embedder/3.2.2"));

		  StringBuilder sb = new StringBuilder();
		  sb.append
		  ("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
				  + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n"
				  + "\n"
				  + "  <modelVersion>4.0.0</modelVersion>\n"
				  + "  <groupId>empty</groupId>\n"
				  + "  <artifactId>empty</artifactId>\n"
				  + "  <version>1.0-SNAPSHOT</version>\n"
				  + "  <packaging>pom</packaging>\n"
				  + "\n"
				  + "  <build>\n"
				  + "    <plugins>\n"
				  + "      <plugin>\n"
				  + "         <groupId>org.apache.maven.plugins</groupId>\n"
				  + "         <artifactId>maven-dependency-plugin</artifactId>\n"
				  + "         <version>2.10</version>\n"
				  + "         <executions>\n"
				  + "           <execution>\n"
				  + "             <id>copy</id>\n"
				  + "             <goals>\n"
				  + "               <goal>copy</goal>\n"
				  + "             </goals>\n"
				  + "             <configuration>\n"
				  + "               <localRepositoryDirectory>")
				  .append(repo)
				  .append("</localRepositoryDirectory>\n")
				  .append("               <artifactItems>\n");
		  for (Artifact artifact : artifacts)
		  {
			  // each artifact results in an 'artifactItem' element
			  sb.append
			  ("                 <artifactItem>\n"
					  + "                   <groupId>")
					  .append(artifact.groupId)
					  .append
					  ("</groupId>\n"
							  + "                   <artifactId>")
							  .append(artifact.artifactId)
							  .append
							  ("</artifactId>\n"
									  + "                   <version>")
									  .append(artifact.version)
									  .append
									  ("</version>\n"
											  + "                   <type>")
											  .append(artifact.type)
											  .append
											  ("</type>\n"
													  + "                 </artifactItem>\n");
		  }
		  sb.append
		  ("               </artifactItems>\n"
				  + "             </configuration>\n"
				  + "           </execution>\n"
				  + "         </executions>\n"
				  + "      </plugin>\n"
				  + "    </plugins>\n"
				  + "  </build>\n"
				  + "</project>\n");
		  fos = new FileOutputStream(pom.toFile());
		  try
		  {
			  fos.write(sb.toString().getBytes());
		  }
		  finally
		  {
			  fos.close();
		  }

		  /*
		   * 4) Invoke external 'mvn' process to do the downloads
		   */

		  // output file = ${dir}/out (this supports step '4a')
		  File output = dir.resolve("out").toFile();

		  // invoke process, and wait for response
		  int rval = runProcess
				  (timeoutInSeconds, dir.toFile(), output, "mvn", "compile");
		  logger.info("RepositoryAudit: 'mvn' return value = {}", rval);
		  if (rval != 0)
		  {
			  logger.error
			  ("RepositoryAudit: 'mvn compile' invocation failed");
			  if (!ignoreErrors)
			  {
				  failMsg.append("'mvn compile' invocation failed\n");
			  }
		  }

		  /*
		   * 4a) Check attempted and successful downloads from output file
		   *     Note: at present, this step just generates log messages,
		   *     but doesn't do any verification.
		   */
		  if (rval == 0)
		  {
			  // place output in 'fileContents' (replacing the Return characters
			  // with Newline)
			  byte[] outputData = new byte[(int)output.length()];
			  String fileContents;
			  try (FileInputStream fis = new FileInputStream(output)) {
				  //
				  // Ideally this should be in a loop or even better use
				  // Java 8 nio functionality.
				  //
				  int bytesRead = fis.read(outputData);
				  logger.info("fileContents read {} bytes", bytesRead);
				  fileContents = new String(outputData).replace('\r','\n');
			  }

			  // generate log messages from 'Downloading' and 'Downloaded'
			  // messages within the 'mvn' output
			  int index = 0;
			  while ((index = fileContents.indexOf("\nDown", index)) > 0)
			  {
				  index += 5;
				  if (fileContents.regionMatches(index, "loading: ", 0, 9))
				  {
					  index += 9;
					  int endIndex = fileContents.indexOf('\n', index);
					  logger.info
					  ("RepositoryAudit: Attempted download: '{}'", fileContents.substring(index, endIndex));
					  index = endIndex;
				  }
				  else if (fileContents.regionMatches(index, "loaded: ", 0, 8))
				  {
					  index += 8;
					  int endIndex = fileContents.indexOf(' ', index);
					  logger.info
					  ("RepositoryAudit: Successful download: '{}'",fileContents.substring(index, endIndex));
					  index = endIndex;
				  }
			  }
		  }

		  /*
		   * 5) Check the contents of the directory to make sure the downloads
		   *    were successful
		   */
		  for (Artifact artifact : artifacts)
		  {
			  if (repo.resolve(artifact.groupId.replace('.','/'))
					  .resolve(artifact.artifactId)
					  .resolve(artifact.version)
					  .resolve(artifact.artifactId + "-" + artifact.version + "."
							  + artifact.type).toFile().exists())
			  {
				  // artifact exists, as expected
				  logger.info("RepositoryAudit: {} : exists", artifact.toString());
			  }
			  else
			  {
				  // Audit ERROR: artifact download failed for some reason
				  logger.error("RepositoryAudit: {}: does not exist", artifact.toString());
				  if (!ignoreErrors)
				  {
					  failMsg.append("Failed to download artifact: ")
					  .append(artifact).append('\n');
				  }
			  }
		  }

		  /*
		   * 6) Use 'curl' to delete the uploaded test file
		   *    (only if repository information is specified)
		   */
		  if (runProcess
				  (timeoutInSeconds, dir.toFile(), null,
						  "curl",
						  "--request", "DELETE",
						  "--user", repository.user + ":" + repository.pass,
						  repository.url + "/" + groupId.replace('.', '/') + "/" +
								  artifactId + "/" + version)
								  != 0)
		  {
			  logger.error
			  ("RepositoryAudit: delete of uploaded artifact failed");
			  if (!ignoreErrors)
			  {
				  failMsg.append("delete of uploaded artifact failed\n");
			  }
		  }
		  else
		  {
			  logger.info
			  ("RepositoryAudit: delete of uploaded artifact succeeded");
			  artifacts.add(new Artifact(groupId, artifactId, version, "txt"));
		  }

		  /*
		   * 7) Remove the temporary directory
		   */
		  Files.walkFileTree
		  (dir,
				  new SimpleFileVisitor<Path>()
				  {
			  @Override
			  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
			  {
				  // logger.info("RepositoryAudit: Delete " + file);
				  file.toFile().delete();
				  return FileVisitResult.CONTINUE;
			  }

			  @Override
			  public FileVisitResult postVisitDirectory(Path file, IOException e)
					  throws IOException
			  {
				  if (e == null)
				  {
					  // logger.info("RepositoryAudit: Delete " + file);
					  file.toFile().delete();
					  return FileVisitResult.CONTINUE;
				  }
				  else
				  {
					  throw e;
				  }
			  }
				  });
		  
		  	// A single repo failed, set error and keep going
			if(!failMsg.toString().isEmpty()){
		  		if(logger.isDebugEnabled()){
		  			logger.debug("RepositoryAudit: Single repo failed. Continuing audit");
		  		}
			   repoFail(failMsg.toString(), repos.size()); 
			}
		  	// A single repo passed, entire audit is pass
		  	else{
				if(logger.isDebugEnabled()){
		  			logger.debug("RepositoryAudit: Single repo passed. Audit pass");
		  		}
			  numReposFail = 0;
			  response = new StringBuilder(); 
			  failCount = 0; 
			  return; 
			}
	  } //End of for(Repo repository : repos) loop
  }

  /**
   * Run a process, and wait for the response
   *
   * @param timeoutInSeconds the number of seconds to wait for the
   *	process to terminate
   * @param directory the execution directory of the process
   *	(null = current directory)
   * @param stdout the file to contain the standard output
   *	(null = discard standard output)
   * @param command command and arguments
   * @return the return value of the process
   * @throws IOException, InterruptedException
   */
  static int runProcess(long timeoutInSeconds,
		  File directory, File stdout, String... command)
				  throws IOException, InterruptedException
  {
	  ProcessBuilder pb = new ProcessBuilder(command);
	  if (directory != null)
	  {
		  pb.directory(directory);
	  }
	  if (stdout != null)
	  {
		  pb.redirectOutput(stdout);
	  }

	  Process process = pb.start();
	  if (process.waitFor(timeoutInSeconds, TimeUnit.SECONDS))
	  {
		  // process terminated before the timeout
		  return process.exitValue();
	  }

	  // process timed out -- kill it, and return -1
	  process.destroyForcibly();
	  return -1;
  }

  /* ============================================================ */

  /**
   * An instance of this class exists for each artifact that we are trying
   * to download.
   */
  static class Artifact
  {
	  String groupId, artifactId, version, type;

	  /**
	   * Constructor - populate the 'Artifact' instance
	   *
	   * @param groupId groupId of artifact
	   * @param artifactId artifactId of artifact
	   * @param version version of artifact
	   * @param type type of the artifact (e.g. "jar")
	   */
	  Artifact(String groupId, String artifactId, String version, String type)
	  {
		  this.groupId = groupId;
		  this.artifactId = artifactId;
		  this.version = version;
		  this.type = type;
	  }

	  /**
	   * Constructor - populate an 'Artifact' instance
	   *
	   * @param artifact a string of the form:
	   *		"<groupId>/<artifactId>/<version>[/<type>]"
	   * @throws IllegalArgumentException if 'artifact' has the incorrect format
	   */
	  Artifact(String artifact)
	  {
		  String[] segments = artifact.split("/");
		  if (segments.length != 4 && segments.length != 3)
		  {
			  throw new IllegalArgumentException("groupId/artifactId/version/type");
		  }
		  groupId = segments[0];
		  artifactId = segments[1];
		  version = segments[2];
		  type = segments.length == 4 ? segments[3] : "jar";
	  }

	  /**
	   * @return the artifact id in the form:
	   *		"<groupId>/<artifactId>/<version>/<type>"
	   */
	  @Override
	  public String toString()
	  {
		  return groupId + "/" + artifactId + "/" + version + "/" + type;
	  }
  }
}
