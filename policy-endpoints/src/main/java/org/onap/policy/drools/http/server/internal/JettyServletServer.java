/*-
 * ============LICENSE_START=======================================================
 * ONAP
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

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLog;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.onap.policy.drools.http.server.HttpServletServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Http Server implementation using Embedded Jetty
 */
public abstract class JettyServletServer implements HttpServletServer, Runnable {

	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(JettyServletServer.class);

	/**
	 * server name
	 */
	protected final String name;

	/**
	 * server host address
	 */
	protected final String host;

	/**
	 * server port to bind
	 */
	protected final int port;

	/**
	 * server auth user name
	 */
	protected String user;

	/**
	 * server auth password name
	 */
	protected String password;

	/**
	 * server base context path
	 */
	protected final String contextPath;

	/**
	 * embedded jetty server
	 */
	protected final Server jettyServer;

	/**
	 * servlet context
	 */
	protected final ServletContextHandler context;

	/**
	 * jetty connector
	 */
	protected final ServerConnector connector;

	/**
	 * jetty thread
	 */
	protected volatile Thread jettyThread;

	/**
	 * start condition
	 */
	protected Object startCondition = new Object();

	/**
	 * constructor
	 *
	 * @param name server name
	 * @param host server host
	 * @param port server port
	 * @param contextPath context path
	 *
	 * @throws IllegalArgumentException if invalid parameters are passed in
	 */
	public JettyServletServer(String name, String host, int port, String contextPath) {
		String srvName = name;
		String srvHost = host;
		String ctxtPath = contextPath;

		if (srvName == null || srvName.isEmpty())
			srvName = "http-" + port;

		if (port <= 0 && port >= 65535)
			throw new IllegalArgumentException("Invalid Port provided: " + port);

		if (srvHost == null || srvHost.isEmpty())
		    srvHost = "localhost";

		if (ctxtPath == null || ctxtPath.isEmpty())
		    ctxtPath = "/";

		this.name = srvName;

		this.host = srvHost;
		this.port = port;

		this.contextPath = ctxtPath;

        this.context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        this.context.setContextPath(ctxtPath);

        this.jettyServer = new Server();
        this.jettyServer.setRequestLog(new Slf4jRequestLog());

        this.connector = new ServerConnector(this.jettyServer);
        this.connector.setName(srvName);
        this.connector.setReuseAddress(true);
        this.connector.setPort(port);
        this.connector.setHost(srvHost);

        this.jettyServer.addConnector(this.connector);
        this.jettyServer.setHandler(context);
	}

	@Override
	public void setBasicAuthentication(String user, String password, String servletPath) {
        String srvltPath = servletPath;

	    if (user == null || user.isEmpty() || password == null || password.isEmpty())
        	throw new IllegalArgumentException("Missing user and/or password");

        if (srvltPath == null || srvltPath.isEmpty())
            srvltPath = "/*";
        	     
    	HashLoginService hashLoginService = new HashLoginService();
        hashLoginService.putUser(user,
        		                Credential.getCredential(password),
        		                new String[] {"user"});
        hashLoginService.setName(this.connector.getName() + "-login-service");

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec(srvltPath);

        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        securityHandler.setAuthenticator(new BasicAuthenticator());
        securityHandler.setRealmName(this.connector.getName() + "-realm");
        securityHandler.addConstraintMapping(constraintMapping);
        securityHandler.setLoginService(hashLoginService);

        this.context.setSecurityHandler(securityHandler);

		this.user = user;
		this.password = password;
	}

	/**
	 * jetty server execution
	 */
	@Override
	public void run() {
        try {
        	logger.info("{}: STARTING", this);
        
            this.jettyServer.start();

            if (logger.isInfoEnabled())
            	logger.info("{}: STARTED: {}", this, this.jettyServer.dump());

        	synchronized(this.startCondition) {
        		this.startCondition.notifyAll();
        	}
        
            this.jettyServer.join();
        } catch (Exception e) {
			logger.error("{}: error found while bringing up server", this, e);
		}
	}

	@Override
	public boolean waitedStart(long maxWaitTime) throws InterruptedException {
		logger.info("{}: WAITED-START", this);

		if (maxWaitTime < 0)
			throw new IllegalArgumentException("max-wait-time cannot be negative");

		long pendingWaitTime = maxWaitTime;

		if (!this.start())
			return false;

		synchronized (this.startCondition) {

			while (!this.jettyServer.isRunning()) {
				try {
					long startTs = System.currentTimeMillis();

					this.startCondition.wait(pendingWaitTime);

					if (maxWaitTime == 0)
						/* spurious notification */
						continue;

					long endTs = System.currentTimeMillis();
					pendingWaitTime = pendingWaitTime - (endTs - startTs);

					logger.info("{}: pending time is {} ms.", this, pendingWaitTime);

					if (pendingWaitTime <= 0)
						return false;

				} catch (InterruptedException e) {
					logger.warn("{}: waited-start has been interrupted", this);
					throw e;
				}
			}

			return this.jettyServer.isRunning();
		}
	}

	@Override
	public boolean start() {
		logger.info("{}: STARTING", this);

		synchronized(this) {
			if (jettyThread == null ||
				!this.jettyThread.isAlive()) {

				this.jettyThread = new Thread(this);
				this.jettyThread.setName(this.name + "-" + this.port);
				this.jettyThread.start();
			}
		}

		return true;
	}

	@Override
	public boolean stop() {
		logger.info("{}: STOPPING", this);

		synchronized(this) {
			if (jettyThread == null) {
				return true;
			}

			if (!jettyThread.isAlive()) {
				this.jettyThread = null;
			}

			try {
				this.connector.stop();
			} catch (Exception e) {
				logger.error("{}: error while stopping management server", this, e);
			}

			try {
				this.jettyServer.stop();
			} catch (Exception e) {
				logger.error("{}: error while stopping management server", this, e);
				return false;
			}

			Thread.yield();
		}

		return true;
	}

	@Override
	public void shutdown() {
		logger.info("{}: SHUTTING DOWN", this);

		this.stop();

		if (this.jettyThread == null)
			return;

		Thread jettyThreadCopy = this.jettyThread;

		if (jettyThreadCopy.isAlive()) {
			try {
				jettyThreadCopy.join(2000L);
			} catch (InterruptedException e) {
				logger.warn("{}: error while shutting down management server", this);
				Thread.currentThread().interrupt();
			}
			if (!jettyThreadCopy.isInterrupted()) {
				try {
					jettyThreadCopy.interrupt();
				} catch(Exception e) {
					// do nothing
					logger.warn("{}: exception while shutting down (OK)", this, e);
				}
			}
		}

		this.jettyServer.destroy();
	}

	@Override
	public boolean isAlive() {
		if (this.jettyThread != null)
			return this.jettyThread.isAlive();

		return false;
	}

	@Override
	public int getPort() {
		return this.port;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the host
	 */
	public String getHost() {
		return host;
	}

	/**
	 * @return the user
	 */
	public String getUser() {
		return user;
	}

	/**
	 * @return the password
	 */
	@JsonIgnore
	public String getPassword() {
		return password;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("JettyServer [name=").append(name).append(", host=").append(host).append(", port=").append(port)
				.append(", user=").append(user).append(", password=").append(password != null).append(", contextPath=")
				.append(contextPath).append(", jettyServer=").append(jettyServer).append(", context=").append(this.context)
				.append(", connector=").append(connector).append(", jettyThread=").append(jettyThread)
				.append("]");
		return builder.toString();
	}

}
