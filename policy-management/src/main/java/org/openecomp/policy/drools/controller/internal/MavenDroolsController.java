/*-
 * ============LICENSE_START=======================================================
 * policy-management
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

package org.openecomp.policy.drools.controller.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.drools.core.ClassObjectFilter;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.controller.DroolsController;
import org.openecomp.policy.drools.core.PolicyContainer;
import org.openecomp.policy.drools.core.PolicySession;
import org.openecomp.policy.drools.core.jmx.PdpJmx;
import org.openecomp.policy.drools.event.comm.TopicSink;
import org.openecomp.policy.drools.protocol.coders.EventProtocolCoder;
import org.openecomp.policy.drools.protocol.coders.JsonProtocolFilter;
import org.openecomp.policy.drools.protocol.coders.TopicCoderFilterConfiguration;
import org.openecomp.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.openecomp.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomJacksonCoder;
import org.openecomp.policy.drools.protocol.coders.TopicCoderFilterConfiguration.PotentialCoderFilter;
import org.openecomp.policy.drools.utils.ReflectionUtil;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Maven-based Drools Controller that interacts with the 
 * policy-core PolicyContainer and PolicySession to manage
 * Drools containers instantiated using Maven.
 */
public class MavenDroolsController implements DroolsController {
	
	/**
	 * logger 
	 */
	private static Logger  logger = FlexLogger.getLogger(MavenDroolsController.class);
	
	/**
	 * Policy Container, the access object to the policy-core layer
	 */
	@JsonIgnore
	protected final PolicyContainer policyContainer;
	
	/**
	 * alive status of this drools controller, 
	 * reflects invocation of start()/stop() only
	 */
	protected volatile boolean alive = false;
	
	/**
	 * locked status of this drools controller,
	 * reflects if i/o drools related operations are permitted,
	 * more specifically: offer() and deliver().
	 * It does not affect the ability to start and stop 
	 * underlying drools infrastructure
	 */
	protected volatile boolean locked = false;
	
	/**
	 * list of topics, each with associated decoder classes, each
	 * with a list of associated filters.
	 */
	protected List<TopicCoderFilterConfiguration> decoderConfigurations;
	
	/**
	 * list of topics, each with associated encoder classes, each
	 * with a list of associated filters.
	 */
	protected List<TopicCoderFilterConfiguration> encoderConfigurations;
	
	/**
	 * recent source events processed
	 */
	protected final CircularFifoQueue<Object> recentSourceEvents = new CircularFifoQueue<Object>(10);
	
	/**
	 * recent sink events processed
	 */
	protected final CircularFifoQueue<String> recentSinkEvents = new CircularFifoQueue<String>(10);
	
	/**
	 * original Drools Model/Rules classloader hash
	 */
	protected int modelClassLoaderHash;

	/**
	 * Expanded version of the constructor 
	 * 
	 * @param groupId maven group id
	 * @param artifactId maven artifact id
	 * @param version maven version
	 * @param decoderConfiguration list of topic -> decoders -> filters mapping
	 * @param encoderConfiguration list of topic -> encoders -> filters mapping
	 * 
	 * @throws IllegalArgumentException invalid arguments passed in
	 */
	public MavenDroolsController(String groupId, 
								 String artifactId, 
								 String version,
								 List<TopicCoderFilterConfiguration> decoderConfigurations,
								 List<TopicCoderFilterConfiguration> encoderConfigurations) 
		   throws IllegalArgumentException {
		
		if (logger.isInfoEnabled())
			logger.info("DROOLS CONTROLLER: instantiation " +  this + 
					    " -> {" + groupId + ":" + artifactId + ":" + version + "}");
		
		if (groupId == null || artifactId == null || version == null ||
			groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
			throw new IllegalArgumentException("Missing maven coordinates: " + 
					                           groupId + ":" + artifactId + ":" + 
					                           version);
		}
		
		this.policyContainer= new PolicyContainer(groupId, artifactId, version);
		this.init(decoderConfigurations, encoderConfigurations);
		
		if (logger.isInfoEnabled())
			logger.info("DROOLS CONTROLLER: instantiation completed " +  this);
	}
	
