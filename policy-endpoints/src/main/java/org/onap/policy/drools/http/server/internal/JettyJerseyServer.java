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
package org.onap.policy.drools.http.server.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

import org.eclipse.jetty.servlet.ServletHolder;
import org.onap.policy.drools.utils.NetworkUtil;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.swagger.jersey.config.JerseyJaxrsConfig;

/**
 * REST Jetty Server that uses Jersey Servlets to support JAX-RS Web Services
 */
public class JettyJerseyServer extends JettyServletServer {

	/**
	 * Swagger API Base Path
	 */
	protected static final String SWAGGER_API_BASEPATH = "swagger.api.basepath";

	/**
	 * Swagger Context ID
	 */
	protected static final String SWAGGER_CONTEXT_ID = "swagger.context.id";

	/**
	 * Swagger Scanner ID
	 */
	protected static final String SWAGGER_SCANNER_ID = "swagger.scanner.id";

	/**
	 * Swagger Pretty Print
	 */
	protected static final String SWAGGER_PRETTY_PRINT = "swagger.pretty.print";

	/**
	 * Swagger Packages
	 */
	protected static final String SWAGGER_INIT_PACKAGES_PARAM_VALUE = "io.swagger.jaxrs.listing";

	/**
	 * Jersey Packages Init Param Name
	 */
	protected static final String JERSEY_INIT_PACKAGES_PARAM_NAME = "jersey.config.server.provider.packages";

	/**
	 * Jersey Packages Init Param Value
	 */
	protected static final String JERSEY_INIT_PACKAGES_PARAM_VALUE = "com.fasterxml.jackson.jaxrs.json";

	/**
	 * Jersey Classes Init Param Name
	 */
	protected static final String JERSEY_INIT_CLASSNAMES_PARAM_NAME = "jersey.config.server.provider.classnames";

	/**
	 * Jersey Jackson Classes Init Param Value
	 */
	protected static final String JERSEY_JACKSON_INIT_CLASSNAMES_PARAM_VALUE = "com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider";

	/**
	 * Jersey Swagger Classes Init Param Value
	 */
	protected static final String SWAGGER_INIT_CLASSNAMES_PARAM_VALUE = "io.swagger.jaxrs.listing.ApiListingResource," +
																        "io.swagger.jaxrs.listing.SwaggerSerializers";
	/**
	 * Logger
	 */
	protected static Logger logger = LoggerFactory.getLogger(JettyJerseyServer.class);

	/**
	 * Container for servlets
	 */
	protected HashMap<String, ServletHolder> servlets = new HashMap<>();

	/**
	 * Swagger ID
	 */
	protected String swaggerId = null;

	/**
	 * Constructor
	 *
	 * @param name name
	 * @param host host server host
	 * @param port port server port
	 * @param swagger support swagger?
	 * @param contextPath context path
	 *
	 * @throws IllegalArgumentException in invalid arguments are provided
	 */
	public JettyJerseyServer(String name, String host, int port, String contextPath, boolean swagger) {

		super(name, host, port, contextPath);
		if (swagger) {
			this.swaggerId = "swagger-" + this.port;
			attachSwaggerServlet();
		}
	}

	/**
	 * attaches a swagger initialization servlet
	 */
	protected void attachSwaggerServlet() {

		ServletHolder swaggerServlet = context.addServlet(JerseyJaxrsConfig.class, "/");

		String hostname = this.connector.getHost();
		if (hostname == null || hostname.isEmpty() || hostname.equals(NetworkUtil.IPv4_WILDCARD_ADDRESS)) {
			try {
				hostname = InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				logger.warn("{}: can't resolve connector's hostname: {}", this, hostname, e);
				hostname = "localhost";
			}
		}

		swaggerServlet.setInitParameter(SWAGGER_API_BASEPATH,
				"http://" + hostname + ":" + this.connector.getPort() + "/");
		swaggerServlet.setInitParameter(SWAGGER_CONTEXT_ID, swaggerId);
		swaggerServlet.setInitParameter(SWAGGER_SCANNER_ID, swaggerId);
		swaggerServlet.setInitParameter(SWAGGER_PRETTY_PRINT, "true");
		swaggerServlet.setInitOrder(2);

        if (logger.isDebugEnabled())
        	logger.debug("{}: Swagger Servlet has been attached: {}", this, swaggerServlet.dump());
	}

