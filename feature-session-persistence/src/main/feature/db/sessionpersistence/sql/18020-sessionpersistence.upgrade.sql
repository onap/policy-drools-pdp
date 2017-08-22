/*-
 * ============LICENSE_START=======================================================
 * feature-session-persistence
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

CREATE TABLE if not exists sessionpersistence.DROOLSSESSIONENTITY 
(
sessionName VARCHAR(255) NOT NULL, 
sessionId BIGINT NOT NULL, 
createdDate TIMESTAMP NOT NULL, 
updatedDate TIMESTAMP NOT NULL, 
PRIMARY KEY (sessionName)
); 
 
CREATE TABLE if not exists sessionpersistence.SESSIONINFO 
(
ID BIGINT NOT NULL AUTO_INCREMENT, 
LASTMODIFICATIONDATE TIMESTAMP, 
RULESBYTEARRAY BLOB, 
STARTDATE TIMESTAMP default current_timestamp, 
OPTLOCK INTEGER, 
PRIMARY KEY (ID)
);

CREATE TABLE if not exists sessionpersistence.WORKITEMINFO 
(
WORKITEMID BIGINT NOT NULL AUTO_INCREMENT, 
CREATIONDATE TIMESTAMP default current_timestamp, 
`NAME` VARCHAR(500), 
PROCESSINSTANCEID BIGINT, 
STATE BIGINT, 
OPTLOCK INTEGER, 
WORKITEMBYTEARRAY BLOB, 
PRIMARY KEY (WORKITEMID)
);

CREATE TABLE IF NOT EXISTS sessionpersistence.SESSIONINFO_ID_SEQ (next_val bigint) engine=MyISAM;
INSERT INTO sessionpersistence.SESSIONINFO_ID_SEQ (next_val) SELECT 1 WHERE NOT EXISTS (SELECT * FROM sessionpersistence.SESSIONINFO_ID_SEQ);

CREATE TABLE IF NOT EXISTS sessionpersistence.WORKITEMINFO_ID_SEQ (next_val bigint) engine=MyISAM;
INSERT INTO sessionpersistence.WORKITEMINFO_ID_SEQ (next_val) SELECT 1 WHERE NOT EXISTS (SELECT * FROM sessionpersistence.WORKITEMINFO_ID_SEQ);

set foreign_key_checks=1;