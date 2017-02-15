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

package org.openecomp.policy.drools.server.restful;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.controller.DroolsController;
import org.openecomp.policy.drools.event.comm.TopicEndpoint;
import org.openecomp.policy.drools.event.comm.TopicSink;
import org.openecomp.policy.drools.event.comm.TopicSource;
import org.openecomp.policy.drools.event.comm.bus.DmaapTopicSink;
import org.openecomp.policy.drools.event.comm.bus.DmaapTopicSource;
import org.openecomp.policy.drools.event.comm.bus.UebTopicSink;
import org.openecomp.policy.drools.event.comm.bus.UebTopicSource;
import org.openecomp.policy.drools.properties.PolicyProperties;
import org.openecomp.policy.drools.protocol.coders.EventProtocolCoder;
import org.openecomp.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.openecomp.policy.drools.protocol.coders.JsonProtocolFilter;
import org.openecomp.policy.drools.protocol.coders.JsonProtocolFilter.FilterRule;
import org.openecomp.policy.drools.protocol.coders.ProtocolCoderToolset;
import org.openecomp.policy.drools.protocol.configuration.ControllerConfiguration;
import org.openecomp.policy.drools.protocol.configuration.PdpdConfiguration;
import org.openecomp.policy.drools.system.PolicyController;
import org.openecomp.policy.drools.system.PolicyEngine;


/**
 * REST Endpoint for management of the Drools PDP
 */
@Path("/policy/pdp")
public class RestManager {
	/**
	 * Logger
	 */
	private static Logger  logger = FlexLogger.getLogger(RestManager.class);  
	
	/**
	 * gets the Policy Engine
	 * 
	 * @return the Policy Engine
	 */
    @GET
    @Path("engine")
    @Produces(MediaType.APPLICATION_JSON)
    public PolicyEngine engine() {    	
    	return PolicyEngine.manager;
    }
    
    
    /**
     * Updates the Policy Engine
     * 
     * @param configuration configuration
     * @return Policy Engine
     */
    @PUT
    @Path("engine")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateEngine(PdpdConfiguration configuration) {
    	PolicyController controller = null;
    	boolean success = true;
		try {
			success = PolicyEngine.manager.configure(configuration);
		} catch (Exception e) {
			success = false;
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
                              "PolicyEngine", this.toString());
		}
    	
