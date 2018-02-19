/*
 * ============LICENSE_START=======================================================
 * policy-core
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

package org.onap.policy.drools.properties;

/**
 * Declares the Startable property of any class class implementing
 * this interface.   This implies that the implementing class supports
 * start-like operations.
 */
public interface Startable {
	
	/**
	 * Start operation.  This operation starts the entity.
	 * 
	 * @return boolean.  true if the start operation was successful, 
	 * otherwise false.
	 * @throws IllegalStateException. if the element is in a state that
	 * conflicts with the start operation.
	 */
	public boolean start();
	
	/**
	 * Stop operation.  The entity can be restarted again by invoking
	 * the start operation.
	 * 
	 * @return boolean.  true if the stop operation was successful, 
	 * otherwise false.
	 * @throws IllegalStateException. if the element is in a state that
	 * conflicts with the stop operation.
	 */
	public boolean stop();
	
	/**
	 * shutdown operation.   The terminate operation yields the entity
	 * unusuable.  It cannot be (re)started.
	 * 
	 * @throws IllegalStateException. if the element is in a state that
	 * conflicts with the stop operation.
	 */
	public void shutdown();
	
	/**
	 * is it alive?
	 * @return boolean.  true if alive, otherwise false
	 */
	public boolean isAlive();
}
