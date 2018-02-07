/*-
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.protocol.coders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomJacksonCoder;
import org.onap.policy.drools.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coder (Encoder/Decoder) of Events.
 */
public interface EventProtocolCoder {
	
	public static class CoderFilters {

		/**
		 * coder class 
		 */
		protected String factClass;
		
		/**
		 * filters to apply to the selection of the decodedClass;
		 */
		protected JsonProtocolFilter filter;

		/**
		 * classloader hash
		 */
		protected int modelClassLoaderHash;

		
		/**
		 * constructor
		 * 
		 * @param codedClass coder class
		 * @param filter filters to apply
		 */
		public CoderFilters(String codedClass, JsonProtocolFilter filter, int modelClassLoaderHash) {
			this.factClass = codedClass;
			this.filter = filter;
			this.modelClassLoaderHash = modelClassLoaderHash;
		}

		/**
		 * @return the codedClass
		 */
		public String getCodedClass() {
			return factClass;
		}

		/**
		 * @param codedClass the decodedClass to set
		 */
		public void setCodedClass(String codedClass) {
			this.factClass = codedClass;
		}
		
		/**
		 * @return the filter
		 */
		public JsonProtocolFilter getFilter() {
			return filter;
		}

		/**
		 * @param filter the filter to set
		 */
		public void setFilter(JsonProtocolFilter filter) {
			this.filter = filter;
		}

		public int getModelClassLoaderHash() {
			return modelClassLoaderHash;
		}

		public void setFromClassLoaderHash(int fromClassLoaderHash) {
			this.modelClassLoaderHash = fromClassLoaderHash;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("CoderFilters [factClass=").append(factClass).append(", filter=").append(filter)
					.append(", modelClassLoaderHash=").append(modelClassLoaderHash).append("]");
			return builder.toString();
		}

	}
	
	/**
	 * Adds a Decoder class to decode the protocol over this topic
	 *  
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic the topic 
	 * @param eventClass the event class
	 * @param protocolFilter filters to selectively choose a particular decoder
	 * when there are multiples
	 * 
	 * @throw IllegalArgumentException if an invalid parameter is passed
	 */
	public void addDecoder(String groupId, String artifactId, 
			               String topic, 
			               String eventClass, 
			               JsonProtocolFilter protocolFilter, 
			               CustomGsonCoder customGsonCoder,
			               CustomJacksonCoder customJacksonCoder,
			               int modelClassLoaderHash)
		throws IllegalArgumentException;

	/**
	 * removes all decoders associated with the controller id
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic of the controller
	 * 
	 * @throws IllegalArgumentException if invalid arguments have been provided
	 */
	void removeEncoders(String groupId, String artifactId, String topic) throws IllegalArgumentException;
	
	/**
	 * removes decoders associated with the controller id and topic
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic the topic
	 * 
	 * @throws IllegalArgumentException if invalid arguments have been provided
	 */
	public void removeDecoders(String groupId, String artifactId, String topic) throws IllegalArgumentException;
	
	/**
	 * Given a controller id and a topic, it gives back its filters
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic the topic
	 * 
	 * return list of decoders
	 * 
	 * @throw IllegalArgumentException if an invalid parameter is passed
	 */
	public List<CoderFilters> getDecoderFilters(String groupId, String artifactId, String topic)
		throws IllegalArgumentException;	
	

	/**
	 * Given a controller id and a topic, it gives back the decoding configuration
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic the topic
	 * 
	 * return decoding toolset
	 * 
	 * @throw IllegalArgumentException if an invalid parameter is passed
	 */
	public ProtocolCoderToolset getDecoders(String groupId, String artifactId, String topic) 
		   throws IllegalArgumentException;
	
	/**
	 * Given a controller id and a topic, it gives back all the decoding configurations
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic the topic
	 * 
	 * return decoding toolset
	 * 
	 * @throw IllegalArgumentException if an invalid parameter is passed
	 */
	public List<ProtocolCoderToolset> getDecoders(String groupId, String artifactId)
		   throws IllegalArgumentException;
	
	
	/**
	 * gets all decoders associated with the group and artifact ids
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * 
	 * @throws IllegalArgumentException if invalid arguments have been provided
	 */
	public List<CoderFilters> getDecoderFilters(String groupId, String artifactId) throws IllegalArgumentException;
	

	/**
	 * Given a controller id and a topic, it gives back the classes that implements the encoding
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic the topic
	 * 
	 * return list of decoders
	 * 
	 * @throw IllegalArgumentException if an invalid parameter is passed
	 */
	public List<CoderFilters> getEncoderFilters(String groupId, String artifactId, String topic) 
			throws IllegalArgumentException;
	
	/**
	 * gets all encoders associated with the group and artifact ids
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * 
	 * @throws IllegalArgumentException if invalid arguments have been provided
	 */
	public List<CoderFilters> getEncoderFilters(String groupId, String artifactId) throws IllegalArgumentException;
	
