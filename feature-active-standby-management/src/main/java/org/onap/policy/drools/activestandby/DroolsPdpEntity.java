/*-
 * ============LICENSE_START=======================================================
 * feature-active-standby-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQuery;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.Getter;
import lombok.Setter;
import org.onap.policy.common.im.MonitorTime;

@Entity
//@Table(name="DroolsPdpEntity")

@NamedQuery(name = "DroolsPdpEntity.findAll", query = "SELECT e FROM DroolsPdpEntity e ")
@NamedQuery(name = "DroolsPdpEntity.deleteAll", query = "DELETE FROM DroolsPdpEntity WHERE 1=1")
@Getter
@Setter
public class DroolsPdpEntity extends DroolsPdpObject implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "pdpId", nullable = false)
    private String pdpId = "-1";

    @Column(name = "designated", nullable = false)
    private boolean designated = false;

    @Column(name = "priority", nullable = false)
    private int priority = 0;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updatedDate", nullable = false)
    private Date updatedDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "designatedDate", nullable = false)
    private Date designatedDate;

    @Column(name = "site", nullable = true, length = 50)
    private String site;

    /**
     * Constructor.
     */
    public DroolsPdpEntity() {
        updatedDate = MonitorTime.getInstance().getDate();
        //When this is translated to a TimeStamp in MySQL, it assumes the date is relative
        //to the local timezone.  So, a value of Date(0) is actually Dec 31 18:00:00 CST 1969
        //which is an invalid value for the MySql TimeStamp
        designatedDate = new Date(864000000);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
