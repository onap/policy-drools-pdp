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

package org.onap.policy.drools.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.onap.policy.drools.controller.internal.MavenDroolsController;
import org.onap.policy.drools.controller.internal.NullDroolsController;
import org.onap.policy.drools.event.comm.Topic;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onap.policy.drools.event.comm.TopicSource;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomJacksonCoder;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.PotentialCoderFilter;
import org.onap.policy.drools.utils.Pair;

/**
 * Drools Controller Factory to manage controller creation, destruction,
 * and retrieval for management interfaces
 */
public interface DroolsControllerFactory {

	/**
	 * Constructs a Drools Controller based on properties
	 *
	 * @param properties properties containing initialization parameters
	 * @param eventSources list of event sources
	 * @param eventSinks list of event sinks
	 *
	 * @return the instantiated Drools Controller
	 * @throws IllegalArgumentException with invalid parameters
	 * @throws LinkageError Failure to link rules and models in Drools Libraries
	 */
	public DroolsController build(Properties properties,
								  List<? extends TopicSource> eventSources,
								  List<? extends TopicSink> eventSinks)
			throws LinkageError;

	/**
	 * Explicit construction of a Drools Controller
	 *
	 * @param groupId maven group id of drools artifact
	 * @param artifactId maven artifact id of drools artifact
	 * @param version maven version id of drools artifact
	 * @param decoderConfigurations list of decoder configurations
	 * @param encoderConfigurations list of encoder configurations
	 *
	 * @return the instantiated Drools Controller
	 * @throws IllegalArgumentException with invalid parameters
	 * @throws LinkageError Failure to link rules and models in Drools Libraries
	 */
	public DroolsController build(String groupId,
			 					  String artifactId,
			 					  String version,
			 					  List<TopicCoderFilterConfiguration> decoderConfigurations,
			 					  List<TopicCoderFilterConfiguration> encoderConfigurations)
			throws LinkageError;

	/**
	 * Releases the Drools Controller from operation
	 *
	 * @param controller the Drools Controller to shut down
	 */
	public void shutdown(DroolsController controller);

	/**
	 * Disables all Drools Controllers from operation
	 */
	public void shutdown();

	/**
	 * Destroys and releases resources for a Drools Controller
	 *
	 * @param controller the Drools Controller to destroy
	 */
	public void destroy(DroolsController controller);

	/**
	 * Destroys all Drools Controllers
	 */
	public void destroy();

	/**
	 * Gets the Drools Controller associated with the maven group
	 * and artifact id
	 *
	 * @param groupId maven group id of drools artifact
	 * @param artifactId maven artifact id of drools artifact
	 * @param version maven version id of drools artifact
	 *
	 * @return the Drools Controller
	 * @throws IllegalArgumentException with invalid parameters
	 */
	public DroolsController get(String groupId,
							    String artifactId,
								String version);

	/**
	 * returns the current inventory of Drools Controllers
	 *
	 * @return a list of Drools Controllers
	 */
	public List<DroolsController> inventory();
}

/* ---------------- implementation -----------------*/

/**
 * Factory of Drools Controllers indexed by the Maven coordinates
 */
class IndexedDroolsControllerFactory implements DroolsControllerFactory {

	/**
	 * logger
	 */
	private static Logger logger = LoggerFactory.getLogger(MavenDroolsController.class);

	/**
	 * Policy Controller Name Index
	 */
	protected HashMap<String, DroolsController> droolsControllers = new HashMap<>();

	/**
	 * Null Drools Controller
	 */
	protected NullDroolsController nullDroolsController = new NullDroolsController();


	public IndexedDroolsControllerFactory() {

		/* Add a NULL controller which will always be present in the hash */

		DroolsController controller = new NullDroolsController();
		String controllerId = controller.getGroupId() + ":" + controller.getArtifactId();

		synchronized(this) {
			droolsControllers.put(controllerId, controller);
		}
	}