	/**
	 * init encoding/decoding configuration
	 * @param decoderConfiguration list of topic -> decoders -> filters mapping
	 * @param encoderConfiguration list of topic -> encoders -> filters mapping
	 */
	protected void init(List<TopicCoderFilterConfiguration> decoderConfigurations,
			            List<TopicCoderFilterConfiguration> encoderConfigurations) {
		
		this.decoderConfigurations = decoderConfigurations;
		this.encoderConfigurations = encoderConfigurations;
		
		this.initCoders(decoderConfigurations, true);
		this.initCoders(encoderConfigurations, false);
		
		this.modelClassLoaderHash = this.policyContainer.getClassLoader().hashCode();		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateToVersion(String newGroupId, String newArtifactId, String newVersion,
			                    List<TopicCoderFilterConfiguration> decoderConfigurations,
			                    List<TopicCoderFilterConfiguration> encoderConfigurations) 
		throws IllegalArgumentException, LinkageError, Exception {
		
		if (logger.isInfoEnabled())
			logger.info("UPDATE-TO-VERSION: " +  this + " -> {" + newGroupId + ":" + newArtifactId + ":" + newVersion + "}");
		
		if (newGroupId == null || newArtifactId == null || newVersion == null ||
			newGroupId.isEmpty() || newArtifactId.isEmpty() || newVersion.isEmpty()) {
				throw new IllegalArgumentException("Missing maven coordinates: " + 
						                           newGroupId + ":" + newArtifactId + ":" + 
						                           newVersion);
		}
		
		if (newGroupId.equalsIgnoreCase(DroolsController.NO_GROUP_ID) || 
			newArtifactId.equalsIgnoreCase(DroolsController.NO_ARTIFACT_ID) || 
			newVersion.equalsIgnoreCase(DroolsController.NO_VERSION)) {
				throw new IllegalArgumentException("BRAINLESS maven coordinates provided: " + 
						                           newGroupId + ":" + newArtifactId + ":" + 
						                           newVersion);
		}
		
		if (newGroupId.equalsIgnoreCase(this.getGroupId()) && 
			newArtifactId.equalsIgnoreCase(this.getArtifactId()) &&
			newVersion.equalsIgnoreCase(this.getVersion())) {
				logger.warn("Al in the right version: " + newGroupId + ":" + 
			                newArtifactId + ":" +  newVersion + " vs. " + this);
				return;
		}
		
		if (!newGroupId.equalsIgnoreCase(this.getGroupId()) || 
			!newArtifactId.equalsIgnoreCase(this.getArtifactId())) {
				throw new IllegalArgumentException("Group ID and Artifact ID maven coordinates must be identical for the upgrade: " + 
							                       newGroupId + ":" + newArtifactId + ":" + 
							                       newVersion + " vs. " + this);
		}

		/* upgrade */
		String messages = this.policyContainer.updateToVersion(newVersion);
		if (logger.isWarnEnabled())
			logger.warn(this + "UPGRADE results: " + messages);
		
		/*
		 * If all sucessful (can load new container), now we can remove all coders from previous sessions
		 */
		this.removeCoders();
		
		/*
		 * add the new coders
		 */
		this.init(decoderConfigurations, encoderConfigurations);
		
		if (logger.isInfoEnabled())
			logger.info("UPDATE-TO-VERSION: completed " +  this);
	}

	/**
	 * initialize decoders for all the topics supported by this controller
	 * Note this is critical to be done after the Policy Container is
	 * instantiated to be able to fetch the corresponding classes.
	 * 
	 * @param decoderConfiguration list of topic -> decoders -> filters mapping
	 */
	protected void initCoders(List<TopicCoderFilterConfiguration> coderConfigurations, 
			                  boolean decoder) 
	          throws IllegalArgumentException {
		
		if (logger.isInfoEnabled())
			logger.info("INIT-CODERS: " +  this);
		
		if (coderConfigurations == null) {
			return;
		}
			

		for (TopicCoderFilterConfiguration coderConfig: coderConfigurations) {
			String topic = coderConfig.getTopic();
			
			CustomGsonCoder customGsonCoder = coderConfig.getCustomGsonCoder();
			if (coderConfig.getCustomGsonCoder() != null && 
				coderConfig.getCustomGsonCoder().getClassContainer() != null &&
				!coderConfig.getCustomGsonCoder().getClassContainer().isEmpty()) {
					
				String customGsonCoderClass = coderConfig.getCustomGsonCoder().getClassContainer();
				if (!ReflectionUtil.isClass(this.policyContainer.getClassLoader(),
						                   customGsonCoderClass)) {
					logger.error(customGsonCoderClass + " cannot be retrieved");
					throw new IllegalArgumentException(customGsonCoderClass + " cannot be retrieved");
				} else {
					if (logger.isInfoEnabled())
						logger.info("CLASS FETCHED " + customGsonCoderClass);
				}
			}
			
			CustomJacksonCoder customJacksonCoder = coderConfig.getCustomJacksonCoder();
			if (coderConfig.getCustomJacksonCoder() != null && 
				coderConfig.getCustomJacksonCoder().getClassContainer() != null &&
				!coderConfig.getCustomJacksonCoder().getClassContainer().isEmpty()) {
				
				String customJacksonCoderClass = coderConfig.getCustomJacksonCoder().getClassContainer();
				if (!ReflectionUtil.isClass(this.policyContainer.getClassLoader(),
						                   customJacksonCoderClass)) {
					logger.error(customJacksonCoderClass + " cannot be retrieved");
					throw new IllegalArgumentException(customJacksonCoderClass + " cannot be retrieved");
				} else {
					if (logger.isInfoEnabled())
						logger.info("CLASS FETCHED " + customJacksonCoderClass);
				}
			}	
			
			List<PotentialCoderFilter> coderFilters = coderConfig.getCoderFilters();
			if (coderFilters == null || coderFilters.isEmpty()) {
				continue;
			}
			
			for (PotentialCoderFilter coderFilter : coderFilters) {
				String potentialCodedClass = coderFilter.getCodedClass();
				JsonProtocolFilter protocolFilter = coderFilter.getFilter();
				
				if (!ReflectionUtil.isClass(this.policyContainer.getClassLoader(), 
						                   potentialCodedClass)) {
					logger.error(potentialCodedClass + " cannot be retrieved");
					throw new IllegalArgumentException(potentialCodedClass + " cannot be retrieved");
				} else {
					if (logger.isInfoEnabled())
						logger.info("CLASS FETCHED " + potentialCodedClass);
				}			
				
				if (decoder)
					EventProtocolCoder.manager.addDecoder(this.getGroupId(), this.getArtifactId(), 
							                              topic, potentialCodedClass, protocolFilter,
							                              customGsonCoder,
							                              customJacksonCoder,
							                              this.policyContainer.getClassLoader().hashCode());
				else
					EventProtocolCoder.manager.addEncoder(this.getGroupId(), this.getArtifactId(), 
                                                          topic, potentialCodedClass, protocolFilter,
							                              customGsonCoder,
							                              customJacksonCoder,
							                              this.policyContainer.getClassLoader().hashCode());
			}
		}
	}
	

	/**
	 * remove decoders.
	 */
	protected void removeDecoders() 
	          throws IllegalArgumentException {
		if (logger.isInfoEnabled())
			logger.info("REMOVE-DECODERS: " +  this);
		
		if (this.decoderConfigurations == null) {
			return;
		}
			

		for (TopicCoderFilterConfiguration coderConfig: decoderConfigurations) {
			String topic = coderConfig.getTopic();								
			EventProtocolCoder.manager.removeDecoders
						(this.getGroupId(), this.getArtifactId(), topic);
		}
	}
	
	/**
	 * remove decoders.
	 */
	protected void removeEncoders() 
	          throws IllegalArgumentException {
		
		if (logger.isInfoEnabled())
			logger.info("REMOVE-ENCODERS: " +  this);
		
		if (this.encoderConfigurations == null)
			return;
			

		for (TopicCoderFilterConfiguration coderConfig: encoderConfigurations) {
			String topic = coderConfig.getTopic();								
			EventProtocolCoder.manager.removeEncoders
						(this.getGroupId(), this.getArtifactId(), topic);
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean ownsCoder(Class<? extends Object> coderClass, int modelHash) throws IllegalStateException {
		if (!ReflectionUtil.isClass
				(this.policyContainer.getClassLoader(), coderClass.getCanonicalName())) {
			logger.error(this + coderClass.getCanonicalName() + " cannot be retrieved. ");
			return false;
		}
		
		if (modelHash == this.modelClassLoaderHash) {
			if (logger.isInfoEnabled())
				logger.info(coderClass.getCanonicalName() + 
						    this + " class loader matches original drools controller rules classloader " +
			                coderClass.getClassLoader());
			return true;
		} else {
			if (logger.isWarnEnabled())
				logger.warn(this + coderClass.getCanonicalName() + " class loaders don't match  " +
	                        coderClass.getClassLoader() + " vs " + 
						    this.policyContainer.getClassLoader());
			return false;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean start() {
		
		if (logger.isInfoEnabled())
			logger.info("START: " + this);
		
		synchronized (this) {			
			if (this.alive)
				return true;
			
			this.alive = true;
		}
		
		return this.policyContainer.start();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean stop() {
		
		logger.info("STOP: " + this);
		
		synchronized (this) {
			if (!this.alive)
				return true;
			
			this.alive = false;
		}
		
		return this.policyContainer.stop();
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() throws IllegalStateException {
		
		if (logger.isInfoEnabled())
			logger.info(this + "SHUTDOWN");
		
		try {
			this.stop();
			this.removeCoders();
		} catch (Exception e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "stop", this.toString());
		} finally {
			this.policyContainer.shutdown();
		}

	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void halt() throws IllegalStateException {
		if (logger.isInfoEnabled())
			logger.info(this + "SHUTDOWN");
		
		try {
			this.stop();
			this.removeCoders();
		} catch (Exception e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "halt", this.toString());
		} finally {
			this.policyContainer.destroy();
		}	
	}
	
	/**
	 * removes this drools controllers and encoders and decoders from operation
	 */
	protected void removeCoders() {
		
		if (logger.isInfoEnabled())
			logger.info(this + "REMOVE-CODERS");
		
		try {
			this.removeDecoders();
		} catch (IllegalArgumentException e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "removeDecoders", this.toString());
		}
		
		try {
			this.removeEncoders();
		} catch (IllegalArgumentException e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "removeEncoders", this.toString());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAlive() {
		return this.alive;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean offer(String topic, String event) {

		if (logger.isInfoEnabled())
			logger.info("OFFER: " + topic + ":" + event + " INTO " + this);
		
		if (this.locked)
			return true;
		
		if (!this.alive)
			return true;
		
		// 0. Check if the policy container has any sessions
		
		if (this.policyContainer.getPolicySessions().size() <= 0) {
			// no sessions
			return true;
		}
		
		// 1. Now, check if this topic has a decoder:
		
		if (!EventProtocolCoder.manager.isDecodingSupported(this.getGroupId(), 
				                                              this.getArtifactId(), 
				                                              topic)) {
			
			logger.warn("DECODING-UNSUPPORTED: " + ":" + this.getGroupId() + 
					          ":" + this.getArtifactId() + ":" + topic + " IN " + this);
			return true;
		}
		
		// 2. Decode
		
		Object anEvent;
		try {
			anEvent = EventProtocolCoder.manager.decode(this.getGroupId(), 
					                                      this.getArtifactId(), 
					                                      topic, 
					                                      event);
		} catch (UnsupportedOperationException uoe) {
			if (logger.isInfoEnabled())
				logger.info("DECODE:"+ this + ":" + topic + ":" + event);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("DECODE:"+ this + ":" + topic + ":" + event);
			return true;
		}
		
		synchronized(this.recentSourceEvents) {
			this.recentSourceEvents.add(anEvent);
		}
		
		// increment event count for Nagios monitoring
		PdpJmx.getInstance().updateOccured();  
		
		// Broadcast
		
		if (logger.isInfoEnabled())
			logger.info(this + "BROADCAST-INJECT of " + event + " FROM " + topic + " INTO " + this.policyContainer.getName());		
		
		if (!this.policyContainer.insertAll(anEvent))
			logger.warn(this + "Failed to inject into PolicyContainer " + this.getSessionNames());
		
		return true;
	}	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(TopicSink sink, Object event) 
			throws IllegalArgumentException, 
			       IllegalStateException, 
		           UnsupportedOperationException {
		
		if (logger.isInfoEnabled())
			logger.info(this + "DELIVER: " +  event + " FROM " + this + " TO " + sink);
		
		if (sink == null)
			throw new IllegalArgumentException
						(this +  " invalid sink");
		
		if (event == null)
			throw new IllegalArgumentException
						(this +  " invalid event");
		
		if (this.locked)
			throw new IllegalStateException
							(this +  " is locked");
		
		if (!this.alive)
			throw new IllegalStateException
							(this +  " is stopped");
		
		String json =
				EventProtocolCoder.manager.encode(sink.getTopic(), event, this);
		
		synchronized(this.recentSinkEvents) {
			this.recentSinkEvents.add(json);
		}
		
		return sink.send(json);
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getVersion() {
		return this.policyContainer.getVersion();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getArtifactId() {
		return this.policyContainer.getArtifactId();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getGroupId() {
		return this.policyContainer.getGroupId();
	}

	/**
	 * @return the modelClassLoaderHash
	 */
	public int getModelClassLoaderHash() {
		return modelClassLoaderHash;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean lock() {
		logger.info("LOCK: " +  this);
		
		this.locked = true;
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean unlock() {
		logger.info("UNLOCK: " +  this);
		
		this.locked = false;
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isLocked() {
		return this.locked;
	}
	
	/**
	 * gets the policy container
	 * @return the underlying container
	 */
	@JsonIgnore
	protected PolicyContainer getContainer() {
		return this.policyContainer;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getSessionNames() {
		return getSessionNames(true);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getCanonicalSessionNames() {
		return getSessionNames(false);
	}
	
	/**
	 * get session names
	 * @param abbreviated true for the short form, otherwise the long form
	 * @return session names
	 */
	protected List<String> getSessionNames(boolean abbreviated) {
		List<String> sessionNames = new ArrayList<String>();
		try {
			for (PolicySession session: this.policyContainer.getPolicySessions()) {
				if (abbreviated)
					sessionNames.add(session.getName());
				else
					sessionNames.add(session.getFullName());
			}
		} catch (Exception e) {
			logger.warn("Can't retrieve CORE sessions: " + e.getMessage(), e);
			sessionNames.add(e.getMessage());
		}
		return sessionNames;
	}
	
	/**
	 * provides the underlying core layer container sessions
	 * 
	 * @return the attached Policy Container
	 */
	protected List<PolicySession> getSessions() {
		List<PolicySession> sessions = new ArrayList<PolicySession>();
		sessions.addAll(this.policyContainer.getPolicySessions());
		return sessions;
	}
	
	/**
	 * provides the underlying core layer container session with name sessionName
	 * 
	 * @param sessionName session name
	 * @return the attached Policy Container
	 * @throws IllegalArgumentException when an invalid session name is provided
	 * @throws IllegalStateException when the drools controller is in an invalid state
	 */
	protected PolicySession getSession(String sessionName) {
		if (sessionName == null || sessionName.isEmpty())
			throw new IllegalArgumentException("A Session Name must be provided");
		
		List<PolicySession> sessions = this.getSessions();
		for (PolicySession session : sessions) {
			if (sessionName.equals(session.getName()) || sessionName.equals(session.getName()))
				return session;				
		}
		
		throw new IllegalArgumentException("Invalid Session Name: " + sessionName);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Map<String,Integer> factClassNames(String sessionName) throws IllegalArgumentException {		
		if (sessionName == null || sessionName.isEmpty())
			throw new IllegalArgumentException("Invalid Session Name: " + sessionName);
		
		// List<String> classNames = new ArrayList<>();
		Map<String,Integer> classNames = new HashMap<>();
		
		PolicySession session = getSession(sessionName);
		KieSession kieSession = session.getKieSession();

		Collection<FactHandle> facts = session.getKieSession().getFactHandles();
		for (FactHandle fact : facts) {
			try {
				String className = kieSession.getObject(fact).getClass().getName();
				if (classNames.containsKey(className))
					classNames.put(className, classNames.get(className) + 1);
				else
					classNames.put(className, 1);
			} catch (Exception e) {
				if (logger.isInfoEnabled())
					logger.info("Object cannot be retrieved from fact: " + fact);
			}			
		}	
		
		return classNames;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public long factCount(String sessionName) throws IllegalArgumentException {		
		if (sessionName == null || sessionName.isEmpty())
			throw new IllegalArgumentException("Invalid Session Name: " + sessionName);
		
		PolicySession session = getSession(sessionName);
		return session.getKieSession().getFactCount();	
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Object> facts(String sessionName, String className, boolean delete) {		
		if (sessionName == null || sessionName.isEmpty())
			throw new IllegalArgumentException("Invalid Session Name: " + sessionName);
		
		if (className == null || className.isEmpty())
			throw new IllegalArgumentException("Invalid Class Name: " + className);
		
		Class<?> factClass = 
				ReflectionUtil.fetchClass(this.policyContainer.getClassLoader(), className);
		if (factClass == null)
			throw new IllegalArgumentException("Class cannot be fetched in model's classloader: " + className);
		
		PolicySession session = getSession(sessionName);
		KieSession kieSession = session.getKieSession();
		
		List<Object> factObjects = new ArrayList<>();
		
		Collection<FactHandle> factHandles = kieSession.getFactHandles(new ClassObjectFilter(factClass));
		for (FactHandle factHandle : factHandles) {
			try {
				factObjects.add(kieSession.getObject(factHandle));
				if (delete)
					kieSession.delete(factHandle);					
			} catch (Exception e) {
				if (logger.isInfoEnabled())
					logger.info("Object cannot be retrieved from fact: " + factHandle);
			}
		}		
		
		return factObjects;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Object> factQuery(String sessionName, String queryName, String queriedEntity, boolean delete, Object... queryParams) {		
		if (sessionName == null || sessionName.isEmpty())
			throw new IllegalArgumentException("Invalid Session Name: " + sessionName);
		
		if (queryName == null || queryName.isEmpty())
			throw new IllegalArgumentException("Invalid Query Name: " + queryName);
		
		if (queriedEntity == null || queriedEntity.isEmpty())
			throw new IllegalArgumentException("Invalid Queried Entity: " + queriedEntity);
		
		PolicySession session = getSession(sessionName);
		KieSession kieSession = session.getKieSession();
		
		List<Object> factObjects = new ArrayList<>();
		
		QueryResults queryResults = kieSession.getQueryResults(queryName, queryParams);
		for (QueryResultsRow row : queryResults) {
			try {
				factObjects.add(row.get(queriedEntity));
				if (delete)
					kieSession.delete(row.getFactHandle(queriedEntity));
			} catch (Exception e) {
				if (logger.isInfoEnabled())
					logger.info("Object cannot be retrieved from fact: " + row);
			}
		}
		
		return factObjects;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<?> fetchModelClass(String className) throws IllegalStateException {
		Class<?> modelClass = 
			ReflectionUtil.fetchClass(this.policyContainer.getClassLoader(), className);
		return modelClass;
	}

	/**
	 * @return the recentSourceEvents
	 */
	@Override
	public Object[] getRecentSourceEvents() {
		synchronized(this.recentSourceEvents) {
			Object[] events = new Object[recentSourceEvents.size()];
			return recentSourceEvents.toArray(events);
		}
	}

	/**
	 * @return the recentSinkEvents
	 */
	@Override
	public String[] getRecentSinkEvents() {
		synchronized(this.recentSinkEvents) {
			String[] events = new String[recentSinkEvents.size()];
			return recentSinkEvents.toArray(events);
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isBrained() {
		return true;
	}
	

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("MavenDroolsController [policyContainer=")
		       .append((policyContainer != null) ? policyContainer.getName() : "NULL").append(":")
		       .append(", alive=")
			   .append(alive).append(", locked=")
			   .append(", modelClassLoaderHash=").append(modelClassLoaderHash).append("]");
		return builder.toString();
	}

}
