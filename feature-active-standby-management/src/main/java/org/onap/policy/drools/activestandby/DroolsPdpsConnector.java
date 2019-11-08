/*-
 * ============LICENSE_START=======================================================
 * feature-active-standby-management
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

import java.util.Collection;

public interface DroolsPdpsConnector {


    //return a list of PDPs, NOT including this PDP
    Collection<DroolsPdp> getDroolsPdps();

    void update(DroolsPdp pdp);

    //determines if the DroolsPdp parameter is considered "current" or expired 
    //(has it been too long since the Pdp sent an update)
    boolean isPdpCurrent(DroolsPdp pdp);

    // Updates DESIGNATED boolean in PDP record.
    void setDesignated(DroolsPdp pdp, boolean designated);

    // Marks droolspdpentity.DESIGNATED=false, so another PDP-D will go active.
    void standDownPdp(String pdpId);

    // This is used in a JUnit test environment to manually
    // insert a PDP
    void insertPdp(DroolsPdp pdp);

    // This is used in a JUnit test environment to manually
    // delete a PDP
    void deletePdp(String pdpId);

    // This is used in a JUnit test environment to manually
    // clear the droolspdpentity table.
    void deleteAllPdps();

    // This is used in a JUnit test environment to manually
    // get a PDP
    DroolsPdpEntity getPdp(String pdpId);

    // Used by DroolsPdpsElectionHandler to determine if the currently designated
    // PDP has failed.
    boolean hasDesignatedPdpFailed(Collection<DroolsPdp> pdps);


}
