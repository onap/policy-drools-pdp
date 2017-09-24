/*-
 * ============LICENSE_START=======================================================
 * feature-active-standby-management
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

package org.onap.policy.drools.activestandby;


public abstract class DroolsPdpObject implements DroolsPdp{
	
	@Override
	public boolean equals(Object other){
		if(other instanceof DroolsPdp){
		return this.getPdpId().equals(((DroolsPdp)other).getPdpId());
		}else{
			return false;
		}
	}
	@Override
	public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.getPdpId() == null ? 0 : this.getPdpId().hashCode());
        result = prime * result + (this.getSiteName() == null ? 0 : this.getSiteName().hashCode());
        result = prime * result + this.getPriority();
		return super.hashCode();
	}
	private int nullSafeCompare(String one, String two){
		if(one != null && two != null){
			return one.compareTo(two);
		}
		if(one == null && two != null){
			return -1;
		}
		if(one != null && two == null){
			return 1;
		}
		return 0;
	}
	@Override
	public int comparePriority(DroolsPdp other){
		if(nullSafeCompare(this.getSiteName(),other.getSiteName()) == 0){
		if(this.getPriority() != other.getPriority()){
			return this.getPriority() - other.getPriority();
		}
		return this.getPdpId().compareTo(other.getPdpId());
		} else {
			return nullSafeCompare(this.getSiteName(),other.getSiteName());
		}
	}
	@Override
	public int comparePriority(DroolsPdp other, String previousSite){
		if(previousSite == null || previousSite.equals("")){
			return comparePriority(other);
		}
		if(nullSafeCompare(this.getSiteName(),other.getSiteName()) == 0){
			if(this.getPriority() != other.getPriority()){
				return this.getPriority() - other.getPriority();
			}
			return this.getPdpId().compareTo(other.getPdpId());
		} else {
			return nullSafeCompare(this.getSiteName(),other.getSiteName());
		}
	}
}
