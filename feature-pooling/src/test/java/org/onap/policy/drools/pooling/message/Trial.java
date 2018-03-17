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

package org.onap.policy.drools.pooling.message;

import org.junit.Test;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Trial {

	@Test
	public void test() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		
		Message msg = new Forward("me", CommInfrastructure.UEB, "my topic", "a message", "my req");
		
		String enc = mapper.writeValueAsString(msg);
		System.out.println("enc=" + enc);
		
		Message msg2 = mapper.readValue(enc, Message.class);
		System.out.println("class=" + msg2.getClass());
	}

}
