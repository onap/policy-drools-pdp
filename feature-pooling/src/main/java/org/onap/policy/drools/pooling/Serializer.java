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

package org.onap.policy.drools.pooling;

import java.io.IOException;
import java.util.Map;

import org.onap.policy.drools.pooling.message.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Serializer {

	/**
	 * Used to encode & decode JSON messages sent & received, respectively, on
	 * the internal DMaaP topic.
	 */
	private final ObjectMapper mapper = new ObjectMapper();

	public Serializer() {
		
	}
	
	public String encodeFilter(Map<String,Object> filter) throws JsonProcessingException {
		return mapper.writeValueAsString(filter);
	}

	public String encodeMsg(Message msg) throws JsonProcessingException {
		return mapper.writeValueAsString(msg);
	}

	public Message decodeMsg(String msg) throws IOException {
		return mapper.readValue(msg, Message.class);
	}
}