	@Override
	public DroolsController build(Properties properties,
								  List<? extends TopicSource> eventSources,
								  List<? extends TopicSink> eventSinks)
			throws LinkageError {

		String groupId = properties.getProperty(PolicyProperties.RULES_GROUPID);
		if (groupId == null || groupId.isEmpty())
			groupId =  DroolsController.NO_GROUP_ID;

		String artifactId = properties.getProperty(PolicyProperties.RULES_ARTIFACTID);
		if (artifactId == null || artifactId.isEmpty())
			artifactId =  DroolsController.NO_ARTIFACT_ID;

		String version = properties.getProperty(PolicyProperties.RULES_VERSION);
		if (version == null || version.isEmpty())
			version =  DroolsController.NO_VERSION;

		List<TopicCoderFilterConfiguration>
			topics2DecodedClasses2Filters = codersAndFilters(properties, eventSources);

		List<TopicCoderFilterConfiguration>
			topics2EncodedClasses2Filters = codersAndFilters(properties, eventSinks);

		return this.build(groupId, artifactId, version,
						  topics2DecodedClasses2Filters,
				          topics2EncodedClasses2Filters);
	}

	/**
	 * find out decoder classes and filters
	 *
	 * @param properties properties with information about decoders
	 * @param topicEntities topic sources
	 * @return list of topics, each with associated decoder classes, each
	 *         with a list of associated filters
	 * @throws IllegalArgumentException invalid input data
	 */
	protected List<TopicCoderFilterConfiguration> codersAndFilters
					(Properties properties, List<? extends Topic> topicEntities) {

		String PROPERTY_TOPIC_ENTITY_PREFIX;

		List<TopicCoderFilterConfiguration>
			topics2DecodedClasses2Filters = new ArrayList<>();

		if (topicEntities.isEmpty())
			return topics2DecodedClasses2Filters;

		for (Topic topic: topicEntities) {

			/* source or sink ? ueb or dmaap? */
			boolean isSource = topic instanceof TopicSource;
			CommInfrastructure commInfra = topic.getTopicCommInfrastructure();
			if (commInfra == CommInfrastructure.UEB) {
				if (isSource) {
					PROPERTY_TOPIC_ENTITY_PREFIX = PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + ".";
				} else {
					PROPERTY_TOPIC_ENTITY_PREFIX = PolicyProperties.PROPERTY_UEB_SINK_TOPICS + ".";
				}
			} else if (commInfra == CommInfrastructure.DMAAP) {
				if (isSource) {
					PROPERTY_TOPIC_ENTITY_PREFIX = PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + ".";
				} else {
					PROPERTY_TOPIC_ENTITY_PREFIX = PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + ".";
				}
			} else if (commInfra == CommInfrastructure.NOOP) {
				if (!isSource)
					PROPERTY_TOPIC_ENTITY_PREFIX = PolicyProperties.PROPERTY_NOOP_SINK_TOPICS + ".";
				else
					continue;
			} else {
				throw new IllegalArgumentException("Invalid Communication Infrastructure: " + commInfra);
			}

			// 1. first the topic

			String aTopic = topic.getTopic();

			// 2.  check if there is a custom decoder for this topic that the user prefers to use
			//     instead of the ones provided in the platform

			String customGson = properties.getProperty
					(PROPERTY_TOPIC_ENTITY_PREFIX +
					 aTopic +
					 PolicyProperties.PROPERTY_TOPIC_EVENTS_CUSTOM_MODEL_CODER_GSON_SUFFIX);

			CustomGsonCoder customGsonCoder = null;
			if (customGson != null && !customGson.isEmpty()) {
				try {
					customGsonCoder =  new CustomGsonCoder(customGson);
				} catch (IllegalArgumentException e) {
					logger.warn("{}: cannot create custom-gson-coder {} because of {}",
							    this, customGson, e.getMessage(), e);
				}
			}

			String customJackson = properties.getProperty
					(PROPERTY_TOPIC_ENTITY_PREFIX +
					 aTopic +
					 PolicyProperties.PROPERTY_TOPIC_EVENTS_CUSTOM_MODEL_CODER_JACKSON_SUFFIX);

			CustomJacksonCoder customJacksonCoder = null;
			if (customJackson != null && !customJackson.isEmpty()) {
				try {
					customJacksonCoder =  new CustomJacksonCoder(customJackson);
				} catch (IllegalArgumentException e) {
					logger.warn("{}: cannot create custom-jackson-coder {} because of {}",
						        this, customJackson, e.getMessage(), e);
				}
			}

			// 3. second the list of classes associated with each topic

			String eventClasses =
					properties.getProperty(PROPERTY_TOPIC_ENTITY_PREFIX + aTopic + PolicyProperties.PROPERTY_TOPIC_EVENTS_SUFFIX);

			if (eventClasses == null || eventClasses.isEmpty()) {
				// TODO warn
				continue;
			}

			List<PotentialCoderFilter> classes2Filters = new ArrayList<>();

			List<String> aTopicClasses =
					new ArrayList<>(Arrays.asList(eventClasses.split("\\s*,\\s*")));

			for (String aClass: aTopicClasses) {


				// 4. third, for each coder class, get the list of field filters

				String filter = properties.getProperty
						(PROPERTY_TOPIC_ENTITY_PREFIX +
						 aTopic +
						 PolicyProperties.PROPERTY_TOPIC_EVENTS_SUFFIX +
						 "." + aClass +
						 PolicyProperties.PROPERTY_TOPIC_EVENTS_FILTER_SUFFIX);

				List<Pair<String,String>> filters = new ArrayList<>();

				if (filter == null || filter.isEmpty()) {
					// 4. topic -> class -> with no filters

					JsonProtocolFilter protocolFilter = JsonProtocolFilter.fromRawFilters(filters);
					PotentialCoderFilter class2Filters =
							new PotentialCoderFilter(aClass, protocolFilter);
					classes2Filters.add(class2Filters);
					continue;
				}

				// There are filters associated with the applicability of
				// this class for decoding.
				List<String> listOfFilters =
						new ArrayList<>(Arrays.asList(filter.split("\\s*,\\s*")));

				for (String nameValue: listOfFilters) {
					String fieldName;
					String regexValue;

					String[] nameValueSplit = nameValue.split("\\s*=\\s*");
					if (nameValueSplit.length <= 0 || nameValueSplit.length > 2) {
						// TODO warn
						// skip
						continue;
					}

					if (nameValueSplit.length == 2) {
						fieldName = nameValueSplit[0];
						regexValue = nameValueSplit[1];
					} else if (nameValueSplit.length == 1) {
						fieldName = nameValueSplit[0];
						regexValue = null;
					} else {
						// unreachable
						continue;
					}

					filters.add(new Pair<String,String>(fieldName, regexValue));
				}

				JsonProtocolFilter protocolFilter = JsonProtocolFilter.fromRawFilters(filters);
				PotentialCoderFilter class2Filters =
						new PotentialCoderFilter(aClass, protocolFilter);
				classes2Filters.add(class2Filters);
			}

			TopicCoderFilterConfiguration topic2Classes2Filters =
						new TopicCoderFilterConfiguration(aTopic,classes2Filters, customGsonCoder, customJacksonCoder);
			topics2DecodedClasses2Filters.add(topic2Classes2Filters);
		}

		return topics2DecodedClasses2Filters;
	}

