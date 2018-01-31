/*-
 * ============LICENSE_START=======================================================
 * policy-core
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

package org.onap.policy.drools.core.jmx;

import java.util.concurrent.atomic.AtomicLong;

public class PdpJmx implements PdpJmxMBean  {

	private static PdpJmx instance = new PdpJmx();
	private final AtomicLong updates = new AtomicLong();
	private final AtomicLong actions = new AtomicLong();

	public static PdpJmx getInstance() {
		return instance;
	}

	@Override
	public long getUpdates(){
		return updates.longValue();
	}

	@Override
	public long getRulesFired(){
		return actions.longValue();
	}

	public void updateOccured(){
		updates.incrementAndGet();
	}

	public void ruleFired(){
		actions.incrementAndGet();
	}
}
