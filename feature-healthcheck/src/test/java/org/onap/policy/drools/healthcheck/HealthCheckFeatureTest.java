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

package org.onap.policy.drools.healthcheck;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;






import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.onap.policy.drools.healthcheck.HealthCheck.Report;
import org.onap.policy.drools.healthcheck.HealthCheck.Reports;
import org.onap.policy.drools.http.client.HttpClient;
import org.onap.policy.drools.http.server.HttpServletServer;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckFeatureTest {

    /**
     * Healthcheck Configuration File
     */
    private static final String HEALTH_CHECK_PROPERTIES_FILE = "feature-healthcheck.properties";
    
    private static final Path healthCheckPropsPath = Paths.get(SystemPersistence.manager.getConfigurationPath().toString(),
            HEALTH_CHECK_PROPERTIES_FILE);
    
    private static final Path healthCheckPropsBackupPath = Paths.get(SystemPersistence.manager.getConfigurationPath().toString(),
            HEALTH_CHECK_PROPERTIES_FILE + ".bak");
    
    
    /**
     * logger
     */
    private static Logger logger = LoggerFactory.getLogger(HealthCheckFeatureTest.class);

    private static Properties httpProperties = new Properties();
    
    
    @BeforeClass
    public static void setup(){
        
        httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES, "HEALTHCHECK");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, 
             "localhost");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, 
             "7777");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, 
             "username");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, 
             "password");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_REST_CLASSES_SUFFIX, 
              org.onap.policy.drools.healthcheck.RestMockHealthCheck.class.getName());
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_MANAGED_SUFFIX, 
             "true");

        
        httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES, "HEALTHCHECK");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, 
             "localhost");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, 
             "7777");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_URL_SUFFIX, 
             "healthcheck/test");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_HTTPS_SUFFIX, 
             "false");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, 
             "username");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, 
             "password");
        httpProperties.setProperty
            (PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK" + PolicyProperties.PROPERTY_MANAGED_SUFFIX, 
             "true");
        

        configDirSetup();
     
    }
    
    @AfterClass
    public static void tearDown() {
        logger.info("-- tearDown() --");

        configDirCleanup();
    }

    //@Ignore
	@Test
	public void test() {
	    
		HealthCheckFeature feature = new HealthCheckFeature();
		feature.afterStart(PolicyEngine.manager);
		
		Reports reports = HealthCheck.monitor.healthCheck();

		for (Report rpt : reports.details) {
		    if (rpt.name == "HEALTHCHECK") {
		        assertTrue(rpt.healthy);
		        assertEquals(200,rpt.code);
		        assertEquals("All Alive", rpt.message);
		        break;
		    }
		}
		
		feature.afterShutdown(PolicyEngine.manager);
		
		try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            logger.error("Error stopping thread", e);
        }
		
		HttpServletServer server = HttpServletServer.factory.get(7777);
		assertFalse(server.isAlive());
	
	}

	
	  /**
	   * setup up config directory
	   */
	  protected static void configDirSetup() {
	      
	    File origPropsFile = new File(healthCheckPropsPath.toString());
	    File backupPropsFile = new File(healthCheckPropsBackupPath.toString());
	    
	    try {
	            Files.deleteIfExists(healthCheckPropsBackupPath);
	            origPropsFile.renameTo(backupPropsFile);
	            OutputStream fout = new FileOutputStream(origPropsFile);
	            httpProperties.store(fout,"");
	            
	          } catch (final Exception e) {
	            logger.info("Problem cleaning {}", healthCheckPropsPath, e);
	          }	        
	    }
	  
	   /**
       * cleanup up config directory
       */
      protected static void configDirCleanup() {
          
        File origPropsFile = new File(healthCheckPropsBackupPath.toString());
        File backupPropsFile = new File(healthCheckPropsPath.toString());
        
        try {
                Files.deleteIfExists(healthCheckPropsPath);
                origPropsFile.renameTo(backupPropsFile);             
              } catch (final Exception e) {
                logger.info("Problem cleaning {}", healthCheckPropsPath, e);
              }         
        }

}
