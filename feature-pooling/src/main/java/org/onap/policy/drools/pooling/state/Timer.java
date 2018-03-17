/*
 * ============LICENSE_START=======================================================
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling.state;

/**
 * 
 */
public abstract class Timer {

	/**
	 * Time, in milliseconds, when this timer expires.
	 */
	private long texpireMs;

	/**
	 * 
	 * @param expireMs
	 *            time, in milliseconds, when this timer expires
	 */
	public Timer(long expireMs) {
		this.texpireMs = expireMs + System.currentTimeMillis();
	}

	/**
	 * Resets the timer.
	 * 
	 * @param expireMs
	 *            time, in milliseconds, when this timer expires
	 */
	public void reset(long expireMs) {
		this.texpireMs = expireMs + System.currentTimeMillis();
	}

	/**
	 * Determines if the timer will expire by the given time.
	 * 
	 * @param timeMs
	 *            time, in milliseconds, against which to check the timer
	 * @return {@code true} if the timer has expired, {@code false} otherwise
	 */
	public boolean hasExpired(long timeMs) {
		return (texpireMs <= timeMs);
	}

	/**
	 * Fires the timer.
	 * 
	 * @return the new state, or {@code null} if the state is unchanged
	 */
	public abstract State fire();
}
