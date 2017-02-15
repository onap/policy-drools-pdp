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
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.openecomp.policy.drools.persistence.DroolsPdpObject;

@Entity
//@Table(name="DroolsPdpEntity")

@NamedQueries({
	@NamedQuery(name="DroolsPdpEntity.findAll", query="SELECT e FROM DroolsPdpEntity e "),
	@NamedQuery(name="DroolsPdpEntity.deleteAll", query="DELETE FROM DroolsPdpEntity WHERE 1=1")
})
public class DroolsPdpEntity extends DroolsPdpObject implements Serializable{

	private static final long serialVersionUID = 1L;
	
	@Id
	@Column(name="pdpId", nullable=false)
	private String pdpId="-1";
	
	@Column(name="designated", nullable=false)
	private boolean designated=false;
	
	@Column(name="priority", nullable=false)
	private int priority=0;
	
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name="updatedDate", nullable=false)
	private Date updatedDate;
	
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name="designatedDate",nullable=false)
	private Date designatedDate;
	
	@Column(name="site", nullable=true, length = 50)
	private String site;
	
	
	@OneToMany(mappedBy="pdpEntity")
	//@OneToMany
	//@JoinColumn(name="pdpId", referencedColumnName="pdpId")
	//@JoinColumn(name="pdpId")
	private List<DroolsSessionEntity> sessions;
	
	
	public DroolsPdpEntity(){
		updatedDate = new Date();
		//When this is translated to a TimeStamp in MySQL, it assumes the date is relative
		//to the local timezone.  So, a value of Date(0) is actually Dec 31 18:00:00 CST 1969
		//which is an invalid value for the MySql TimeStamp 
		designatedDate = new Date(864000000);
	}

	@Override
	public String getPdpId() {
		return this.pdpId;
	}
	
	public void setPdpId(String pdpId) {
		this.pdpId = pdpId;
	}
	
	@Override
	public boolean isDesignated() {
		return this.designated;
	}

	@Override
	public int getPriority() {
		return this.priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	@Override
	public Date getUpdatedDate() {
		return this.updatedDate;
	}

	@Override
	public void setDesignated(boolean isDesignated) {		
		this.designated=isDesignated;		
	}

	@Override
	public void setUpdatedDate(Date updatedDate) {
		this.updatedDate=updatedDate;
	}

	
	public List<DroolsSessionEntity> getSessions() {
		return sessions;
	}

	public void addSession(DroolsSessionEntity session) {
		sessions.add(session);
	}
	
	public void removeSession(DroolsSessionEntity session) {
		sessions.remove(session);
		
	}

	@Override
	public void addSession(DroolsSession session) {
		// TODO Auto-generated method stub
		
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
		site = siteName;
		
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
