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
package org.onap.policy.drools.persistence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.persistence.FileSystemPersistence;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.PolicyProperties;

public class FileSystemPersistenceTest {
    
    private static Properties policyEnginePropsObject;
    private static Properties foo2ControllerPropsObject;

    private static final String OTHER_CONFIG_DIR = "tmp";
    private static final String POLICY_ENGINE_PROPERTIES_FILE = "policy-engine.properties";
    private static final String FOO1_CONTROLLER = "foo1";
    private static final String FOO1_CONTROLLER_PROPERTIES = FOO1_CONTROLLER + "-controller.properties";

    private static final String FOO2_CONTROLLER = "foo2";
    private static final String FOO2_CONTROLLER_PROPERTIES = FOO2_CONTROLLER + "-controller.properties";
    
    private static final String ENV_PROPS = "envProps";
    private static final String ENV_PROPS_FILE = ENV_PROPS + ".environment";
    
    @BeforeClass
    public static void setUp() {
        policyEnginePropsObject = new Properties();
        policyEnginePropsObject.setProperty("foo", "bar");
        policyEnginePropsObject.setProperty("fiz", "buz");

        foo2ControllerPropsObject = new Properties();
        foo2ControllerPropsObject.setProperty("foo", "bar");
        foo2ControllerPropsObject.setProperty("fiz", "buz");
        configDirSetup();
        
    }
    
    @AfterClass
    public static void tearDown() {
        configDirCleanup();
    }
    
    @Test
    public void test() {
        FileSystemPersistence fsp = new FileSystemPersistence();
        Path configurationDirectory = Paths.get(OTHER_CONFIG_DIR);
        List<Properties> controllerPropertiesList = new ArrayList<>();
        List<Properties> envPropertesList = new ArrayList<>();
        
        Properties emptyProps = new Properties();
        controllerPropertiesList.add(emptyProps);
        controllerPropertiesList.add(foo2ControllerPropsObject);
        envPropertesList.add(emptyProps);
        
        fsp.setConfigurationDir(OTHER_CONFIG_DIR);
        assertEquals(fsp.getConfigurationPath().toString(), OTHER_CONFIG_DIR);
        assertTrue(!Files.notExists(configurationDirectory));
        
        assertEquals(fsp.getEngineProperties(),policyEnginePropsObject);

        assertTrue(fsp.backupController(FOO1_CONTROLLER));
        
        assertTrue(fsp.storeController(FOO2_CONTROLLER, foo2ControllerPropsObject));
        assertTrue(Files.exists(Paths.get(OTHER_CONFIG_DIR + "/" + FOO2_CONTROLLER_PROPERTIES)));
        
        assertEquals(fsp.getControllerProperties(FOO2_CONTROLLER), foo2ControllerPropsObject); 
        assertEquals(fsp.getControllerProperties(), controllerPropertiesList);
        
        assertEquals(fsp.getProperties(FOO2_CONTROLLER + "-controller"), foo2ControllerPropsObject);
        
        assertEquals(fsp.getEnvironmentProperties(ENV_PROPS), emptyProps);
        assertEquals(fsp.getEnvironmentProperties(), envPropertesList);
        
        assertTrue(fsp.deleteController(FOO2_CONTROLLER));
        assertTrue(Files.notExists(Paths.get(OTHER_CONFIG_DIR + "/" + FOO2_CONTROLLER_PROPERTIES)));
        
    }
    

    /**
     * setup up config directory
     */
    protected static void configDirSetup() {
        
      Path configDir = Paths.get(OTHER_CONFIG_DIR);
      Path policyEnginePropsPath = Paths.get(OTHER_CONFIG_DIR + "/" + FileSystemPersistence.PROPERTIES_FILE_ENGINE);
      Path testControllerPath = Paths.get(OTHER_CONFIG_DIR + "/" + FOO1_CONTROLLER_PROPERTIES);
      Path environmentPropertiesPath = Paths.get(OTHER_CONFIG_DIR + "/" + ENV_PROPS_FILE);
      try {

              if (Files.notExists(configDir)) {
                      Files.createDirectories(configDir);
              }
              
              if (Files.notExists(testControllerPath)) {
                      Files.createFile(testControllerPath);
              }
              
              if (Files.notExists(environmentPropertiesPath)) {
                      Files.createFile(environmentPropertiesPath);
              }
              
              
              
              Files.deleteIfExists(policyEnginePropsPath);
              OutputStream fout = new FileOutputStream(policyEnginePropsPath.toFile());
              policyEnginePropsObject.store(fout, "");
              
            } catch (final Exception e) {
              //logger.info("Problem cleaning {}", healthCheckPropsPath, e);
            }         
      }
    

     /**
     * cleanup up config directory
     */
    protected static void configDirCleanup() {
        
        try {
            Files.deleteIfExists(Paths.get(OTHER_CONFIG_DIR + "/" + POLICY_ENGINE_PROPERTIES_FILE));
            Files.deleteIfExists(Paths.get(OTHER_CONFIG_DIR + "/" + FOO1_CONTROLLER_PROPERTIES));
            Files.deleteIfExists(Paths.get(OTHER_CONFIG_DIR + "/" + FOO1_CONTROLLER_PROPERTIES + ".bak"));
            Files.deleteIfExists(Paths.get(OTHER_CONFIG_DIR + "/" + FOO2_CONTROLLER_PROPERTIES));
            Files.deleteIfExists(Paths.get(OTHER_CONFIG_DIR + "/" + FOO2_CONTROLLER_PROPERTIES + ".bak"));
            Files.deleteIfExists(Paths.get(OTHER_CONFIG_DIR + "/" + ENV_PROPS_FILE));
            Files.deleteIfExists(Paths.get(OTHER_CONFIG_DIR));
            
        } catch (IOException e) {
            //logger.error();
        }
        
    }
          
}