	/**
	 * retrieves cached server based on servlet path
	 *
	 * @param servletPath servlet path
	 * @return the jetty servlet holder
	 *
	 * @throws IllegalArgumentException if invalid arguments are provided
	 */
	protected synchronized ServletHolder getServlet(String servletPath) {

		ServletHolder jerseyServlet = servlets.get(servletPath);
		if (jerseyServlet == null) {
			jerseyServlet = context.addServlet
	                (org.glassfish.jersey.servlet.ServletContainer.class, servletPath);
			jerseyServlet.setInitOrder(0);
			servlets.put(servletPath, jerseyServlet);
		}

		return jerseyServlet;
	}

	@Override
	public synchronized void addServletPackage(String servletPath, String restPackage) {
		String servPath = servletPath;
    	if (restPackage == null || restPackage.isEmpty())
			throw new IllegalArgumentException("No discoverable REST package provided");
    
    	if (servPath == null || servPath.isEmpty())
    	    servPath = "/*";

		ServletHolder jerseyServlet = this.getServlet(servPath);

		String initClasses =
				jerseyServlet.getInitParameter(JERSEY_INIT_CLASSNAMES_PARAM_NAME);
		if (initClasses != null && !initClasses.isEmpty())
			logger.warn("Both packages and classes are used in Jetty+Jersey Configuration: {}", restPackage);

		String initPackages =
				jerseyServlet.getInitParameter(JERSEY_INIT_PACKAGES_PARAM_NAME);
		if (initPackages == null) {
			if (this.swaggerId != null) {
				initPackages = JERSEY_INIT_PACKAGES_PARAM_VALUE + "," +
				               SWAGGER_INIT_PACKAGES_PARAM_VALUE + "," +
						       restPackage;

	        	jerseyServlet.setInitParameter(SWAGGER_CONTEXT_ID, swaggerId);
	    		jerseyServlet.setInitParameter(SWAGGER_SCANNER_ID, swaggerId);
			} else {
				initPackages = JERSEY_INIT_PACKAGES_PARAM_VALUE + "," +
				               restPackage;
			}
		} else {
			initPackages = initPackages + "," + restPackage;
		}

        jerseyServlet.setInitParameter(JERSEY_INIT_PACKAGES_PARAM_NAME,  initPackages);

        if (logger.isDebugEnabled())
        	logger.debug("{}: added REST package: {}", this, jerseyServlet.dump());
	}

	@Override
	public synchronized void addServletClass(String servletPath, String restClass) {

    	if (restClass == null || restClass.isEmpty())
			throw new IllegalArgumentException("No discoverable REST class provided");
    
    	if (servletPath == null || servletPath.isEmpty())
    		servletPath = "/*";

		ServletHolder jerseyServlet = this.getServlet(servletPath);

		String initPackages =
				jerseyServlet.getInitParameter(JERSEY_INIT_PACKAGES_PARAM_NAME);
		if (initPackages != null && !initPackages.isEmpty())
			logger.warn("Both classes and packages are used in Jetty+Jersey Configuration: {}", restClass);

		String initClasses =
				jerseyServlet.getInitParameter(JERSEY_INIT_CLASSNAMES_PARAM_NAME);
		if (initClasses == null) {
			if (this.swaggerId != null) {
				initClasses = JERSEY_JACKSON_INIT_CLASSNAMES_PARAM_VALUE + "," +
						      SWAGGER_INIT_CLASSNAMES_PARAM_VALUE + "," +
				              restClass;

	        	jerseyServlet.setInitParameter(SWAGGER_CONTEXT_ID, swaggerId);
	    		jerseyServlet.setInitParameter(SWAGGER_SCANNER_ID, swaggerId);
			} else {
				initClasses = JERSEY_JACKSON_INIT_CLASSNAMES_PARAM_VALUE + "," + restClass;
			}
		} else {
			initClasses = initClasses + "," + restClass;
		}

        jerseyServlet.setInitParameter(JERSEY_INIT_CLASSNAMES_PARAM_NAME, initClasses);

        if (logger.isDebugEnabled())
        	logger.debug("{}: added REST class: {}", this, jerseyServlet.dump());
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("JettyJerseyServer [servlets=").append(servlets).append(", swaggerId=").append(swaggerId)
				.append(", toString()=").append(super.toString()).append("]");
		return builder.toString();
	}
}
