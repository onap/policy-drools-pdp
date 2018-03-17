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

import org.onap.policy.drools.pooling.PoolingFeatureException;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Forward extends Message {

	/**
	 * Target bucket.
	 */
	private int bucket;

	/**
	 * Name of the session that should process the request.
	 */
	private String sessName;

	/**
	 * The request, serialized and then base-64 encoded.
	 */
	private String encodedReq;

	/**
	 * Number of hops (i.e., number of times it's been forwarded) so far.
	 */
	private int numHops;

	public Forward() {
		super();

	}

	public Forward(String source, int bucket, String sessName, String encReq) {
		super(source);

		this.bucket = bucket;
		this.sessName = sessName;
		this.encodedReq = encReq;
		this.numHops = 1;
	}

	/**
	 * Increments {@link #numHops}.
	 */
	public void bumpNumHops() {
		++numHops;
	}

	public int getBucket() {
		return bucket;
	}

	public void setBucket(int bucket) {
		this.bucket = bucket;
	}

	public String getSessName() {
		return sessName;
	}

	public void setSessName(String sessName) {
		this.sessName = sessName;
	}

	public String getEncodedReq() {
		return encodedReq;
	}

	public void setEncodedReq(String encodedReq) {
		this.encodedReq = encodedReq;
	}

	public int getNumHops() {
		return numHops;
	}

	public void setNumHops(int numHops) {
		this.numHops = numHops;
	}
	
	@JsonIgnore
	public void checkValidity() throws PoolingFeatureException {
		super.checkValidity();
		
		if(bucket < 0) {
			throw new PoolingFeatureException("invalid message bucket");
		}

		if(sessName == null || sessName.isEmpty()) {
			throw new PoolingFeatureException("missing message session name");
		}

		if(encodedReq == null || encodedReq.isEmpty()) {
			throw new PoolingFeatureException("missing message request data");
		}

		if(numHops < 0) {
			throw new PoolingFeatureException("invalid message hop count");
		}
	}

}