		if (!success)
			return Response.status(Response.Status.NOT_ACCEPTABLE).
                    entity(new Error("cannot perform operation")).build();
		else
			return Response.status(Response.Status.OK).entity(controller).build();
    }
    
    /**
     * Activates the Policy Engine
     * 
     * @param configuration configuration
     * @return Policy Engine
     */
    @PUT
    @Path("engine/activation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response activateEngine() {
    	boolean success = true;
		try {
			PolicyEngine.manager.activate();
		} catch (Exception e) {
			success = false;
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
                              "PolicyEngine", this.toString());
		}
    	
		if (!success)
			return Response.status(Response.Status.NOT_ACCEPTABLE).
                    entity(new Error("cannot perform operation")).build();
		else
			return Response.status(Response.Status.OK).entity(PolicyEngine.manager).build();
    }
    
    /**
     * Activates the Policy Engine
     * 
     * @param configuration configuration
     * @return Policy Engine
     */
    @PUT
    @Path("engine/deactivation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deactivateEngine() {
    	boolean success = true;
		try {
			PolicyEngine.manager.deactivate();
		} catch (Exception e) {
			success = false;
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
                              "PolicyEngine", this.toString());
		}
    	
		if (!success)
			return Response.status(Response.Status.NOT_ACCEPTABLE).
                    entity(new Error("cannot perform operation")).build();
		else
			return Response.status(Response.Status.OK).entity(PolicyEngine.manager).build();
    }
    
    @DELETE
    @Path("engine")
    @Produces(MediaType.APPLICATION_JSON)
    public Response engineShutdown() { 
    	try {
			PolicyEngine.manager.shutdown();
		} catch (IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
					          "shutdown: " + PolicyEngine.manager);
    		return Response.status(Response.Status.BAD_REQUEST).
			        entity(PolicyEngine.manager).
			        build();
		}
    	
		return Response.status(Response.Status.OK).
		                entity(PolicyEngine.manager).
		                build();
    }   
    
    @PUT
    @Path("engine/lock")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response lockEngine() {
    	boolean success = PolicyEngine.manager.lock();
    	if (success)
    		return Response.status(Status.OK).
    				        entity("Policy Engine is locked").
    				        build();
    	else
    		return Response.status(Status.SERVICE_UNAVAILABLE).
    				        entity("Policy Engine cannot be locked").
    				        build();
    }
    
    @DELETE
    @Path("engine/unlock")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response unlockEngine() {
    	boolean success = PolicyEngine.manager.unlock();
    	if (success)
    		return Response.status(Status.OK).
    				        entity("Policy Engine is unlocked").
    				        build();
    	else
    		return Response.status(Status.SERVICE_UNAVAILABLE).
    				        entity("Policy Engine cannot be unlocked").
    				        build();
    }
    
    @GET
    @Path("engine/controllers")
    @Produces(MediaType.APPLICATION_JSON)
    public List<PolicyController> controllers() {
        return PolicyEngine.manager.getPolicyControllers();
    }
    
    @POST
    @Path("engine/controllers")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addController(Properties config) {
    	if (config == null)
    		return Response.status(Response.Status.BAD_REQUEST).
    				        entity(new Error("A configuration must be provided")).
    				        build();
    	
    	String controllerName = config.getProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME);
    	if (controllerName == null || controllerName.isEmpty())
    		return Response.status(Response.Status.BAD_REQUEST).
    				        entity(new Error
    				        			("Configuration must have an entry for " + 
    				                     PolicyProperties.PROPERTY_CONTROLLER_NAME)).
    				        build();
    	
    	PolicyController controller;
		try {
			controller = PolicyController.factory.get(controllerName);
			if (controller != null)
				return Response.status(Response.Status.NOT_MODIFIED).
						        entity(controller).
						        build();
		} catch (IllegalArgumentException e) {
			// This is OK
		} catch (IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
                              controllerName, this.toString());
			return Response.status(Response.Status.NOT_ACCEPTABLE).
                            entity(new Error(controllerName + " not found")).build();
		}
    	
    	try {
			controller = PolicyEngine.manager.createPolicyController
					(config.getProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME), config);
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
			                  controllerName, this.toString());
    		return Response.status(Response.Status.BAD_REQUEST).
							        entity(new Error(e.getMessage())).
							        build();
		}
    	
    	try {
			boolean success = controller.start();
			if (!success) {
				logger.warn("Can't start " + controllerName + ": " + controller.toString());
				return Response.status(Response.Status.PARTIAL_CONTENT).
                                       entity(new Error(controllerName + " can't be started")).build();
			}
		} catch (IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
                    controllerName, this.toString());
			return Response.status(Response.Status.PARTIAL_CONTENT).
                                   entity(controller).build();
		}
    	
		return Response.status(Response.Status.CREATED).
                entity(controller).
                build();
    }    
    
    @GET
    @Path("engine/controllers/{controllerName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response controller(@PathParam("controllerName") String controllerName) {
    	PolicyController controller = null;
		try {
			controller = PolicyController.factory.get(controllerName);
		} catch (IllegalArgumentException e) {
			logger.info("Can't retrieve controller " + controllerName + 
					          ".  Reason: " + e.getMessage());
		} catch (IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
                              controllerName, this.toString());
			return Response.status(Response.Status.NOT_ACCEPTABLE).
                            entity(new Error(controllerName + " not acceptable")).build();
		}
    	
		if (controller != null)
    		return Response.status(Response.Status.OK).
			        entity(controller).build();
		else
			return Response.status(Response.Status.NOT_FOUND).
		                           entity(new Error(controllerName + " not found")).build();
    }
    
    @DELETE
    @Path("engine/controllers/{controllerName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteController(@PathParam("controllerName") String controllerName) {
    	
    	if (controllerName == null || controllerName.isEmpty())
    		return Response.status(Response.Status.BAD_REQUEST).
    				        entity("A controller name must be provided").
    				        build();
    	
    	PolicyController controller;
    	try {
	    	controller =
	    			PolicyController.factory.get(controllerName);
	    	if (controller == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
	    				        entity(new Error(controllerName + "  does not exist")).
	    				        build();
		} catch (IllegalArgumentException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
			                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
							        entity(new Error(controllerName +  " not found: " + e.getMessage())).
							        build();
		} catch (IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
                              controllerName, this.toString());
			return Response.status(Response.Status.NOT_ACCEPTABLE).
                                   entity(new Error(controllerName + " not acceptable")).build();
		}
    	
    	try {
			PolicyEngine.manager.removePolicyController(controllerName);
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
			                  controllerName + controller);
    		return Response.status(Response.Status.INTERNAL_SERVER_ERROR).
							       entity(new Error(e.getMessage())).
							       build();
		}
    	
		return Response.status(Response.Status.OK).
                entity(controller).
                build();
    }
    
    /**
     * Updates the Policy Engine
     * 
     * @param configuration configuration
     * @return Policy Engine
     */
    @PUT
    @Path("engine/controllers/{controllerName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateController(@PathParam("controllerName") String controllerName,
    		                         ControllerConfiguration controllerConfiguration) {
    	
    	if (controllerName == null || controllerName.isEmpty() || 
    	    controllerConfiguration == null || 
    	    controllerConfiguration.getName().intern() != controllerName)
    		return Response.status(Response.Status.BAD_REQUEST).
    				        entity("A valid or matching controller names must be provided").
    				        build();
    	
    	PolicyController controller;
    	try {
    		controller = PolicyEngine.manager.updatePolicyController(controllerConfiguration);
	    	if (controller == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
	    				        entity(new Error(controllerName + "  does not exist")).
	    				        build();
		} catch (IllegalArgumentException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
			                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
							        entity(new Error(controllerName +  " not found: " + e.getMessage())).
							        build();
		} catch (Exception e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
                              controllerName, this.toString());
			return Response.status(Response.Status.NOT_ACCEPTABLE).
                                   entity(new Error(controllerName + " not acceptable")).build();
		}
    	
		return Response.status(Response.Status.OK).
                entity(controller).
                build();
    }
    
    public DroolsController getDroolsController(String controllerName) throws IllegalArgumentException {
		PolicyController controller = PolicyController.factory.get(controllerName);
    	if (controller == null)
    		throw new IllegalArgumentException(controllerName + "  does not exist");

		DroolsController drools = controller.getDrools();
    	if (drools == null)
    		throw new IllegalArgumentException(controllerName + "  has no drools configuration");
    	
    	return drools;
    }
    
    @GET
    @Path("engine/controllers/{controllerName}/decoders")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decoders(@PathParam("controllerName") String controllerName) {
		try {
			DroolsController drools = getDroolsController(controllerName);
			List<ProtocolCoderToolset> decoders = EventProtocolCoder.manager.getDecoders
														(drools.getGroupId(), drools.getArtifactId());			
			return Response.status(Response.Status.OK).
	                               entity(decoders).
	                               build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }
    
    @GET
    @Path("engine/controllers/{controllerName}/decoders/filters")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decoderFilters(@PathParam("controllerName") String controllerName) {
		try {
			DroolsController drools = getDroolsController(controllerName);
			List<CoderFilters> filters = EventProtocolCoder.manager.getDecoderFilters
							(drools.getGroupId(), drools.getArtifactId());
			return Response.status(Response.Status.OK).
		                    entity(filters).
		                    build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }
    
    @GET
    @Path("engine/controllers/{controllerName}/decoders/{topicName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decoder(@PathParam("controllerName") String controllerName,
    		                 @PathParam("topicName") String topicName) {
		try {
			DroolsController drools = getDroolsController(controllerName);
			ProtocolCoderToolset decoder = EventProtocolCoder.manager.getDecoders
							(drools.getGroupId(), drools.getArtifactId(), topicName);
			return Response.status(Response.Status.OK).
		                    entity(decoder).
		                    build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }    
    
    @GET
    @Path("engine/controllers/{controllerName}/decoders/{topicName}/filters")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decoderFilter(@PathParam("controllerName") String controllerName,
    		                       @PathParam("topicName") String topicName) {
		try {
			DroolsController drools = getDroolsController(controllerName);
			ProtocolCoderToolset decoder = EventProtocolCoder.manager.getDecoders
												(drools.getGroupId(), drools.getArtifactId(), topicName);
			if (decoder == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(topicName + "  does not exist")).
				        build();
			else
				return Response.status(Response.Status.OK).
	                    entity(decoder.getCoders()).
	                    build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }
    
    @GET
    @Path("engine/controllers/{controllerName}/decoders/{topicName}/filters/{factClassName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decoderFilter(@PathParam("controllerName") String controllerName,
    		                       @PathParam("topicName") String topicName,
    		                       @PathParam("factClassName") String factClass) {
		try {
			DroolsController drools = getDroolsController(controllerName);
	    	ProtocolCoderToolset decoder = EventProtocolCoder.manager.getDecoders
											(drools.getGroupId(), drools.getArtifactId(), topicName);
			CoderFilters filters = decoder.getCoder(factClass);
			if (filters == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(topicName + ":" + factClass + "  does not exist")).
				        build();
			else
				return Response.status(Response.Status.OK).
                        entity(filters).
                        build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }
    
    @POST
    @Path("engine/controllers/{controllerName}/decoders/{topicName}/filters/{factClassName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response decoderFilter(@PathParam("controllerName") String controllerName,
    		                      @PathParam("topicName") String topicName,
    		                      @PathParam("factClassName") String factClass,
    		                      JsonProtocolFilter configFilters) {
    	
    	if (configFilters == null) {
    		return Response.status(Response.Status.BAD_REQUEST).
    				        entity(new Error("Configuration Filters not provided")).
    				        build();
    	}
    	
		try {
			DroolsController drools = getDroolsController(controllerName);
	    	ProtocolCoderToolset decoder = EventProtocolCoder.manager.getDecoders
											(drools.getGroupId(), drools.getArtifactId(), topicName);
	    	CoderFilters filters = decoder.getCoder(factClass);
			if (filters == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(topicName + ":" + factClass + "  does not exist")).
				        build();
			filters.setFilter(configFilters);
			return Response.status(Response.Status.OK).
		                    entity(filters).
		                    build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }
    
    @GET
    @Path("engine/controllers/{controllerName}/decoders/{topicName}/filters/{factClassName}/rules")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decoderFilterRules(@PathParam("controllerName") String controllerName,
    		                          @PathParam("topicName") String topicName,
    		                          @PathParam("factClassName") String factClass) {
		try {
			DroolsController drools = getDroolsController(controllerName);
	    	ProtocolCoderToolset decoder = EventProtocolCoder.manager.getDecoders
											(drools.getGroupId(), drools.getArtifactId(), topicName);
	    	
	    	CoderFilters filters = decoder.getCoder(factClass);
			if (filters == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass + "  does not exist")).
				        build();
			
			JsonProtocolFilter filter = filters.getFilter();
			if (filter == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass + "  no filters")).
				        build();
			
			return Response.status(Response.Status.OK).
		                    entity(filter.getRules()).
		                    build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }
    
    @GET
    @Path("engine/controllers/{controllerName}/decoders/{topicName}/filters/{factClassName}/rules/{ruleName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decoderFilterRules(@PathParam("controllerName") String controllerName,
    		                          @PathParam("topicName") String topicName,
    		                          @PathParam("factClassName") String factClass,
    		                          @PathParam("ruleName") String ruleName) {
		try {
			DroolsController drools = getDroolsController(controllerName);
	    	ProtocolCoderToolset decoder = EventProtocolCoder.manager.getDecoders
											(drools.getGroupId(), drools.getArtifactId(), topicName);
	    	
	    	CoderFilters filters = decoder.getCoder(factClass);
			if (filters == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass + "  does not exist")).
				        build();
			
			JsonProtocolFilter filter = filters.getFilter();
			if (filter == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass + "  no filters")).
				        build();
			
			return Response.status(Response.Status.OK).
		                    entity(filter.getRules(ruleName)).
		                    build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }
    
    @DELETE
    @Path("engine/controllers/{controllerName}/decoders/{topicName}/filters/{factClassName}/rules/{ruleName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteDecoderFilterRule(@PathParam("controllerName") String controllerName,
	    		                          @PathParam("topicName") String topicName,
	    		                          @PathParam("factClassName") String factClass,
	    		                          @PathParam("ruleName") String ruleName,
	    		                          FilterRule rule) {
		
		try {
			DroolsController drools = getDroolsController(controllerName);
	    	ProtocolCoderToolset decoder = EventProtocolCoder.manager.getDecoders
											(drools.getGroupId(), drools.getArtifactId(), topicName);
	    	
	    	CoderFilters filters = decoder.getCoder(factClass);
			if (filters == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass + "  does not exist")).
				        build();
			
			JsonProtocolFilter filter = filters.getFilter();
			if (filter == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass + "  no filters")).
				        build();
			
			if (rule == null) {
				filter.deleteRules(ruleName);
				return Response.status(Response.Status.OK).
	                    entity(filter.getRules()).
	                    build();		
			}
			
			if (rule.getName() == null || !rule.getName().equals(ruleName))
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass + ":" + ruleName + 
				        		         " rule name request inconsistencies (" + rule.getName() + ")")).
				        build();
			
			filter.deleteRule(ruleName, rule.getRegex());
			return Response.status(Response.Status.OK).
		                    entity(filter.getRules()).
		                    build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }
    
    @PUT
    @Path("engine/controllers/{controllerName}/decoders/{topicName}/filters/{factClassName}/rules")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decoderFilterRule(@PathParam("controllerName") String controllerName,
	    		                      @PathParam("topicName") String topicName,
	    		                      @PathParam("factClassName") String factClass,
	    		                      JsonProtocolFilter.FilterRule rule) {
		
		try {
			DroolsController drools = getDroolsController(controllerName);
	    	ProtocolCoderToolset decoder = EventProtocolCoder.manager.getDecoders
											(drools.getGroupId(), drools.getArtifactId(), topicName);
	    	
	    	CoderFilters filters = decoder.getCoder(factClass);
			if (filters == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass + "  does not exist")).
				        build();
			
			JsonProtocolFilter filter = filters.getFilter();
			if (filter == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass + "  no filters")).
				        build();
			
			if (rule.getName() == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
				        entity(new Error(controllerName + ":" + topicName + ":" + factClass +  
				        		         " rule name request inconsistencies (" + rule.getName() + ")")).
				        build();
			
			filter.addRule(rule.getName(), rule.getRegex());
			return Response.status(Response.Status.OK).
		                    entity(filter.getRules()).
		                    build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(e.getMessage())).
					               build();
		}
    }
    
    @GET
    @Path("engine/controllers/{controllerName}/encoders")
    @Produces(MediaType.APPLICATION_JSON)
    public Response encoderFilters(@PathParam("controllerName") String controllerName) {   	
		List<CoderFilters> encoders;
		try {
			PolicyController controller = PolicyController.factory.get(controllerName);
	    	if (controller == null)
	    		return Response.status(Response.Status.BAD_REQUEST).
	    				        entity(new Error(controllerName + "  does not exist")).
	    				        build();
			DroolsController drools = controller.getDrools();
	    	if (drools == null)
	    		return Response.status(Response.Status.INTERNAL_SERVER_ERROR).
	    				        entity(new Error(controllerName + "  has not drools component")).
	    				        build();
			encoders = EventProtocolCoder.manager.getEncoderFilters
							(drools.getGroupId(), drools.getArtifactId());
		} catch (IllegalArgumentException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
	                  controllerName, this.toString());
			return Response.status(Response.Status.BAD_REQUEST).
					               entity(new Error(controllerName +  " not found: " + e.getMessage())).
					               build();
		} catch (IllegalStateException e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
                    controllerName, this.toString());
			return Response.status(Response.Status.NOT_ACCEPTABLE).
                            entity(new Error(controllerName + " is not accepting the request")).build();
		}
		
		return Response.status(Response.Status.OK).
                               entity(encoders).
                               build();
    }
    
    @POST
    @Path("engine/controllers/{controllerName}/decoders/{topic}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decode(@PathParam("controllerName") String controllerName,
    		                   @PathParam("topic") String topic,
    		                   String json) {
    	
    	PolicyController policyController = PolicyController.factory.get(controllerName);
    	
    	CodingResult result = new CodingResult();
		result.decoding = false;
		result.encoding = false;
		result.jsonEncoding = null;

		Object event;
    	try {
    		event = EventProtocolCoder.manager.decode
    					(policyController.getDrools().getGroupId(), 
    					 policyController.getDrools().getArtifactId(), 
    					 topic, 
    					 json);
    		result.decoding = true;
    	} catch (Exception e) {
    		return Response.status(Response.Status.BAD_REQUEST).
			        entity(new Error(e.getMessage())).
			        build();
    	}
    	
    	try {
    		result.jsonEncoding = EventProtocolCoder.manager.encode(topic, event);
    		result.encoding = true;
    	} catch (Exception e) {
    		return Response.status(Response.Status.OK).
			        entity(result).
			        build();
    	} 
    	
		return Response.status(Response.Status.OK).
                entity(result).
                build();
    }
    
	@GET
    @Path("engine/topics")
    @Produces(MediaType.APPLICATION_JSON)
    public TopicEndpoint topics() {
    	return TopicEndpoint.manager;
    }
    
	@SuppressWarnings("unchecked")
	@GET
    @Path("engine/topics/sources")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TopicSource> sources() {
    	return (List<TopicSource>) TopicEndpoint.manager.getTopicSources();
    }
    
    @SuppressWarnings("unchecked")
	@GET
    @Path("engine/topics/sinks")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TopicSink> sinks() {
    	return (List<TopicSink>) TopicEndpoint.manager.getTopicSinks();
    }
    
	@GET
    @Path("engine/topics/sources/ueb")
    @Produces(MediaType.APPLICATION_JSON)
    public List<UebTopicSource> uebSources() {
    	return TopicEndpoint.manager.getUebTopicSources();
    }
    
	@GET
    @Path("engine/topics/sinks/ueb")
    @Produces(MediaType.APPLICATION_JSON)
    public List<UebTopicSink> uebSinks() {
    	return (List<UebTopicSink>) TopicEndpoint.manager.getUebTopicSinks();
    }
    
	@GET
    @Path("engine/topics/sources/dmaap")
    @Produces(MediaType.APPLICATION_JSON)
    public List<DmaapTopicSource> dmaapSources() {
    	return TopicEndpoint.manager.getDmaapTopicSources();
    }
    
	@GET
    @Path("engine/topics/sinks/dmaap")
    @Produces(MediaType.APPLICATION_JSON)
    public List<DmaapTopicSink> dmaapSinks() {
    	return (List<DmaapTopicSink>) TopicEndpoint.manager.getDmaapTopicSinks();
    }
    
    @SuppressWarnings("unchecked")
    @GET
    @Path("engine/topics/{topic}/sources")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TopicSource> sourceTopic(@PathParam("topic") String topic) {
    	List<String> topics = new ArrayList<String>();
    	topics.add(topic);
    	
    	return (List<TopicSource>) TopicEndpoint.manager.getTopicSources(topics);
    }
    
    @SuppressWarnings("unchecked")
    @GET
    @Path("engine/topics/{topic}/sinks")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TopicSink> sinkTopic(@PathParam("topic") String topic) {
    	List<String> topics = new ArrayList<String>();
    	topics.add(topic);
    	
    	return (List<TopicSink>) TopicEndpoint.manager.getTopicSinks(topics);
    }
    
    
    @GET
    @Path("engine/topics/{topic}/ueb/source")
    @Produces(MediaType.APPLICATION_JSON)
    public UebTopicSource uebSourceTopic(@PathParam("topic") String topic) {
    	return TopicEndpoint.manager.getUebTopicSource(topic);
    }
    
    @GET
    @Path("engine/topics/{topic}/ueb/sink")
    @Produces(MediaType.APPLICATION_JSON)
    public UebTopicSink uebSinkTopic(@PathParam("topic") String topic) {
    	return TopicEndpoint.manager.getUebTopicSink(topic);
    }
    
    @GET
    @Path("engine/topics/{topic}/dmaap/source")
    @Produces(MediaType.APPLICATION_JSON)
    public DmaapTopicSource dmaapSourceTopic(@PathParam("topic") String topic) {
    	return TopicEndpoint.manager.getDmaapTopicSource(topic);
    }
    
    @GET
    @Path("engine/topics/{topic}/dmaap/sink")
    @Produces(MediaType.APPLICATION_JSON)
    public DmaapTopicSink dmaapSinkTopic(@PathParam("topic") String topic) {
    	return TopicEndpoint.manager.getDmaapTopicSink(topic);
    }
    
    @GET
    @Path("engine/topics/{topic}/ueb/source/events")
    @Produces(MediaType.APPLICATION_JSON)
    public Response uebSourceEvent(@PathParam("topic") String topicName) {
    	
    	UebTopicSource uebReader = TopicEndpoint.manager.getUebTopicSource(topicName);
    	String[] events = uebReader.getRecentEvents();
		return Response.status(Status.OK).
		        entity(events).
		        build();
    }
    
    @GET
    @Path("engine/topics/{topic}/ueb/sink/events")
    @Produces(MediaType.APPLICATION_JSON)
    public Response uebSinkEvent(@PathParam("topic") String topicName) {
    	
    	UebTopicSink uebSink = TopicEndpoint.manager.getUebTopicSink(topicName);
    	String[] events = uebSink.getRecentEvents();
		return Response.status(Status.OK).
		        entity(events).
		        build();
    }
    
    @GET
    @Path("engine/topics/{topic}/dmaap/source/events")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dmaapSourcevent(@PathParam("topic") String topicName) {
    	
    	DmaapTopicSource uebReader = TopicEndpoint.manager.getDmaapTopicSource(topicName);
    	String[] events = uebReader.getRecentEvents();
		return Response.status(Status.OK).
		        entity(events).
		        build();
    }
    
    @GET
    @Path("engine/topics/{topic}/dmaap/sink/events")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dmaapSinkEvent(@PathParam("topic") String topicName) {
    	
    	DmaapTopicSink uebSink = TopicEndpoint.manager.getDmaapTopicSink(topicName);
    	String[] events = uebSink.getRecentEvents();
		return Response.status(Status.OK).
		        entity(events).
		        build();
    }
    
    @PUT
    @Path("engine/topics/{topic}/ueb/sources/events")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uebOffer(@PathParam("topic") String topicName,
    		                 String json) {
    	try {
			UebTopicSource uebReader = TopicEndpoint.manager.getUebTopicSource(topicName);
			boolean success = uebReader.offer(json);
			if (success)
				return Response.status(Status.OK).
						        entity("Successfully injected event over " + topicName).
						        build();
			else
				return Response.status(Status.NOT_ACCEPTABLE).
						        entity("Failure to inject event over " + topicName).
						        build();
		} catch (Exception e) {
    		return Response.status(Response.Status.BAD_REQUEST).
			        entity(new Error(e.getMessage())).
			        build();
		} 
    }
    
    @PUT
    @Path("engine/topics/{topic}/dmaap/sources/events")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response dmaapOffer(@PathParam("topic") String topicName,
    		                   String json) {
    	try {
			DmaapTopicSource dmaapReader = TopicEndpoint.manager.getDmaapTopicSource(topicName);
			boolean success = dmaapReader.offer(json);
			if (success)
				return Response.status(Status.OK).
						        entity("Successfully injected event over " + topicName).
						        build();
			else
				return Response.status(Status.NOT_ACCEPTABLE).
						        entity("Failure to inject event over " + topicName).
						        build();
		} catch (Exception e) {
    		return Response.status(Response.Status.BAD_REQUEST).
			        entity(new Error(e.getMessage())).
			        build();
		} 
    }
    
    @PUT
    @Path("engine/topics/lock")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response lockTopics() {
    	boolean success = TopicEndpoint.manager.lock();
    	if (success)
    		return Response.status(Status.OK).
    				        entity("Endpoints are locked").
    				        build();
    	else
    		return Response.status(Status.SERVICE_UNAVAILABLE).
    				        entity("Endpoints cannot be locked").
    				        build();
    }
    
    @DELETE
    @Path("engine/topics/lock")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response unlockTopics() {
    	boolean success = TopicEndpoint.manager.unlock();
    	if (success)
    		return Response.status(Status.OK).
    				        entity("Endpoints are unlocked").
    				        build();
    	else
    		return Response.status(Status.SERVICE_UNAVAILABLE).
    				        entity("Endpoints cannot be unlocked").
    				        build();
    }
    
    @PUT
    @Path("engine/topics/{topic}/ueb/sources/lock")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response lockTopic(@PathParam("topic") String topicName) {
    	UebTopicSource reader = TopicEndpoint.manager.getUebTopicSource(topicName);  	
    	boolean success = reader.lock();
    	if (success)
    		return Response.status(Status.OK).
    				        entity("Endpoints are unlocked").
    				        build();
    	else
    		return Response.status(Status.SERVICE_UNAVAILABLE).
    				        entity("Endpoints cannot be unlocked").
    				        build();
    }
    
    @PUT
    @Path("engine/topics/{topic}/ueb/sources/unlock")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response unlockTopic(@PathParam("topic") String topicName) {
    	UebTopicSource reader = TopicEndpoint.manager.getUebTopicSource(topicName);  	
    	boolean success = reader.unlock();
    	if (success)
    		return Response.status(Status.OK).
    				        entity("Endpoints are unlocked").
    				        build();
    	else
    		return Response.status(Status.SERVICE_UNAVAILABLE).
    				        entity("Endpoints cannot be unlocked").
    				        build();
    }
    
    @PUT
    @Path("engine/controllers/{controllerName}/lock")
    @Produces(MediaType.APPLICATION_JSON)
    public Response lockController(@PathParam("controllerName") String controllerName) {
    	PolicyController policyController = PolicyController.factory.get(controllerName);
    	boolean success = policyController.lock();
    	if (success)
    		return Response.status(Status.OK).
    				        entity("Controller " + controllerName + " is now locked").
    				        build();
    	else
    		return Response.status(Status.SERVICE_UNAVAILABLE).
    				        entity("Controller " + controllerName + " cannot be locked").
    				        build();
    }  
    
    @DELETE
    @Path("engine/controllers/{controllerName}/lock")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unlockController(@PathParam("controllerName") String controllerName) {
    	PolicyController policyController = PolicyController.factory.get(controllerName);
    	boolean success = policyController.unlock();
    	if (success)
    		return Response.status(Status.OK).
    				        entity("Controller " + controllerName + " is now unlocked").
    				        build();
    	else
    		return Response.status(Status.SERVICE_UNAVAILABLE).
    				        entity("Controller " + controllerName + " cannot be unlocked").
    				        build();
    }
    
    @POST
    @Path("engine/util/coders/filters/rules/{ruleName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response rules(@DefaultValue("false") @QueryParam("negate") boolean negate,
    		              @PathParam("ruleName") String name,
    		              String regex) {   	
    	String literalRegex = Pattern.quote(regex);
    	if (negate)
    		literalRegex = "^(?!" + literalRegex + "$).*";
    	
		return Response.status(Status.OK).
				        entity(new JsonProtocolFilter.FilterRule(name,literalRegex)).
				        build();
    }
    
    @GET
    @Path("engine/util/uuid")
    public Response uuid() {   	
		return Response.status(Status.OK).
		        entity(UUID.randomUUID().toString()).
		        build();
    }
    
    /*
     * Helper classes for aggregation of results
     */
    
    
	public static class Endpoints {
		public List<TopicSource> sources;
		public List<TopicSink> sinks;
		
		public Endpoints(List<TopicSource> sources,
				         List<TopicSink> sinks) {
			this.sources = sources;
			this.sinks = sinks;
		}
	}
	
	public static class Endpoint {
		public TopicSource source;
		public TopicSink sink;
		
		public Endpoint(TopicSource source,
				           TopicSink sink) {
			this.source = source;
			this.sink = sink;
		}
	}
	
	public static class CodingResult {
		public String jsonEncoding;
		public Boolean encoding;
		public Boolean decoding;
	}
	
	public static class Error {
		public String error;

		/**
		 * @param error
		 */
		public Error(String error) {
			this.error = error;
		}
	}
}

