/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.drools.core.ClassObjectFilter;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Query;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.jmx.PdpJmx;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.features.DroolsControllerFeatureAPI;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomJacksonCoder;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.PotentialCoderFilter;
import org.onap.policy.drools.utils.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maven-based Drools Controller that interacts with the 
 * policy-core PolicyContainer and PolicySession to manage
 * Drools containers instantiated using Maven.
 */
public class MavenDroolsController implements DroolsController {
	
	/**
	 * logger 
	 */
	private static Logger  logger = LoggerFactory.getLogger(MavenDroolsController.class);
	
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
     * list of topics, each with associated extractor classes, each
     * with a list of associated filters.
     */
    protected List<TopicCoderFilterConfiguration> extractorConfigurations;
	
	/**
	 * list of topics, each with associated encoder classes, each
	 * with a list of associated filters.
	 */
	protected List<TopicCoderFilterConfiguration> encoderConfigurations;
	
	/**
	 * recent source events processed
	 */
	protected final CircularFifoQueue<Object> recentSourceEvents = new CircularFifoQueue<>(10);
	
	/**
	 * recent sink events processed
	 */
	protected final CircularFifoQueue<String> recentSinkEvents = new CircularFifoQueue<>(10);
	
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
	 * @param decoderConfigurations list of topic -> decoders -> filters/extractors mapping
	 * @param encoderConfigurations list of topic -> encoders -> filters mapping
	 * 
	 * @throws IllegalArgumentException invalid arguments passed in
	 */
	public MavenDroolsController(String groupId, 
								 String artifactId, 
								 String version,
								 List<TopicCoderFilterConfiguration> decoderConfigurations,
								 List<TopicCoderFilterConfiguration> encoderConfigurations) {
		
		logger.info("drools-controller instantiation [{}:{}:{}]", groupId, artifactId, version);
		
		if (groupId == null || groupId.isEmpty())
			throw new IllegalArgumentException("Missing maven group-id coordinate");
	
		if (artifactId == null || artifactId.isEmpty())
			throw new IllegalArgumentException("Missing maven artifact-id coordinate");
		
		if (version == null || version.isEmpty())
			throw new IllegalArgumentException("Missing maven version coordinate");
		
		this.policyContainer= new PolicyContainer(groupId, artifactId, version);
		this.init(decoderConfigurations, encoderConfigurations);
		
		logger.debug("{}: instantiation completed ", this);
	}
	
	/**
	 * init encoding/decoding configuration
	 * @param decoderConfigurations list of topic -> decoders -> filters mapping
	 * @param encoderConfigurations list of topic -> encoders -> filters mapping
	 */
	protected void init(List<TopicCoderFilterConfiguration> decoderConfigurations,
			            List<TopicCoderFilterConfiguration> encoderConfigurations) {
		
		this.decoderConfigurations = decoderConfigurations;
		this.encoderConfigurations = encoderConfigurations;
		
        this.initCoders(decoderConfigurations,
                        (topic, potentialCodedClass, protocolFilter, customGsonCoder, customJacksonCoder) -> {
                            EventProtocolCoder.manager.addDecoder(this.getGroupId(), this.getArtifactId(), topic,
                                            potentialCodedClass, protocolFilter, customGsonCoder, customJacksonCoder,
                                            this.policyContainer.getClassLoader().hashCode());

                        });
        
        this.initCoders(decoderConfigurations,
                        (topic, potentialCodedClass, protocolFilter, customGsonCoder, customJacksonCoder) -> {
                            EventProtocolCoder.manager.addExtractor(this.getGroupId(), this.getArtifactId(), topic,
                                            potentialCodedClass, protocolFilter, customGsonCoder, customJacksonCoder,
                                            this.policyContainer.getClassLoader().hashCode());

                        });
		
		this.initCoders(encoderConfigurations, 
                        (topic, potentialCodedClass, protocolFilter, customGsonCoder, customJacksonCoder) -> {
                            EventProtocolCoder.manager.addEncoder(this.getGroupId(), this.getArtifactId(), topic,
                                            potentialCodedClass, protocolFilter, customGsonCoder, customJacksonCoder,
                                            this.policyContainer.getClassLoader().hashCode());

                        });
		
		this.modelClassLoaderHash = this.policyContainer.getClassLoader().hashCode();		
	}
	
