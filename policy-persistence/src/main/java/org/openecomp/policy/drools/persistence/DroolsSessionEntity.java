/*-
 * ============LICENSE_START=======================================================
 * policy-persistence
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

package org.openecomp.policy.drools.persistence;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
@Entity
public class DroolsSessionEntity implements Serializable, DroolsSession {

	private static final long serialVersionUID = -5495057038819948709L;
	
	@Id
	@Column(name="pdpId", nullable=false)
	private String pdpId="-1";
	@Id
	@Column(name="sessionName", nullable=false)
	private String sessionName="-1";
	
	@Column(name="sessionId", nullable=false)
	private long sessionId=-1L;

	@ManyToOne
	private DroolsPdpEntity pdpEntity;
	public DroolsSessionEntity(){
		
	}
	@Override
	public String getPdpId() {
		return pdpId;
	}
	@Override
	public void setPdpId(String pdpId) {
		this.pdpId = pdpId;
	}
	@Override
	public String getSessionName() {
		return sessionName;
	}
	@Override
	public void setSessionName(String sessionName) {
		this.sessionName = sessionName;
	}
	@Override
	public long getSessionId() {
		return sessionId;
	}

	@Override
	public void setSessionId(long sessionId) {
		this.sessionId = sessionId;
	}
	public void setPdpEntity(DroolsPdpEntity pdpEntity){
		this.pdpEntity = pdpEntity;
	}
	@Override
	public boolean equals(Object other){
		if(other instanceof DroolsSession){
		return this.getPdpId().equals(((DroolsSession)other).getPdpId()) && this.getSessionName().equals(((DroolsSession)other).getSessionName());
		}else{
			return false;
		}
	}
	
	@Override
	public int hashCode(){
		String combinedId = this.getPdpId().concat(":").concat(this.getSessionName());
		return combinedId.hashCode();
	}


}