	@Override
	public DroolsController build(String newGroupId,
			 					  String newArtifactId,
			                      String newVersion,
			                      List<TopicCoderFilterConfiguration> decoderConfigurations,
			                      List<TopicCoderFilterConfiguration> encoderConfigurations)
			throws LinkageError {

		if (newGroupId == null || newGroupId.isEmpty())
				throw new IllegalArgumentException("Missing maven group-id coordinate");

		if (newArtifactId == null || newArtifactId.isEmpty())
			throw new IllegalArgumentException("Missing maven artifact-id coordinate");

		if (newVersion == null || newVersion.isEmpty())
			throw new IllegalArgumentException("Missing maven version coordinate");

		String controllerId = newGroupId + ":" + newArtifactId;
		DroolsController controllerCopy = null;
		synchronized (this) {
			/*
			 * The Null Drools Controller for no maven coordinates is always here
			 * so when no coordinates present, this is the return point
			 *
			 * assert (controllerCopy instanceof NullDroolsController)
			 */
			if (droolsControllers.containsKey(controllerId)) {
				controllerCopy = droolsControllers.get(controllerId);
				if (controllerCopy.getVersion().equalsIgnoreCase(newVersion)) {
					return controllerCopy;
				}
			}
		}

		if (controllerCopy != null) {
			/*
			 * a controller keyed by group id + artifact id exists
			 * but with different version => version upgrade/downgrade
			 */

			controllerCopy.updateToVersion(newGroupId, newArtifactId, newVersion,
					                       decoderConfigurations, encoderConfigurations);

			return controllerCopy;
		}

		/* new drools controller */

		DroolsController controller = new MavenDroolsController
												(newGroupId, newArtifactId, newVersion,
											     decoderConfigurations,
											     encoderConfigurations);

		synchronized(this) {
			droolsControllers.put(controllerId, controller);
		}

		return controller;
	}

