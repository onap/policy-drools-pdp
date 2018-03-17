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

import java.io.ByteArrayInputStream;

public class ReuableByteArrayInputStream extends ByteArrayInputStream {

	public ReuableByteArrayInputStream() {
		super(new byte[0]);
	}

	public void setBytes(byte[] bytes) {
		buf = bytes;
		count = bytes.length;
		mark = bytes.length;
		pos = 0;
	}

	public void setBytes(byte[] bytes, int offset, int len) {
		buf = bytes;
		count = len;
		mark = len;
		pos = offset;
	}
}
