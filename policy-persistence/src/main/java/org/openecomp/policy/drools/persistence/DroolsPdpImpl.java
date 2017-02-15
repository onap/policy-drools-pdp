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

import java.util.Date;
import java.util.LinkedList;
import java.util.List;


public class DroolsPdpImpl extends DroolsPdpObject {

	private boolean designated;
	private int priority;
	private Date updatedDate;
	private Date designatedDate;
	private String pdpId;
	private String site;
	private List<DroolsSessionEntity> sessions;
	
	public DroolsPdpImpl(String pdpId, boolean designated, int priority, Date updatedDate){
		this.pdpId = pdpId;
		this.designated = designated;
		this.priority = priority;
		this.updatedDate = updatedDate;
		//When this is translated to a TimeStamp in MySQL, it assumes the date is relative
		//to the local timezone.  So, a value of Date(0) is actually Dec 31 18:00:00 CST 1969
		//which is an invalid value for the MySql TimeStamp 
		this.designatedDate = new Date(864000000);
		sessions = new LinkedList<DroolsSessionEntity>();

	}
	@Override
	public boolean isDesignated() {
		
		return designated;
	}

	@Override
	public int getPriority() {		
		return priority;
	}
	@Override
	public void setUpdatedDate(Date date){
		this.updatedDate = date;
	}
	@Override
	public Date getUpdatedDate() {		
		return updatedDate;
	}
	
	@Override
	public String getPdpId() {		
		return pdpId;
	}
	@Override
	public void setDesignated(boolean isDesignated) {		
		this.designated = isDesignated;
		
	}


	@Override
	public List<DroolsSessionEntity> getSessions() {
		// TODO Auto-generated method stub
		return sessions;
	}
	@Override
	public void addSession(DroolsSession session) {
		
		
	}
	@Override
	public void removeSession(DroolsSession session) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public String getSiteName() {
		return site;
	}
	@Override
	public void setSiteName(String siteName) {
		this.site = siteName;
		
	}
	@Override
	public Date getDesignatedDate() {
		return designatedDate;
	}
	@Override
	public void setDesignatedDate(Date designatedDate) {
		this.designatedDate = designatedDate;
		
	}


}
