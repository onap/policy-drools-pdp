/*-
 * ============LICENSE_START=======================================================
 * Base Package
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

set foreign_key_checks=0; 

drop table if exists drools.LastSiteEntity; 

drop table if exists drools.DROOLSPDPENTITY; 

CREATE TABLE if not exists drools.DROOLSPDPENTITY 
( 
pdpId VARCHAR(255) NOT NULL, 
designated TINYINT(1) default 0 NOT NULL, 
priority INTEGER NOT NULL, 
site VARCHAR(50), 
updatedDate DATETIME NOT NULL, 
designatedDate DATETIME NOT NULL, 
PRIMARY KEY (pdpId) 
);

drop table if exists drools.DROOLSSESSIONENTITY; 
CREATE TABLE if not exists drools.DROOLSSESSIONENTITY 
(
sessionName VARCHAR(255) NOT NULL, 
pdpId VARCHAR(255) NOT NULL, 
sessionId BIGINT NOT NULL, 
PDPENTITY_pdpId VARCHAR(255), 
PRIMARY KEY (sessionName, pdpId)
); 
 
ALTER TABLE drools.DROOLSSESSIONENTITY ADD CONSTRAINT FK_DROOLSSESSIONENTITY_PDPENTITY_pdpId 
FOREIGN KEY (PDPENTITY_pdpId) 
REFERENCES drools.DROOLSPDPENTITY (pdpId);  

set foreign_key_checks=1; 
