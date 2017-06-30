/*-
 * ============LICENSE_START=======================================================
 * feature-healthcheck
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

package org.openecomp.policy.drools.healthcheck;

import java.util.ArrayList;
import java.util.Properties;

import javax.ws.rs.core.Response;

import org.openecomp.policy.drools.http.client.HttpClient;
import org.openecomp.policy.drools.http.server.HttpServletServer;
import org.openecomp.policy.drools.persistence.SystemPersistence;
import org.openecomp.policy.drools.properties.Startable;
import org.openecomp.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Healthcheck
 */
public interface HealthCheck extends Startable {
	
	/**
	 * Healthcheck Report
	 */
	public static class Report {
		/**
		 * Named Entity in the report
		 */
		public String name;
		
		/**
		 * URL queried
		 */
		public String url;
		
		/**
		 * healthy?
		 */
		public boolean healthy;
		
		/**
		 * return code
		 */
		public int code;
		
		/**
		 * Message from remote entity
		 */
		public String message;
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Report [name=");
			builder.append(name);
			builder.append(", url=");
			builder.append(url);
			builder.append(", healthy=");
			builder.append(healthy);
			builder.append(", code=");
			builder.append(code);
			builder.append(", message=");
			builder.append(message);
			builder.append("]");
			return builder.toString();
		}
	}
	
	/**
	 * Report aggregation
	 */
	public static class Reports {
		public boolean healthy;
		public ArrayList<Report> details = new ArrayList<>();
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Reports [healthy=");
			builder.append(healthy);
			builder.append(", details=");
			builder.append(details);
			builder.append("]");
			return builder.toString();
		}
	}
	
	/**
	 * perform a healthcheck
	 * @return a report
	 */
	public Reports healthCheck();

	/**
	 * Healthcheck Monitor
	 */
	public static final HealthCheck monitor = new HealthCheckMonitor();
}

/**
 * Healthcheck Monitor
 */
class HealthCheckMonitor implements HealthCheck {

	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(HealthCheckMonitor.class);
	
	/**
	 * attached http servers
	 */
	protected volatile ArrayList<HttpServletServer> servers = new ArrayList<>();
	
	/**
	 * attached http clients
	 */
	protected volatile ArrayList<HttpClient> clients = new ArrayList<>();
	
	/**
	 * healthcheck configuration
	 */
	protected volatile Properties healthCheckProperties = null; 
	
	/**
	 * {@inheritDoc}
	 */
	public Reports healthCheck() {	
		Reports reports = new Reports();
		reports.healthy = PolicyEngine.manager.isAlive();
		
		HealthCheck.Report engineReport = new Report();
		engineReport.healthy = PolicyEngine.manager.isAlive();
		engineReport.name = "PDP-D";
		engineReport.url = "self";
		engineReport.code = (PolicyEngine.manager.isAlive()) ? 200 : 500;
		engineReport.message = (PolicyEngine.manager.isAlive()) ? "alive" : "not alive";
		reports.details.add(engineReport);
		
		for (HttpClient client : clients) {
			HealthCheck.Report report = new Report();
			report.name = client.getName();
			report.url = client.getBaseUrl();
			report.healthy = true;
			try {
				Response response = client.get();
				report.code = response.getStatus();
				if (report.code != 200) {
					report.healthy = false;
					reports.healthy = false;
				}
					
				try {
					report.message = HttpClient.getBody(response, String.class);
				} catch (Exception e) {
					logger.warn("{}: cannot get body from http-client {}", this, client, e);
				}
			} catch (Exception e) {
				report.healthy = false;
				reports.healthy = false;
			}
			reports.details.add(report);
		}
		return reports;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean start() throws IllegalStateException {
		
		try {
			this.healthCheckProperties = SystemPersistence.manager.getProperties(HealthCheckFeature.CONFIGURATION_PROPERTIES_NAME);
			this.servers = HttpServletServer.factory.build(healthCheckProperties);
			this.clients = HttpClient.factory.build(healthCheckProperties);
			
			for (HttpServletServer server : servers) {
				try {
					server.start();
				} catch (Exception e) {
					logger.warn("{}: cannot start http-server {}", this, server, e);
				}
			}
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean stop() throws IllegalStateException {
		
		for (HttpServletServer server : servers) {
			try {
				server.stop();
			} catch (Exception e) {
				logger.warn("{}: cannot stop http-server {}", this, server, e);
			}
		}
		
		for (HttpClient client : clients) {
			try {
				client.stop();
			} catch (Exception e) {
				logger.warn("{}: cannot stop http-client {}", this, client, e);
			}
		}
		
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() throws IllegalStateException {
		this.stop();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean isAlive() {
		return this.healthCheckProperties != null;
	}
	
	/**
	 * @return list of attached Http Servers
	 */
	public ArrayList<HttpServletServer> getServers() {
		return this.servers;
	}
	
	/**
	 * @return list of attached Http Clients
	 */
	public ArrayList<HttpClient> getClients() {
		return this.clients;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("HealthCheckMonitor [servers=");
		builder.append(servers);
		builder.append(", clients=");
		builder.append(clients);
		builder.append("]");
		return builder.toString();
	}
	
}