	@Override
	public void updateToVersion(String newGroupId, String newArtifactId, String newVersion,
			                    List<TopicCoderFilterConfiguration> decoderConfigurations,
			                    List<TopicCoderFilterConfiguration> encoderConfigurations) 
		throws LinkageError {
		
		logger.info("{}: updating version -> [{}:{}:{}]", newGroupId, newArtifactId, newVersion);
		
		if (newGroupId == null || newGroupId.isEmpty())
			throw new IllegalArgumentException("Missing maven group-id coordinate");
	
		if (newArtifactId == null || newArtifactId.isEmpty())
			throw new IllegalArgumentException("Missing maven artifact-id coordinate");
		
		if (newVersion == null || newVersion.isEmpty())
			throw new IllegalArgumentException("Missing maven version coordinate");
		
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
	 * @param coderConfigurations list of topic -> decoders -> filters mapping
	 */
	protected void initCoders(List<TopicCoderFilterConfiguration> coderConfigurations, 
	                AddCoderFunction addCoderFunc) {
		
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
					throw makeRetrieveEx(customGsonCoderClass);
				} else {
					if (logger.isInfoEnabled())
						logClassFetched(customGsonCoderClass);
				}
			}
			
			CustomJacksonCoder customJacksonCoder = coderConfig.getCustomJacksonCoder();
			if (coderConfig.getCustomJacksonCoder() != null && 
				coderConfig.getCustomJacksonCoder().getClassContainer() != null &&
				!coderConfig.getCustomJacksonCoder().getClassContainer().isEmpty()) {
				
				String customJacksonCoderClass = coderConfig.getCustomJacksonCoder().getClassContainer();
				if (!ReflectionUtil.isClass(this.policyContainer.getClassLoader(),
						                   customJacksonCoderClass)) {
					throw makeRetrieveEx(customJacksonCoderClass);
				} else {
					if (logger.isInfoEnabled())
						logClassFetched(customJacksonCoderClass);
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
					throw makeRetrieveEx(potentialCodedClass);
				} else {
					if (logger.isInfoEnabled())
						logClassFetched(potentialCodedClass);
				}			

                addCoderFunc.add(topic, potentialCodedClass, protocolFilter, customGsonCoder, customJacksonCoder);
			}
		}
	}
	
	/**
	 * Logs an error and makes an exception for an item that cannot be retrieved.
	 * @param itemName
	 * @return a new exception
	 */
	private IllegalArgumentException makeRetrieveEx(String itemName) {
		logger.error(itemName + " cannot be retrieved");
		return new IllegalArgumentException(itemName + " cannot be retrieved");
	}

	/**
	 * Logs the name of the class that was fetched.
	 * @param className
	 */
	private void logClassFetched(String className) {
		logger.info("CLASS FETCHED " + className);
	}
	

	/**
	 * remove decoders.
	 */
	protected void removeDecoders(){
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
	protected void removeEncoders() {
		
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
	

	@Override
	public boolean ownsCoder(Class<? extends Object> coderClass, int modelHash) {
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

	@Override
	public void shutdown() {		
		logger.info("{}: SHUTDOWN", this);
		
		try {
			this.stop();
			this.removeCoders();
		} catch (Exception e) {
			logger.error("{} SHUTDOWN FAILED because of {}", this, e.getMessage(), e);
		} finally {
			this.policyContainer.shutdown();
		}

	}
	
	@Override
	public void halt() {
		logger.info("{}: HALT", this);
		
		try {
			this.stop();
			this.removeCoders();
		} catch (Exception e) {
			logger.error("{} HALT FAILED because of {}", this, e.getMessage(), e);
		} finally {
			this.policyContainer.destroy();
		}	
	}
	
	/**
	 * removes this drools controllers and encoders and decoders from operation
	 */
	protected void removeCoders() {
		logger.info("{}: REMOVE-CODERS", this);
		
		try {
			this.removeDecoders();
		} catch (IllegalArgumentException e) {
			logger.error("{} REMOVE-DECODERS FAILED because of {}", this, e.getMessage(), e);
		}
		
		try {
			this.removeEncoders();
		} catch (IllegalArgumentException e) {
			logger.error("{} REMOVE-ENCODERS FAILED because of {}", this, e.getMessage(), e);
		}
	}

	@Override
	public boolean isAlive() {
		return this.alive;
	}
	
	@Override
	public boolean offer(String topic, String event) {
		logger.debug("{}: OFFER: {} <- {}", this, topic, event);
		
		if (this.locked)
			return true;
		
		if (!this.alive)
			return true;
		
		// 0. Check if the policy container has any sessions
		
		if (this.policyContainer.getPolicySessions().isEmpty()) {
			// no sessions
			return true;
		}
		
		// 1. Now, check if this topic has a decoder:
		
		if (!EventProtocolCoder.manager.isDecodingSupported(this.getGroupId(), 
				                                              this.getArtifactId(), 
				                                              topic)) {
			
			logger.warn("{}: DECODING-UNSUPPORTED {}:{}:{}", this, 
					    topic, this.getGroupId(), this.getArtifactId());
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
			logger.debug("{}: DECODE FAILED: {} <- {} because of {}", this, topic, 
					     event, uoe.getMessage(), uoe);
			return true;
		} catch (Exception e) {
			logger.warn("{}: DECODE FAILED: {} <- {} because of {}", this, topic, 
				        event, e.getMessage(), e);
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

		for (DroolsControllerFeatureAPI feature : DroolsControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeInsert(this, anEvent))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-insert failure because of {}",
					this, feature.getClass().getName(), e.getMessage(), e);
			}
		}

		boolean successInject = this.policyContainer.insertAll(anEvent);
		if (!successInject)
			logger.warn(this + "Failed to inject into PolicyContainer " + this.getSessionNames());

		for (DroolsControllerFeatureAPI feature : DroolsControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.afterInsert(this, anEvent, successInject))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} after-insert failure because of {}",
					this, feature.getClass().getName(), e.getMessage(), e);
			}
		}

		return true;
	}	
	
	@Override
	public String extractRequestId(String topic, String event) {
	    
	    /*
	     * Check if extraction is supported.
	     */
	    
        if (!EventProtocolCoder.manager.isExtractionSupported(this.getGroupId(), 
                                                              this.getArtifactId(), 
                                                              topic)) {
            
            logger.warn("{}: EXTRACTION-UNSUPPORTED {}:{}:{}", this, 
                        topic, this.getGroupId(), this.getArtifactId());
            
            // let invoker have the event
            return null;
        }
        
        /*
         * Extract request id.
         */
        
        try {
            return EventProtocolCoder.manager.extract(this.getGroupId(), 
                                                          this.getArtifactId(), 
                                                          topic, 
                                                          event);
        } catch (Exception e) {
            logger.warn("{}: EXTRACT FAILED: {} <- {} because of {}", this, topic, 
                        event, e.getMessage(), e);
            return null;
        }
    }

    @Override
	public boolean deliver(TopicSink sink, Object event) {
		
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

	@Override
	public String getVersion() {
		return this.policyContainer.getVersion();
	}
	
	@Override
	public String getArtifactId() {
		return this.policyContainer.getArtifactId();
	}
	
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

	@Override
	public synchronized boolean lock() {
		logger.info("LOCK: " +  this);
		
		this.locked = true;
		return true;
	}

	@Override
	public synchronized boolean unlock() {
		logger.info("UNLOCK: " +  this);
		
		this.locked = false;
		return true;
	}

	@Override
	public boolean isLocked() {
		return this.locked;
	}
	
	@JsonIgnore
	@Override
	public PolicyContainer getContainer() {
		return this.policyContainer;
	}
	
	@JsonProperty("sessions")
	@Override
	public List<String> getSessionNames() {
		return getSessionNames(true);
	}
	
	@JsonProperty("sessionCoordinates")
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
		List<String> sessionNames = new ArrayList<>();
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
		List<PolicySession> sessions = new ArrayList<>();
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
			if (sessionName.equals(session.getName()) || sessionName.equals(session.getFullName()))
				return session;				
		}
		
		throw invalidSessNameEx(sessionName);
	}

	private IllegalArgumentException invalidSessNameEx(String sessionName) {
		return new IllegalArgumentException("Invalid Session Name: " + sessionName);
	}
	
	@Override
	public Map<String,Integer> factClassNames(String sessionName) {		
		if (sessionName == null || sessionName.isEmpty())
			throw invalidSessNameEx(sessionName);

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
				logger.warn("Object cannot be retrieved from fact {}", fact, e);
			}			
		}	
		
		return classNames;
	}
	
	@Override
	public long factCount(String sessionName) {		
		if (sessionName == null || sessionName.isEmpty())
			throw invalidSessNameEx(sessionName);
		
		PolicySession session = getSession(sessionName);
		return session.getKieSession().getFactCount();	
	}
	
	@Override
	public List<Object> facts(String sessionName, String className, boolean delete) {		
		if (sessionName == null || sessionName.isEmpty())
			throw invalidSessNameEx(sessionName);
		
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
				logger.warn("Object cannot be retrieved from fact {}", factHandle, e);
			}
		}		
		
		return factObjects;
	}
	
	@Override
	public List<Object> factQuery(String sessionName, String queryName, String queriedEntity, boolean delete, Object... queryParams) {		
		if (sessionName == null || sessionName.isEmpty())
			throw invalidSessNameEx(sessionName);
		
		if (queryName == null || queryName.isEmpty())
			throw new IllegalArgumentException("Invalid Query Name: " + queryName);
		
		if (queriedEntity == null || queriedEntity.isEmpty())
			throw new IllegalArgumentException("Invalid Queried Entity: " + queriedEntity);
		
		PolicySession session = getSession(sessionName);
		KieSession kieSession = session.getKieSession();
		
		boolean found = false;
		for (KiePackage kiePackage : kieSession.getKieBase().getKiePackages()) {
			for (Query q : kiePackage.getQueries()) {
				if (q.getName() != null && q.getName().equals(queryName)) {
					found = true;
					break;
				}
			}
		}
		if (!found)
			throw new IllegalArgumentException("Invalid Query Name: " + queryName);
        
		List<Object> factObjects = new ArrayList<>();
		
		QueryResults queryResults = kieSession.getQueryResults(queryName, queryParams);
		for (QueryResultsRow row : queryResults) {
			try {
				factObjects.add(row.get(queriedEntity));
				if (delete)
					kieSession.delete(row.getFactHandle(queriedEntity));
			} catch (Exception e) {
				logger.warn("Object cannot be retrieved from row: {}", row, e);
			}
		}
		
		return factObjects;
	}
	
	@Override
	public Class<?> fetchModelClass(String className) {
		return ReflectionUtil.fetchClass(this.policyContainer.getClassLoader(), className);
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

    @FunctionalInterface
    private static interface AddCoderFunction {

        public void add(String topic, String potentialCodedClass, JsonProtocolFilter protocolFilter,
                        CustomGsonCoder customGsonCoder, CustomJacksonCoder customJacksonCoder);
    }
}
