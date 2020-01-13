/*-
 * ============LICENSE_START=======================================================
 * feature-session-persistence
 * ================================================================================
 * Copyright (C) 2017-2018, 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.persistence;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
public class DroolsSessionEntity implements Serializable, DroolsSession {

    private static final long serialVersionUID = -5495057038819948709L;

    @Id
    @Column(name = "sessionName", nullable = false)
    private String sessionName = "-1";

    @Column(name = "sessionId", nullable = false)
    private long sessionId = -1L;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "createdDate", nullable = false)
    private Date createdDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updatedDate", nullable = false)
    private Date updatedDate;

    public DroolsSessionEntity() {
    }

    public DroolsSessionEntity(String sessionName, long sessionId) {
        this.sessionName = sessionName;
        this.sessionId = sessionId;
    }

    @PrePersist
    public void prePersist() {
        this.createdDate = new Date();
        this.updatedDate = new Date();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedDate = new Date();
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

    @Override
    public Date getCreatedDate() {
        return createdDate;
    }

    @Override
    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public Date getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public void setUpdatedDate(Date updatedDate) {
        this.updatedDate = updatedDate;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof DroolsSession) {
            DroolsSession session = (DroolsSession) other;
            return this.getSessionName().equals(session.getSessionName());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + getSessionName().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "{name=" + getSessionName() + ", id=" + getSessionId() + "}";
    }
}
