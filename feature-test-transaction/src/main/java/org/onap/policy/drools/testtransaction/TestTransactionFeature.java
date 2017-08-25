/*-
 * ============LICENSE_START=======================================================
 * policy-test-transaction
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
import org.onap.policy.common.logging.flexlogger.FlexLogger;
import org.onap.policy.common.logging.flexlogger.Logger;

/**
 * TestTransactionFeature implements the PolicyControllerFeatureAPI.
 * TestTransactionFeature is the interface to the TestTransaction feature logic.
 * 
 */
public class TestTransactionFeature implements PolicyControllerFeatureAPI {

    // get an instance of logger 
	static private Logger logger = FlexLogger.getLogger(TestTransactionFeature.class); 
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean afterStart(PolicyController controller){
        
        logger.info("TEST_TRANSACTION FEATURE LOADED");
        
        if (controller.isAlive() &&
            !controller.isLocked() && 
            controller.getDrools().isBrained())
        	TestTransaction.manager.register(controller);
        
        return false;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean afterLock(PolicyController controller) {
        logger.info("CONTROLLER " + controller.getName() + " LOCKED");
        
        TestTransaction.manager.unregister(controller);
        return true;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean afterUnlock(PolicyController controller) {
        logger.info("CONTROLLER " + controller.getName() + " UNLOCKED");
        
        if (controller.isAlive() &&
        	!controller.isLocked() && 
            controller.getDrools().isBrained())
        	TestTransaction.manager.register(controller);
        
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean beforeStop(PolicyController controller) {
        logger.info("CONTROLLER " + controller.getName() + " ABOUT TO STOP");
        
        TestTransaction.manager.unregister(controller);
        
        return true;
    }

    @Override
    public int getSequenceNumber() {
        return 100;
    }
}
