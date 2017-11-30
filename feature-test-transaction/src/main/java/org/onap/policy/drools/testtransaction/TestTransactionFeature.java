/*-
 * ============LICENSE_START=======================================================
 * feature-test-transaction
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

package org.onap.policy.drools.testtransaction;

import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * TestTransactionFeature implements the PolicyControllerFeatureAPI.
 * TestTransactionFeature is the interface to the TestTransaction feature logic.
 * 
 */
public class TestTransactionFeature implements PolicyControllerFeatureAPI {

    // get an instance of logger 
	private static final Logger logger = LoggerFactory.getLogger(TestTransactionFeature.class); 
	
    @Override
    public boolean afterStart(PolicyController controller){
        
        logger.info("TEST_TRANSACTION FEATURE LOADED");
        
        if (controller.isAlive() &&
            !controller.isLocked() && 
            controller.getDrools().isBrained())
        	TestTransaction.manager.register(controller);
        
        return false;
    }
    
    @Override
    public boolean afterLock(PolicyController controller) {
        logger.info("controller {} locked", controller.getName());
        
        TestTransaction.manager.unregister(controller);
        return false;
    }
    
    @Override
    public boolean afterUnlock(PolicyController controller) {
        logger.info("controller {} unlocked", controller.getName());
        
        if (controller.isAlive() &&
        	!controller.isLocked() && 
            controller.getDrools().isBrained())
        	TestTransaction.manager.register(controller);
        
        return false;
    }

    @Override
    public boolean beforeStop(PolicyController controller) {
        logger.info("controller {} stopping", controller.getName());
        
        TestTransaction.manager.unregister(controller);
        
        return false;
    }

    @Override
    public int getSequenceNumber() {
        return 1000;
    }
}
