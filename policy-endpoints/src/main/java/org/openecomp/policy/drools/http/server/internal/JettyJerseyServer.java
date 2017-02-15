/*-
 * ============LICENSE_START=======================================================
 * policy-endpoints
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
package org.openecomp.policy.drools.http.server.internal;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jetty.servlet.ServletHolder;

import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;

/**
 * REST Jetty Server using Jersey
 */
public class JettyJerseyServer extends JettyServletServer {
	
	protected static final String JERSEY_PACKAGES_PARAM = "jersey.config.server.provider.packages";
	protected static final String JERSEY_CLASSNAMES_PARAM = "jersey.config.server.provider.classnames";
	
	protected static Logger logger = FlexLogger.getLogger(JettyJerseyServer.class);
	
	protected ArrayList<String> packages = new ArrayList<String>();
	protected HashMap<String, ServletHolder> servlets = 
							new HashMap<String, ServletHolder>();
	
	public JettyJerseyServer(String name, String host, int port, String contextPath) 
	       throws IllegalArgumentException {		
		super(name, host, port, contextPath);
	}
	
	protected synchronized ServletHolder getServlet(String servletPath) 
			  throws IllegalArgumentException {
		
		if (servletPath == null || servletPath.isEmpty())
			servletPath = "/*";
		
		ServletHolder jerseyServlet = servlets.get(servletPath);
		if (jerseyServlet == null) {
			jerseyServlet = context.addServlet
	                (org.glassfish.jersey.servlet.ServletContainer.class, servletPath);  
			jerseyServlet.setInitOrder(0);
			String initPackages = 
					jerseyServlet.getInitParameter(JERSEY_PACKAGES_PARAM);
			if (initPackages == null) {
		        jerseyServlet.setInitParameter(
		        		JERSEY_PACKAGES_PARAM,
		        		"com.jersey.jaxb,com.fasterxml.jackson.jaxrs.json");
			}
			this.servlets.put(servletPath, jerseyServlet);
		}
		
		return jerseyServlet;
	}
	
	@Override
	public synchronized void addServletPackage(String servletPath, String restPackage) 
	       throws IllegalArgumentException, IllegalStateException {
		
    	if (restPackage == null || restPackage.isEmpty())
			throw new IllegalArgumentException("No discoverable REST package provided");
		
		ServletHolder jerseyServlet = this.getServlet(servletPath);
		if (jerseyServlet == null)
			throw new IllegalStateException("Unexpected, no Jersey Servlet class");
		
		String initPackages = 
				jerseyServlet.getInitParameter(JERSEY_PACKAGES_PARAM);
		if (initPackages == null)
			throw new IllegalStateException("Unexpected, no Init Parameters loaded");
		
        jerseyServlet.setInitParameter(
        		JERSEY_PACKAGES_PARAM,
        		initPackages + "," + restPackage);
        
        if (logger.isDebugEnabled())
        	logger.debug(this + "Added REST Package: " + jerseyServlet.dump());
	}
	
	@Override
	public synchronized void addServletClass(String servletPath, String restClass) 
		       throws IllegalArgumentException, IllegalStateException {
			
    	if (restClass == null || restClass.isEmpty())
			throw new IllegalArgumentException("No discoverable REST class provided");
		
		ServletHolder jerseyServlet = this.getServlet(servletPath);
		if (jerseyServlet == null)
			throw new IllegalStateException("Unexpected, no Jersey Servlet class");
		
		String initClasses = 
				jerseyServlet.getInitParameter(JERSEY_CLASSNAMES_PARAM);
		if (initClasses == null)
			initClasses = restClass;
		else
			initClasses = initClasses + "," + restClass;
		
        jerseyServlet.setInitParameter(
        		JERSEY_CLASSNAMES_PARAM,
        		initClasses);
        
        if (logger.isDebugEnabled())
        	logger.debug(this + "Added REST Class: " + jerseyServlet.dump());
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("JerseyJettyServer [packages=").append(packages).append(", servlets=").append(servlets)
			   .append(", toString()=").append(super.toString()).append("]");
		return builder.toString();
	}
}