	@Override
	public void destroy(DroolsController controller) {
		unmanage(controller);
		controller.halt();
	}

	@Override
	public void destroy() {
		List<DroolsController> controllers = this.inventory();
		for (DroolsController controller: controllers) {
			controller.halt();
		}

		synchronized(this) {
			this.droolsControllers.clear();
		}
	}

	/**
	 * unmanage the drools controller
	 *
	 * @param controller
	 * @return
	 * @throws IllegalArgumentException
	 */
	protected void unmanage(DroolsController controller) {
		if (controller == null) {
			throw new IllegalArgumentException("No controller provided");
		}

		if (!controller.isBrained()) {
			logger.info("Drools Controller is NOT OPERATIONAL - nothing to destroy");
			return;
		}

		String controllerId = controller.getGroupId() + ":" + controller.getArtifactId();
		synchronized(this) {
			if (!this.droolsControllers.containsKey(controllerId)) {
				return;
			}

			droolsControllers.remove(controllerId);
		}
	}

	@Override
	public void shutdown(DroolsController controller) {
		this.unmanage(controller);
		controller.shutdown();
	}

	@Override
	public void shutdown() {
		List<DroolsController> controllers = this.inventory();
		for (DroolsController controller: controllers) {
			controller.shutdown();
		}

		synchronized(this) {
			this.droolsControllers.clear();
		}
	}

	@Override
	public DroolsController get(String groupId,
			                    String artifactId,
			                    String version) {

		if (groupId == null || artifactId == null ||
			groupId.isEmpty() || artifactId.isEmpty()) {
			throw new IllegalArgumentException("Missing maven coordinates: " +
								               groupId + ":" + artifactId);
		}

		String controllerId = groupId + ":" + artifactId;

		synchronized(this) {
			if (this.droolsControllers.containsKey(controllerId)) {
				return droolsControllers.get(controllerId);
			} else {
				throw new IllegalStateException("DroolController for " +
												controllerId + " not found");
			}
		}
	}

	@Override
	public List<DroolsController> inventory() {
		 List<DroolsController> controllers =
				 new ArrayList<>(this.droolsControllers.values());
		 return controllers;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("IndexedDroolsControllerFactory [#droolsControllers=").append(droolsControllers.size())
				.append("]");
		return builder.toString();
	}

}