	/**
	 * Given a controller id, a topic, and a classname, it gives back the classes that implements the decoding
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic the topic
	 * @param classname classname
	 * 
	 * return list of decoders
	 * 
	 * @throw IllegalArgumentException if an invalid parameter is passed
	 */	
	public CoderFilters getDecoderFilters(String groupId, String artifactId, String topic, String classname)
		throws IllegalArgumentException;	
	
	/**
	 * is there a decoder supported for the controller id and topic
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic protocol
	 * @return true if supported
	 */
	public boolean isDecodingSupported(String groupId, String artifactId, String topic);
	
	/**
	 * Adds a Encoder class to encode the protocol over this topic
	 *  
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic the topic 
	 * @param eventClass the event class
	 * @param protocolFilter filters to selectively choose a particular decoder
	 * when there are multiples
	 * 
	 * @throw IllegalArgumentException if an invalid parameter is passed
	 */
	public void addEncoder(String groupId, String artifactId, String topic, 
						   String eventClass, 
			               JsonProtocolFilter protocolFilter,
			               CustomGsonCoder customGsonCoder,
			               CustomJacksonCoder customJacksonCoder,
			               int modelClassLoaderHash)
		throws IllegalArgumentException;
	
	/**
	 * is there an encoder supported for the controller id and topic
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic protocol
	 * @return true if supported
	 */
	public boolean isEncodingSupported(String groupId, String artifactId, String topic);
	
	/**
	 * get encoder based on coordinates and classname
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic protocol
	 * @param json event string
	 * @return
	 * @throws IllegalArgumentException invalid arguments passed in
	 */
	public CoderFilters getEncoderFilters(String groupId, String artifactId, String topic, String classname)
			throws IllegalArgumentException;
	
	/**
	 * get encoder based on topic and encoded class
	 * 
	 * @param topic topic
	 * @param encodedClass encoded class
	 * @return
	 * @throws IllegalArgumentException invalid arguments passed in
	 */
	public List<CoderFilters> getReverseEncoderFilters(String topic, String encodedClass)
			throws IllegalArgumentException;
	
	/**
	 * gets the identifier of the creator of the encoder
	 * 
	 * @param topic topic
	 * @param encodedClass encoded class
	 * @return a drools controller
	 * @throws IllegalArgumentException invalid arguments passed in
	 */
	public DroolsController getDroolsController(String topic, Object encodedClass)
			throws IllegalArgumentException;
	
	/**
	 * gets the identifier of the creator of the encoder
	 * 
	 * @param topic topic
	 * @param encodedClass encoded class
	 * @return list of drools controllers
	 * @throws IllegalArgumentException invalid arguments passed in
	 */
	public List<DroolsController> getDroolsControllers(String topic, Object encodedClass)
			throws IllegalArgumentException;
	
	/**
	 * decode topic's stringified event (json) to corresponding Event Object.
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic protocol
	 * @param json event string
	 * @return
	 * @throws IllegalArgumentException invalid arguments passed in
	 * @throws UnsupportedOperationException if the operation is not supported
	 * @throws IllegalStateException if the system is in an illegal state
	 */
	public Object decode(String groupId, String artifactId, String topic, String json) 
			throws IllegalArgumentException, UnsupportedOperationException, IllegalStateException;

	/**
	 * encodes topic's stringified event (json) to corresponding Event Object.
	 * 
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic protocol
	 * @param event Object
	 * 
	 * @throws IllegalArgumentException invalid arguments passed in
	 */
	public String encode(String groupId, String artifactId, String topic, Object event) 
			throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException;
	
	/**
	 * encodes topic's stringified event (json) to corresponding Event Object.
	 * 
	 * @param topic topic
	 * @param event event object
	 * 
	 * @throws IllegalArgumentException invalid arguments passed in
	 * @throws UnsupportedOperationException operation cannot be performed
	 */
	public String encode(String topic, Object event) 
			throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException;
	
	/**
	 * encodes topic's stringified event (json) to corresponding Event Object.
	 * 
	 * @param topic topic
	 * @param event event object
	 * @param droolsController 
	 * 
	 * @throws IllegalArgumentException invalid arguments passed in
	 * @throws UnsupportedOperationException operation cannot be performed
	 */
	public String encode(String topic, Object event, DroolsController droolsController) 
			throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException;
	
	/**
	 * singleton reference to the global event protocol coder
	 */
	public static EventProtocolCoder manager = new MultiplexorEventProtocolCoder();
}

/**
 * Protocol Coder that does its best attempt to decode/encode, selecting the best
 * class and best fitted json parsing tools.
 */
class MultiplexorEventProtocolCoder implements EventProtocolCoder {
	/**
	 * Logger 
	 */
	private static Logger logger = LoggerFactory.getLogger(MultiplexorEventProtocolCoder.class);
	
	/**
	 * Decoders
	 */
	protected EventProtocolDecoder decoders = new EventProtocolDecoder();
	
