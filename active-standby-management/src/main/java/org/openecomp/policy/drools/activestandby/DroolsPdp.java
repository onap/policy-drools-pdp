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

package org.openecomp.policy.drools.activestandby;

import java.util.Date;

public interface DroolsPdp {

	public String getPdpId();
	public boolean isDesignated();
	public int getPriority();
	public Date getUpdatedDate();
	public void setDesignated(boolean isDesignated);
	public void setUpdatedDate(Date updatedDate);
	public int comparePriority(DroolsPdp other);
	public int comparePriority(DroolsPdp other,String previousSite);
	public String getSiteName();
	public void setSiteName(String siteName);
	public Date getDesignatedDate();
	public void setDesignatedDate(Date designatedDate);
}
