/*
 * ============LICENSE_START=======================================================
 * feature-healthcheck
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

package org.onap.policy.drools.healthcheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.core.Response;

import org.onap.policy.drools.http.client.HttpClient;
import org.onap.policy.drools.http.server.HttpServletServer;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.Startable;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Healthcheck
 */
public interface HealthCheck extends Startable {

	/**
	 * Healthcheck Monitor
	 */
	public static final HealthCheck monitor = new HealthCheckMonitor();
	
	/**
	 * Healthcheck Report
	 */
	public static class Report {
		/**
		 * Named Entity in the report
		 */
		private String name;
		
		/**
		 * URL queried
		 */
		private String url;
		
		/**
		 * healthy?
		 */
		private boolean healthy;
		
		/**
		 * return code
		 */
		private int code;
		
		/**
		 * Message from remote entity
		 */
		private String message;
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Report [name=");
			builder.append(getName());
			builder.append(", url=");
			builder.append(getUrl());
			builder.append(", healthy=");
			builder.append(isHealthy());
			builder.append(", code=");
			builder.append(getCode());
			builder.append(", message=");
			builder.append(getMessage());
			builder.append("]");
			return builder.toString();
		}

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
	}
	
	/**
	 * Report aggregation
	 */
	public static class Reports {
		private boolean healthy;
		private List<Report> details = new ArrayList<>();
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Reports [healthy=");
			builder.append(isHealthy());
			builder.append(", details=");
			builder.append(getDetails());
			builder.append("]");
			return builder.toString();
		}

        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        public List<Report> getDetails() {
            return details;
        }

        public void setDetails(List<Report> details) {
            this.details = details;
        }
	}
	
	/**
	 * perform a healthcheck
	 * @return a report
	 */
	public Reports healthCheck();
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
	@Override
	public Reports healthCheck() {	
		Reports reports = new Reports();
		reports.setHealthy(PolicyEngine.manager.isAlive());
		
		HealthCheck.Report engineReport = new Report();
		engineReport.setHealthy(PolicyEngine.manager.isAlive());
		engineReport.setName("PDP-D");
		engineReport.setUrl("self");
		engineReport.setCode(PolicyEngine.manager.isAlive() ? 200 : 500);
		engineReport.setMessage(PolicyEngine.manager.isAlive() ? "alive" : "not alive");
		reports.getDetails().add(engineReport);
		
		for (HttpClient client : clients) {
			HealthCheck.Report report = new Report();
			report.setName(client.getName());
			report.setUrl(client.getBaseUrl());
			report.setHealthy(true);
			try {
				Response response = client.get();
				report.setCode(response.getStatus());
				if (report.getCode() != 200) {
					report.setHealthy(false);
					reports.setHealthy(false);
				}
        
				report.setMessage(getHttpBody(response, client));
			} catch (Exception e) {
				logger.warn("{}: cannot contact http-client {}", this, client, e);
				
				report.setHealthy(false);
				reports.setHealthy(false);
			}
			reports.getDetails().add(report);
		}
		return reports;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean start() {
		
		try {
			this.healthCheckProperties = SystemPersistence.manager.getProperties(HealthCheckFeature.CONFIGURATION_PROPERTIES_NAME);
			this.servers = HttpServletServer.factory.build(healthCheckProperties);
			this.clients = HttpClient.factory.build(healthCheckProperties);
			
			for (HttpServletServer server : servers) {
			    startServer(server);
			}
		} catch (Exception e) {
			logger.warn("{}: cannot start {}", this, e);		
			return false;
		}
		
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean stop() {
		
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
	public void shutdown() {
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
	public List<HttpServletServer> getServers() {
		return this.servers;
	}
	
	/**
	 * @return list of attached Http Clients
	 */
	public List<HttpClient> getClients() {
		return this.clients;
	}
	
	public String getHttpBody(Response response, HttpClient client) {
        
	    String body = null;
        try {
            body = HttpClient.getBody(response, String.class);
        } catch (Exception e) {
            logger.info("{}: cannot get body from http-client {}", this,
                    client, e);
        }
        
        return body;
	}
	
	public void startServer(HttpServletServer server) {
        try {
            server.start();
        } catch (Exception e) {
            logger.warn("{}: cannot start http-server {}", this, server, e);
        }
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