	/**
	 * Encoders
	 */
	protected EventProtocolEncoder encoders = new EventProtocolEncoder();
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addDecoder(String groupId, String artifactId, String topic, 
			               String eventClass, 
			               JsonProtocolFilter protocolFilter,
			               CustomGsonCoder customGsonCoder,
			               CustomJacksonCoder customJacksonCoder,
			               int modelClassLoaderHash) 
		   throws IllegalArgumentException {
		logger.info("{}: add-decoder {}:{}:{}:{}:{}:{}:{}:{}", this, 
				    groupId, artifactId, topic, eventClass,
				    protocolFilter, customGsonCoder, customJacksonCoder,
				    modelClassLoaderHash);
		this.decoders.add(groupId, artifactId, topic, eventClass, protocolFilter, 
				         customGsonCoder, customJacksonCoder, modelClassLoaderHash);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addEncoder(String groupId, String artifactId, String topic, 
			               String eventClass, 
			               JsonProtocolFilter protocolFilter,
			               CustomGsonCoder customGsonCoder,
			               CustomJacksonCoder customJacksonCoder,
			               int modelClassLoaderHash) 
		   throws IllegalArgumentException {
		logger.info("{}: add-decoder {}:{}:{}:{}:{}:{}:{}:{}", this, 
			        groupId, artifactId, topic, eventClass,
			        protocolFilter, customGsonCoder, customJacksonCoder,
			        modelClassLoaderHash);
		this.encoders.add(groupId, artifactId, topic, eventClass, protocolFilter, 
		                  customGsonCoder, customJacksonCoder, modelClassLoaderHash);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeDecoders(String groupId, String artifactId, String topic) 
		   throws IllegalArgumentException {
		logger.info("{}: remove-decoder {}:{}:{}", this, groupId, artifactId, topic);	
		this.decoders.remove(groupId, artifactId, topic);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeEncoders(String groupId, String artifactId, String topic) 
		   throws IllegalArgumentException {
		logger.info("{}: remove-encoder {}:{}:{}", this, groupId, artifactId, topic);
		this.encoders.remove(groupId, artifactId, topic);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isDecodingSupported(String groupId, String artifactId, String topic) {	
		return this.decoders.isCodingSupported(groupId, artifactId, topic);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEncodingSupported(String groupId, String artifactId, String topic) {
		return this.encoders.isCodingSupported(groupId, artifactId, topic);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object decode(String groupId, String artifactId, String topic, String json) 
			throws IllegalArgumentException, UnsupportedOperationException, IllegalStateException {
		logger.debug("{}: decode {}:{}:{}:{}", this, groupId, artifactId, topic, json);
		return this.decoders.decode(groupId, artifactId, topic, json);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String encode(String groupId, String artifactId, String topic, Object event) 
			throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException {
		logger.debug("{}: encode {}:{}:{}:{}", this, groupId, artifactId, topic, event);
		return this.encoders.encode(groupId, artifactId, topic, event);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String encode(String topic, Object event) 
			throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException {
		logger.debug("{}: encode {}:{}", this, topic, event);
		return this.encoders.encode(topic, event);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String encode(String topic, Object event, DroolsController droolsController) 
			throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException {
		logger.debug("{}: encode {}:{}:{}", this, topic, event, droolsController);
		return this.encoders.encode(topic, event, droolsController);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<CoderFilters> getDecoderFilters(String groupId, String artifactId, String topic) 
			throws IllegalArgumentException {		
		return this.decoders.getFilters(groupId, artifactId, topic);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ProtocolCoderToolset getDecoders(String groupId, String artifactId, String topic) 
			throws IllegalArgumentException {		
		Pair<ProtocolCoderToolset,ProtocolCoderToolset> decoderToolsets = this.decoders.getCoders(groupId, artifactId, topic);
		if (decoderToolsets == null)
			throw new IllegalArgumentException("Decoders not found for " + groupId + ":" + artifactId + ":" + topic);
		
		return decoderToolsets.first();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<CoderFilters> getEncoderFilters(String groupId, String artifactId, String topic) 
			throws IllegalArgumentException {		
		return this.encoders.getFilters(groupId, artifactId, topic);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CoderFilters getDecoderFilters(String groupId, String artifactId, String topic, String classname)
			throws IllegalArgumentException {		
		return this.decoders.getFilters(groupId, artifactId, topic, classname);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public CoderFilters getEncoderFilters(String groupId, String artifactId, String topic, String classname)
			throws IllegalArgumentException {	
		return this.encoders.getFilters(groupId, artifactId, topic, classname);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<CoderFilters> getReverseEncoderFilters(String topic, String encodedClass) throws IllegalArgumentException {
		return this.encoders.getReverseFilters(topic, encodedClass);
	}

	/**
	 * get all deocders by maven coordinates and topic
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * 
	 * @return list of decoders
	 * @throws IllegalArgumentException if invalid input
	 */
	@Override
	public List<ProtocolCoderToolset> getDecoders(String groupId, String artifactId) 
			throws IllegalArgumentException {

		List<Pair<ProtocolCoderToolset,ProtocolCoderToolset>> decoderToolsets = this.decoders.getCoders(groupId, artifactId);
		if (decoderToolsets == null)
			throw new IllegalArgumentException("Decoders not found for " + groupId + ":" + artifactId);
		
		List<ProtocolCoderToolset> parser1CoderToolset = new ArrayList<>();
		for (Pair<ProtocolCoderToolset,ProtocolCoderToolset> coderToolsetPair : decoderToolsets) {
			parser1CoderToolset.add(coderToolsetPair.first());
		}
		
		return parser1CoderToolset;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<CoderFilters> getDecoderFilters(String groupId, String artifactId) throws IllegalArgumentException {
		return this.decoders.getFilters(groupId, artifactId);
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<CoderFilters> getEncoderFilters(String groupId, String artifactId) throws IllegalArgumentException {
		return this.encoders.getFilters(groupId, artifactId);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public DroolsController getDroolsController(String topic, Object encodedClass) throws IllegalArgumentException {
		return this.encoders.getDroolsController(topic, encodedClass);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<DroolsController> getDroolsControllers(String topic, Object encodedClass) throws IllegalArgumentException {
		return this.encoders.getDroolsControllers(topic, encodedClass);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("MultiplexorEventProtocolCoder [decoders=").append(decoders).append(", encoders=")
				.append(encoders).append("]");
		return builder.toString();
	}
}

/**
 * This protocol Coder that does its best attempt to decode/encode, selecting the best
 * class and best fitted json parsing tools.
 */
abstract class GenericEventProtocolCoder  {
	private static Logger  logger = LoggerFactory.getLogger(GenericEventProtocolCoder.class);	
	
	/**
	 * Mapping topic:controller-id -> <protocol-decoder-toolset-pair> 
	 * where protocol-coder-toolset-pair contains both a jackson-protocol-coder-toolset
	 * and a gson-protocol-coder-toolset.   The first value of the pair will the 
	 * protocol coder toolset most likely to be successful with the encoding or decoding,
	 * and consequently the second value will be the less likely.
	 */
	protected final HashMap<String, Pair<ProtocolCoderToolset,ProtocolCoderToolset>> coders = 
			new HashMap<>();
	
	/**
	 * Mapping topic + classname -> Protocol Set 
	 */
	protected final HashMap<String, List<Pair<ProtocolCoderToolset,ProtocolCoderToolset>>> reverseCoders = 
			new HashMap<>();
	
	protected boolean multipleToolsetRetries = false;
	
	GenericEventProtocolCoder(boolean multipleToolsetRetries) {
		this.multipleToolsetRetries = multipleToolsetRetries;
	}
	
	/**
	 * Index a new coder
	 *  
	 * @param groupId of the controller
	 * @param artifactId of the controller
	 * @param topic the topic 
	 * @param eventClass the event class
	 * @param protocolFilter filters to selectively choose a particular decoder
	 * when there are multiples
	 * 
	 * @throw IllegalArgumentException if an invalid parameter is passed
	 */
	public void add(String groupId, String artifactId, 
			        String topic, 
			        String eventClass, 
			        JsonProtocolFilter protocolFilter, 
			        CustomGsonCoder customGsonCoder, 
			        CustomJacksonCoder customJacksonCoder,
			        int modelClassLoaderHash)
		   throws IllegalArgumentException {
		if (groupId == null || groupId.isEmpty()) {			
			throw new IllegalArgumentException("Invalid group id");
		}
		
		if (artifactId == null || artifactId.isEmpty())
			throw new IllegalArgumentException("Invalid artifact id");
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (eventClass == null) {
			throw new IllegalArgumentException("Invalid Event Class");
		}
		
		String key = this.codersKey(groupId, artifactId, topic);
		String reverseKey = this.reverseCodersKey(topic, eventClass);
		
		synchronized(this) {
			if (coders.containsKey(key)) {				
				Pair<ProtocolCoderToolset, ProtocolCoderToolset> toolsets = coders.get(key);
				
				logger.info("{}: adding coders for existing {}: ", this, key, toolsets.first());
				
				toolsets.first().addCoder(eventClass, protocolFilter, modelClassLoaderHash);
				toolsets.second().addCoder(eventClass, protocolFilter, modelClassLoaderHash);
				
				if (!reverseCoders.containsKey(reverseKey)) {
					logger.info("{}: adding new reverse coders (multiple classes case) for {}:{}: {}",
							    this, reverseKey, key, toolsets.first());
					
					List<Pair<ProtocolCoderToolset,ProtocolCoderToolset>> reverseMappings =
							new ArrayList<>();
					reverseMappings.add(toolsets);
					reverseCoders.put(reverseKey, reverseMappings);
				}
				return;
			}
		
			GsonProtocolCoderToolset gsonCoderTools = 
										new GsonProtocolCoderToolset
														(topic, key, 
														 groupId, artifactId, 
														 eventClass, protocolFilter,
														 customGsonCoder,
														 modelClassLoaderHash);
			
			JacksonProtocolCoderToolset jacksonCoderTools = 
										new JacksonProtocolCoderToolset
														(topic, key, 
														 groupId, artifactId, 
														 eventClass, protocolFilter,
														 customJacksonCoder,
														 modelClassLoaderHash);
			
			// Use Gson as the first priority encoding/decoding toolset, and Jackson
			// as second.  This is because it has been observed that they can diverge
			// somewhat in the encoding/decoding data types, which can produce json
			// that may result incompatible with what some network elements are 
			// expecting.   As decoding takes place, this element will reconfigure
			// itself to set the jackson one as the favoured one first, if errors
			// are detected in the gson encoding
			
			Pair<ProtocolCoderToolset,ProtocolCoderToolset> coderTools = 
					new Pair<>(gsonCoderTools, 
							                                            jacksonCoderTools);
			
			logger.info("{}: adding coders for new {}: {}", this, key, coderTools.first());
			
			coders.put(key, coderTools);
			
			if (reverseCoders.containsKey(reverseKey)) {
				// There is another controller (different group id/artifact id/topic)
				// that shares the class and the topic.
				
				List<Pair<ProtocolCoderToolset,ProtocolCoderToolset>> toolsets = 
															reverseCoders.get(reverseKey);
				boolean present = false;
				for (Pair<ProtocolCoderToolset,ProtocolCoderToolset> parserSet: toolsets) {
					// just doublecheck
					present = parserSet.first().getControllerId().equals(key);
					if (present) {
						/* anomaly */
						logger.error("{}: unexpected toolset reverse mapping found for {}:{}: {}", 
								     this, reverseKey, key, parserSet.first());
					}
				}
				
				if (present) {
					return;
				} else {
					logger.info("{}: adding coder set for {}: {} ", this, 
							    reverseKey, coderTools.getFirst());
					toolsets.add(coderTools);
				}
			} else {
				List<Pair<ProtocolCoderToolset,ProtocolCoderToolset>> toolsets =
					new ArrayList<>();
				toolsets.add(coderTools);
				
				logger.info("{}: adding toolset for reverse key {}: {}", this, reverseKey, toolsets);
				reverseCoders.put(reverseKey, toolsets);
			}
			
		}
	}

	/**
	 * produces key for indexing toolset entries
	 * 
	 * @param group group id
	 * @param artifactId artifact id
	 * @param topic topic
	 * @return index key
	 */
	protected String codersKey(String groupId, String artifactId, String topic) {
		return groupId + ":" + artifactId + ":" + topic;
	}
	
	/**
	 * produces a key for the reverse index
	 * 
	 * @param topic topic 
	 * @param eventClass coded class
	 * @return reverse index key
	 */
	protected String reverseCodersKey(String topic, String eventClass) {
		return topic + ":" + eventClass;
	}
	
	/**
	 * remove coder 
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @param topic topic
	 * @throws IllegalArgumentException if invalid input
	 */
	public void remove(String groupId, String artifactId, String topic) 
		   throws IllegalArgumentException {
		
		if (groupId == null || groupId.isEmpty())
			throw new IllegalArgumentException("Invalid group id");
		
		if (artifactId == null || artifactId.isEmpty())
			throw new IllegalArgumentException("Invalid artifact id");
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		String key = this.codersKey(groupId, artifactId, topic);
		
		synchronized(this) {
			if (coders.containsKey(key)) {
				Pair<ProtocolCoderToolset, ProtocolCoderToolset> p = coders.remove(key);
				
				logger.info("{}: removed toolset for {}: {}", this, key, p.getFirst());
				
				for (CoderFilters codeFilter : p.first().getCoders()) {
					String className = codeFilter.getCodedClass();
					String reverseKey = this.reverseCodersKey(topic, className);
					if (this.reverseCoders.containsKey(reverseKey) ) {
						List<Pair<ProtocolCoderToolset, ProtocolCoderToolset>> toolsets = 
																this.reverseCoders.get(reverseKey);
						Iterator<Pair<ProtocolCoderToolset, ProtocolCoderToolset>> toolsetsIter = 
																				toolsets.iterator();
						while (toolsetsIter.hasNext()) {
							Pair<ProtocolCoderToolset, ProtocolCoderToolset> toolset = toolsetsIter.next();
							if (toolset.first().getControllerId().equals(key)) {
								logger.info("{}: removed coder from toolset for {} from reverse mapping {}: ", 
										    this, reverseKey);
								toolsetsIter.remove();
							}
						}
						
						if (this.reverseCoders.get(reverseKey).isEmpty()) {
							logger.info("{}: removing reverse mapping for {}: ", this, reverseKey);						
							this.reverseCoders.remove(reverseKey);
						}
					}
				}
			}
		}
	}

	/**
	 * does it support coding?
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @param topic topic
	 * @return true if its is codable
	 */
	public boolean isCodingSupported(String groupId, String artifactId, String topic) {
		
		if (groupId == null || groupId.isEmpty())
			throw new IllegalArgumentException("Invalid group id");
		
		if (artifactId == null || artifactId.isEmpty())
			throw new IllegalArgumentException("Invalid artifact id");
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		String key = this.codersKey(groupId, artifactId, topic);
		synchronized(this) {
			return coders.containsKey(key);
		}
	}
	
	/**
	 * decode a json string into an Object
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @param topic topic
	 * @param json json string to convert to object
	 * @return the decoded object
	 * @throws IllegalArgumentException if invalid argument is provided
	 * @throws UnsupportedOperationException if the operation cannot be performed
	 */
	public Object decode(String groupId, String artifactId, String topic, String json) 
			throws IllegalArgumentException, UnsupportedOperationException, IllegalStateException {
		
		if (!isCodingSupported(groupId, artifactId, topic))
			throw new IllegalArgumentException("Unsupported:" + codersKey(groupId, artifactId, topic) + " for encoding");
		
		String key = this.codersKey(groupId, artifactId, topic);
		Pair<ProtocolCoderToolset,ProtocolCoderToolset> coderTools = coders.get(key);
		try {
			Object event = coderTools.first().decode(json);
			if (event != null)
				return event;
		} catch (Exception e) {
			logger.debug("{}, cannot decode {}", this, json, e);
		}
		
		if (multipleToolsetRetries) {
			// try the less favored toolset
			try {
				Object event = coderTools.second().decode(json);
				if (event != null) {
					// change the priority of the toolset
					synchronized(this) {
						ProtocolCoderToolset first = coderTools.first();
						ProtocolCoderToolset second = coderTools.second();
						coderTools.first(second);
						coderTools.second(first);
					}
					
					return event;
				}
			} catch (Exception e) {
				throw new UnsupportedOperationException(e);
			}
		} 
		
		throw new UnsupportedOperationException("Cannot decode neither with gson or jackson");
	}

	/**
	 * encode an object into a json string
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @param topic topic
	 * @param event object to convert to string
	 * @return the json string
	 * @throws IllegalArgumentException if invalid argument is provided
	 * @throws UnsupportedOperationException if the operation cannot be performed
	 */
	public String encode(String groupId, String artifactId, String topic, Object event) 
			throws IllegalArgumentException, UnsupportedOperationException {
		
		if (!isCodingSupported(groupId, artifactId, topic))
			throw new IllegalArgumentException
				("Unsupported:" + codersKey(groupId, artifactId, topic));
		
		if (event == null)
			throw new IllegalArgumentException("Unsupported topic:" + topic);
		
		// reuse the decoder set, since there must be affinity in the model
		String key = this.codersKey(groupId, artifactId, topic);
		return this.encodeInternal(key, event);
	}
	
	/**
	 * encode an object into a json string
	 * 
	 * @param key identifier
	 * @param event object to convert to string
	 * @return the json string
	 * @throws IllegalArgumentException if invalid argument is provided
	 * @throws UnsupportedOperationException if the operation cannot be performed
	 */
	protected String encodeInternal(String key, Object event) 
			throws IllegalArgumentException, UnsupportedOperationException {
		
		logger.debug("{}: encode for {}: {}", this, key, event);
		
		Pair<ProtocolCoderToolset,ProtocolCoderToolset> coderTools = coders.get(key);
		try {
			String json = coderTools.first().encode(event);
			if (json != null && !json.isEmpty())
				return json;
		} catch (Exception e) {
			logger.warn("{}: cannot encode (first) for {}: {}", this, key, event, e);
		}
		
		if (multipleToolsetRetries) {
			// try the less favored toolset
			try {
				String json = coderTools.second().encode(event);
				if (json != null) {
					// change the priority of the toolset
					synchronized(this) {
						ProtocolCoderToolset first = coderTools.first();
						ProtocolCoderToolset second = coderTools.second();
						coderTools.first(second);
						coderTools.second(first);
					}
					
					return json;
				}
			} catch (Exception e) {
				logger.error("{}: cannot encode (second) for {}: {}", this, key, event, e);
				throw new UnsupportedOperationException(e);
			}
		}
		
		throw new UnsupportedOperationException("Cannot decode neither with gson or jackson");
	}
	
	/**
	 * encode an object into a json string
	 * 
	 * @param topic topic
	 * @param event object to convert to string
	 * @return the json string
	 * @throws IllegalArgumentException if invalid argument is provided
	 * @throws UnsupportedOperationException if the operation cannot be performed
	 */
	public String encode(String topic, Object event) 
			throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException {
		
		if (event == null)
			throw new IllegalArgumentException("Invalid encoded class");
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid topic");
		
        String reverseKey = this.reverseCodersKey(topic, event.getClass().getCanonicalName());
        if (!this.reverseCoders.containsKey(reverseKey))
            throw new IllegalArgumentException("no reverse coder has been found");
        
        List<Pair<ProtocolCoderToolset, ProtocolCoderToolset>> 
                            toolsets = this.reverseCoders.get(reverseKey);
        
        String key = codersKey(toolsets.get(0).first().getGroupId(), toolsets.get(0).first().getArtifactId(), topic);
        return this.encodeInternal(key, event);
	}
	
	/**
	 * encode an object into a json string
	 * 
	 * @param topic topic
	 * @param encodedClass object to convert to string
	 * @return the json string
	 * @throws IllegalArgumentException if invalid argument is provided
	 * @throws UnsupportedOperationException if the operation cannot be performed
	 */
	public String encode(String topic, Object encodedClass, DroolsController droolsController) 
			throws IllegalArgumentException, IllegalArgumentException, UnsupportedOperationException {
		
		if (encodedClass == null)
			throw new IllegalArgumentException("Invalid encoded class");
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid topic");
		
		String key = codersKey(droolsController.getGroupId(), droolsController.getArtifactId(), topic);
		return this.encodeInternal(key, encodedClass);
	}

	/**
	 * @param topic
	 * @param encodedClass
	 * @param reverseKey
	 * @return
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 */
	protected List<DroolsController> droolsCreators(String topic, Object encodedClass)
			throws IllegalStateException, IllegalArgumentException {
		
		List<DroolsController> droolsControllers = new ArrayList<>();
		
		String reverseKey = this.reverseCodersKey(topic, encodedClass.getClass().getCanonicalName());
		if (!this.reverseCoders.containsKey(reverseKey)) {
			logger.warn("{}: no reverse mapping for {}", this, reverseKey);
			return droolsControllers;
		}
		
		List<Pair<ProtocolCoderToolset, ProtocolCoderToolset>> 
					toolsets = this.reverseCoders.get(reverseKey);
		
		// There must be multiple toolset pairs associated with <topic,classname> reverseKey
		// case 2 different controllers use the same models and register the same encoder for
		// the same topic.  This is assumed not to occur often but for the purpose of encoding
		// but there should be no side-effects.  Ownership is crosscheck against classname and 
		// classloader reference.
		
		if (toolsets == null || toolsets.isEmpty())
			throw new IllegalStateException("No Encoders toolsets available for topic "+ topic +
					                        " encoder " + encodedClass.getClass().getCanonicalName());
		
		for (Pair<ProtocolCoderToolset, ProtocolCoderToolset> encoderSet : toolsets) {
			// figure out the right toolset
			String groupId = encoderSet.first().getGroupId();
			String artifactId = encoderSet.first().getArtifactId();
			List<CoderFilters> coderFilters = encoderSet.first().getCoders();
			for (CoderFilters coder : coderFilters) {
				if (coder.getCodedClass().equals(encodedClass.getClass().getCanonicalName())) {
					DroolsController droolsController = 
							DroolsController.factory.get(groupId, artifactId, "");
					if (droolsController.ownsCoder(encodedClass.getClass(), coder.getModelClassLoaderHash())) {
						droolsControllers.add(droolsController);
					}
				}
			}
		}
		
		if (droolsControllers.isEmpty())
			throw new IllegalStateException("No Encoders toolsets available for "+ topic +
		                                    ":" + encodedClass.getClass().getCanonicalName());
		
		return droolsControllers;
	}


	/**
	 * get all filters by maven coordinates and topic
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @param topic topic
	 * @return list of coders
	 * @throws IllegalArgumentException if invalid input
	 */
	public List<CoderFilters> getFilters(String groupId, String artifactId, String topic) 
			throws IllegalArgumentException {
		
		if (!isCodingSupported(groupId, artifactId, topic))
			throw new IllegalArgumentException("Unsupported:" + codersKey(groupId, artifactId, topic));
		
		String key = this.codersKey(groupId, artifactId, topic);
		Pair<ProtocolCoderToolset,ProtocolCoderToolset> coderTools = coders.get(key);
		return coderTools.first().getCoders();
	}
	
	/**
	 * get all coders by maven coordinates and topic
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @param topic topic
	 * @return list of coders
	 * @throws IllegalArgumentException if invalid input
	 */
	public Pair<ProtocolCoderToolset,ProtocolCoderToolset> getCoders(String groupId, String artifactId, String topic) 
			throws IllegalArgumentException {
		
		if (!isCodingSupported(groupId, artifactId, topic))
			throw new IllegalArgumentException("Unsupported:" + codersKey(groupId, artifactId, topic));
		
		String key = this.codersKey(groupId, artifactId, topic);
		Pair<ProtocolCoderToolset,ProtocolCoderToolset> coderTools = coders.get(key);
		return coderTools;
	}
	
	/**
	 * get all coders by maven coordinates and topic
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @return list of coders
	 * @throws IllegalArgumentException if invalid input
	 */
	public List<CoderFilters> getFilters(String groupId, String artifactId) 
			throws IllegalArgumentException {
		
		if (groupId == null || groupId.isEmpty())
			throw new IllegalArgumentException("Invalid group id");
		
		if (artifactId == null || artifactId.isEmpty())
			throw new IllegalArgumentException("Invalid artifact id");
		
		String key = this.codersKey(groupId, artifactId, "");
		
		List<CoderFilters> codersFilters = new ArrayList<>();
		for (Map.Entry<String, Pair<ProtocolCoderToolset,ProtocolCoderToolset>> entry : coders.entrySet()) {
			if (entry.getKey().startsWith(key)) {
				codersFilters.addAll(entry.getValue().first().getCoders());
			}
		}
		
		return codersFilters;
	}
	
	/**
	 * get all coders by maven coordinates and topic
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @return list of coders
	 * @throws IllegalArgumentException if invalid input
	 */
	public List<Pair<ProtocolCoderToolset,ProtocolCoderToolset>> getCoders(String groupId, String artifactId) 
			throws IllegalArgumentException {
		
		if (groupId == null || groupId.isEmpty())
			throw new IllegalArgumentException("Invalid group id");
		
		if (artifactId == null || artifactId.isEmpty())
			throw new IllegalArgumentException("Invalid artifact id");
		
		String key = this.codersKey(groupId, artifactId, "");
		
		List<Pair<ProtocolCoderToolset,ProtocolCoderToolset>> coderToolset = new ArrayList<>();
		for (Map.Entry<String, Pair<ProtocolCoderToolset,ProtocolCoderToolset>> entry : coders.entrySet()) {
			if (entry.getKey().startsWith(key)) {
				coderToolset.add(entry.getValue());
			}
		}
		
		return coderToolset;
	}


	/**
	 * get all filters by maven coordinates, topic, and classname
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @param topic topic
	 * @param classname
	 * @return list of coders
	 * @throws IllegalArgumentException if invalid input
	 */
	public CoderFilters getFilters(String groupId, String artifactId, String topic, String classname)
			throws IllegalArgumentException {
		
		if (!isCodingSupported(groupId, artifactId, topic))
			throw new IllegalArgumentException("Unsupported:" + codersKey(groupId, artifactId, topic));
		
		if (classname == null || classname.isEmpty())
			throw new IllegalArgumentException("classname must be provided");
		
		String key = this.codersKey(groupId, artifactId, topic);
		Pair<ProtocolCoderToolset,ProtocolCoderToolset> coderTools = coders.get(key);
		return coderTools.first().getCoder(classname);
	}
	
	/**
	 * get coded based on class and topic
	 * 
	 * @param topic
	 * @param codedClass
	 * @return
	 * @throws IllegalArgumentException
	 */
	public List<CoderFilters> getReverseFilters(String topic, String codedClass)
			throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Unsupported");
		
		if (codedClass == null)
			throw new IllegalArgumentException("class must be provided");
		
		String key = this.reverseCodersKey(topic, codedClass);
		List<Pair<ProtocolCoderToolset,ProtocolCoderToolset>> toolsets = this.reverseCoders.get(key);
		if (toolsets == null)
			throw new IllegalArgumentException("No Coder found for " + key);
		
		
		List<CoderFilters> coderFilters = new ArrayList<>();
		for (Pair<ProtocolCoderToolset,ProtocolCoderToolset> toolset: toolsets) {
			coderFilters.addAll(toolset.first().getCoders());
		}
		
		return coderFilters;
	}
	
	/**
	 * returns group and artifact id of the creator of the encoder
	 * 
	 * @param topic
	 * @param fact
	 * @return
	 * @throws IllegalArgumentException
	 */
	DroolsController getDroolsController(String topic, Object fact)
			throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Unsupported");
		
		if (fact == null)
			throw new IllegalArgumentException("class must be provided");
		
		List<DroolsController> droolsControllers = droolsCreators(topic, fact);
		
		if (droolsControllers.isEmpty())	
			throw new IllegalArgumentException("Invalid Topic: " + topic);
		
		if (droolsControllers.size() > 1) {
			logger.warn("{}: multiple drools-controller {} for {}:{} ", this,
				        droolsControllers, topic, fact.getClass().getCanonicalName());
			// continue
		}
		return droolsControllers.get(0);
	}
	
	/**
	 * returns group and artifact id of the creator of the encoder
	 * 
	 * @param topic
	 * @param fact
	 * @return
	 * @throws IllegalArgumentException
	 */
	List<DroolsController> getDroolsControllers(String topic, Object fact)
			throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Unsupported");
		
		if (fact == null)
			throw new IllegalArgumentException("class must be provided");
		
		List<DroolsController> droolsControllers = droolsCreators(topic, fact);
		if (droolsControllers.size() > 1) {
			// unexpected
			logger.warn("{}: multiple drools-controller {} for {}:{} ", this,
					    droolsControllers, topic, fact.getClass().getCanonicalName());		
			// continue
		}
		return droolsControllers;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("GenericEventProtocolCoder [coders=").append(coders.keySet()).append(", reverseCoders=")
				.append(reverseCoders.keySet()).append("]");
		return builder.toString();
	}
}

class EventProtocolDecoder extends GenericEventProtocolCoder {

	public EventProtocolDecoder(){super(false);}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("EventProtocolDecoder [toString()=").append(super.toString()).append("]");
		return builder.toString();
	}
	
}
	
class EventProtocolEncoder extends GenericEventProtocolCoder {
	
	public EventProtocolEncoder(){super(false);}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("EventProtocolEncoder [toString()=").append(super.toString()).append("]");
		return builder.toString();
	}
}
