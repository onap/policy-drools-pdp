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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Map;

import org.onap.policy.drools.pooling.message.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Serializer {

	private static final byte[] EMPTY_BYTES = new byte[0];

	/**
	 * Used to encode & decode JSON messages sent & received, respectively, on
	 * the internal DMaaP topic.
	 */
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Output buffer into which serialized data is written. This is reset after
	 * each request is serialized.
	 */
	private final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();

	/**
	 * Output stream used to serialize a request.
	 */
	private ObjectOutputStream objOut;

	/**
	 * Input buffer from which serialized data is read. This is re-pointed at a
	 * new byte array for each request to be de-serialized.
	 */
	private ReuableByteArrayInputStream bytesIn = new ReuableByteArrayInputStream();

	/**
	 * Input stream used to de-serialize a request.
	 */
	private ObjectInputStream objIn;

	/**
	 * Used to encode a byte array into base64.
	 */
	private final Base64.Encoder encoder = Base64.getEncoder().withoutPadding();

	/**
	 * Used to decode a base64 string into a byte array.
	 */
	private final Base64.Decoder decoder = Base64.getDecoder();

	public Serializer() throws IOException {
		objOut = new ObjectOutputStream(bytesOut);
		objIn = new ObjectInputStream(bytesIn);
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

	public String encodeReq(Object req) throws IOException {
		byte[] bytes;

		try {
			objOut.reset();
			objOut.writeObject(req);

			/*
			 * Append a "reset", which will cause the output & input streams to
			 * forget about their respective object caches.
			 */
			objOut.reset();
			objOut.writeByte(0b01);

			objOut.flush();
			bytes = bytesOut.toByteArray();

		} catch (IOException e) {
			// TODO log
			clearOutputError();
			throw e;
		}

		// no longer need the serialized data
		bytesOut.reset();

		return encoder.encodeToString(bytes);
	}

	private void clearOutputError() throws IOException {
		try {
			objOut.reset();
			objOut.flush();

		} catch (IOException e) {
			// TODO log
			objOut = new ObjectOutputStream(bytesOut);
		}

		bytesOut.reset();
	}

	// TODO need correct ClassLoader

	public Object decodeReq(String req) {
		bytesIn.setBytes(decoder.decode(req));

		try {
			Object obj = objIn.readObject();

			/*
			 * Read the trailing byte, which will cause it to read the "reset"
			 * that appears before it, causing it to discard any internal object
			 * cache.
			 */
			objIn.readByte();

			return obj;

		} catch (ClassNotFoundException | IOException e) {
			// TODO log
			clearInputError();

			return null;
		}
	}

	private void clearInputError() {
		try {
			bytesIn.setBytes(EMPTY_BYTES);
			objIn = new ObjectInputStream(bytesIn);

		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
	}
}
