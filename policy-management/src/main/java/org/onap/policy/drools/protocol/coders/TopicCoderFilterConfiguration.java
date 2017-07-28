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

package org.onap.policy.drools.protocol.coders;

import java.util.List;

public class TopicCoderFilterConfiguration {

	/**
	 * Custom coder, contains class and static field to access parser that the controller
	 * desires to use instead of the framework provided parser
	 */
	public static abstract class CustomCoder {
		protected String className;
		protected String staticCoderField;
		
		/**
		 * create custom coder from raw string in the following format
		 * (typically embedded in a property file):
		 * 
		 * Note this is to support decoding/encoding of partial structures that are
		 * only known by the model.
		 * 
		 * @param rawCustomCoder with format: <class-containing-custom-coder>,<static-coder-field>
		 */
		public CustomCoder(String rawCustomCoder) throws IllegalArgumentException {			
			if (rawCustomCoder != null && !rawCustomCoder.isEmpty()) {
				
				this.className = rawCustomCoder.substring(0,rawCustomCoder.indexOf(","));
				if (this.className == null || this.className.isEmpty()) {
					throw new IllegalArgumentException("No classname to create CustomCoder cannot be created");
				}
				
				this.staticCoderField = rawCustomCoder.substring(rawCustomCoder.indexOf(",")+1);
				if (this.staticCoderField == null || this.staticCoderField.isEmpty()) {
					throw new IllegalArgumentException
						("No staticCoderField to create CustomCoder cannot be created for class " +
						 className);
				}

			}
		}
		/**
		 * @param classContainer
		 * @param staticCoderField
		 */
		public CustomCoder(String className, String staticCoderField) throws IllegalArgumentException {
			if (className == null || className.isEmpty()) {
				throw new IllegalArgumentException("No classname to create CustomCoder cannot be created");
			}
			
			if (staticCoderField == null || staticCoderField.isEmpty()) {
				throw new IllegalArgumentException
					("No staticCoderField to create CustomCoder cannot be created for class " +
					 className);
			}
			
			this.className = className;
			this.staticCoderField = staticCoderField;
		}

		/**
		 * @return the className
		 */
		public String getClassContainer() {
			return className;
		}

		/**
		 * @param className the className to set
		 */
		public void setClassContainer(String className) {
			this.className = className;
		}

		/**
		 * @return the staticCoderField
		 */
		public String getStaticCoderField() {
			return staticCoderField;
		}

		/**
		 * @param staticCoderField the staticGson to set
		 */
		public void setStaticCoderField(String staticCoderField) {
			this.staticCoderField = staticCoderField;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("CustomCoder [className=").append(className).append(", staticCoderField=")
					.append(staticCoderField).append("]");
			return builder.toString();
		}
	}
	
	public static class CustomGsonCoder extends CustomCoder {
	
		public CustomGsonCoder(String className, String staticCoderField) {
				super(className, staticCoderField);
		}

		public CustomGsonCoder(String customGson) throws IllegalArgumentException {
			super(customGson);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("CustomGsonCoder [toString()=").append(super.toString()).append("]");
			return builder.toString();
		}

	}
	
	public static class CustomJacksonCoder extends CustomCoder {
		
		public CustomJacksonCoder(String className, String staticCoderField) {
				super(className, staticCoderField);
		}
		
		public CustomJacksonCoder(String customJackson) throws IllegalArgumentException {
			super(customJackson);
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("CustomJacksonCoder [toString()=").append(super.toString()).append("]");
			return builder.toString();
		}

	}

	/**
	 * Coder/Decoder class and Filter container.   The decoder class is potential,
	 * in order to be operational needs to be fetched from an available
	 * class loader.
	 *
	 */
	public static class PotentialCoderFilter {

		/**
		 * decoder class (pending from being able to be fetched and found 
		 * in some class loader)
		 */
		protected String codedClass;
		
		/**
		 * filters to apply to the selection of the decodedClass;
		 */
		protected JsonProtocolFilter filter;
		
		/**
		 * constructor
		 * 
		 * @param codedClass decoder class
		 * @param filter filters to apply
		 */
		public PotentialCoderFilter(String codedClass, JsonProtocolFilter filter) {
			this.codedClass = codedClass;
			this.filter = filter;
		}

		/**
		 * @return the decodedClass
		 */
		public String getCodedClass() {
			return codedClass;
		}

		/**
		 * @param decodedClass the decodedClass to set
		 */
		public void setCodedClass(String decodedClass) {
			this.codedClass = decodedClass;
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

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("PotentialCoderFilter [codedClass=").append(codedClass).append(", filter=").append(filter)
					.append("]");
			return builder.toString();
		}
	}
	
	/**
	 * the source topic
	 */
	protected final String topic;
	
	/**
	 * List of decoder -> filters
	 */
	protected final List<PotentialCoderFilter> coderFilters;
	
	/**
	 * custom gson coder that this controller prefers to use instead of the framework ones
	 */
	protected CustomGsonCoder customGsonCoder;
	
	/**
	 * custom jackson coder that this controller prefers to use instead of the framework ones
	 */
	protected CustomJacksonCoder customJacksonCoder;

	/**
	 * Constructor 
	 * 
	 * @param decoderFilters list of decoders and associated filters
	 * @param topic the topic
	 */
	public TopicCoderFilterConfiguration(String topic, List<PotentialCoderFilter> decoderFilters,
                                         CustomGsonCoder customGsonCoder, 
                                         CustomJacksonCoder customJacksonCoder) {
		this.coderFilters = decoderFilters;
		this.topic = topic;
		this.customGsonCoder = customGsonCoder;
		this.customJacksonCoder = customJacksonCoder;
	}

	/**
	 * @return the topic
	 */
	public String getTopic() {
		return topic;
	}

	/**
	 * @return the decoderFilters
	 */
	public List<PotentialCoderFilter> getCoderFilters() {
		return coderFilters;
	}
	
	/**
	 * @return the customGsonCoder
	 */
	public CustomGsonCoder getCustomGsonCoder() {
		return customGsonCoder;
	}

	/**
	 * @param customGsonCoder the customGsonCoder to set
	 */
	public void setCustomGsonCoder(CustomGsonCoder customGsonCoder) {
		this.customGsonCoder = customGsonCoder;
	}

	/**
	 * @return the customJacksonCoder
	 */
	public CustomJacksonCoder getCustomJacksonCoder() {
		return customJacksonCoder;
	}

	/**
	 * @param customJacksonCoder the customJacksonCoder to set
	 */
	public void setCustomJacksonCoder(CustomJacksonCoder customJacksonCoder) {
		this.customJacksonCoder = customJacksonCoder;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TopicCoderFilterConfiguration [topic=").append(topic).append(", coderFilters=")
				.append(coderFilters).append(", customGsonCoder=").append(customGsonCoder)
				.append(", customJacksonCoder=").append(customJacksonCoder).append("]");
		return builder.toString();
	}


}
