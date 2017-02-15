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

drop table if exists xacml.MicroServiceModels; 
CREATE TABLE xacml.MicroServiceModels (
ID INTEGER NOT NULL,
modelName VARCHAR(75) NOT NULL,
DESCRIPTION VARCHAR(45),
Dependency VARCHAR(255),
imported_by VARCHAR(45) NOT NULL,
attributes LONGTEXT,
ref_attributes LONGTEXT,
PRIMARY KEY(ID)
); 

drop table if exists xacml.GocRCAlarm; 
CREATE TABLE xacml.GocRCAlarm(
ID int NOT NULL,
alarmName VARCHAR(45) NOT NULL,
DESCRIPTION VARCHAR(45) NULL,
CREATED_DATE TIMESTAMP NOT NULL default current_timestamp,
CREATED_BY VARCHAR(45) NOT NULL,
MODIFIED_DATE TIMESTAMP NOT NULL,
MODIFIED_BY VARCHAR(45) NOT NULL,
PRIMARY KEY(ID)
);

drop table if exists xacml.xacmlGocVnfType; 
CREATE TABLE xacml.xacmlGocVnfType(
ID int NOT NULL,
vnfName VARCHAR(45) NOT NULL,
DESCRIPTION VARCHAR(45) NULL,
CREATED_DATE TIMESTAMP NOT NULL default current_timestamp,
CREATED_BY VARCHAR(45) NOT NULL,
MODIFIED_DATE TIMESTAMP NOT NULL,
MODIFIED_BY VARCHAR(45) NOT NULL,
PRIMARY KEY(ID)
);

drop table if exists xacml.CLOSEDLOOPD2SERVICES; 
CREATE TABLE xacml.CLOSEDLOOPD2SERVICES(
ID INTEGER NOT NULL,
SERVICE_NAME VARCHAR(45) NOT NULL,
DESCRIPTION VARCHAR(45) NULL,
CREATED_DATE TIMESTAMP NOT NULL default current_timestamp,
CREATED_BY VARCHAR(45) NOT NULL,
MODIFIED_DATE TIMESTAMP NOT NULL,
MODIFIED_BY VARCHAR(45) NOT NULL,
PRIMARY KEY(ID)
); 

drop table if exists xacml.CLOSEDLOOPSITE; 
CREATE TABLE xacml.CLOSEDLOOPSITE(
ID INTEGER NOT NULL,
SITE_NAME VARCHAR(45) NOT NULL,
DESCRIPTION VARCHAR(45) NULL,
CREATED_DATE TIMESTAMP NOT NULL default current_timestamp,
CREATED_BY VARCHAR(45) NOT NULL,
MODIFIED_DATE TIMESTAMP NOT NULL,
MODIFIED_BY VARCHAR(45) NOT NULL,
PRIMARY KEY(ID)
);

drop table if exists xacml.USERINFO; 
CREATE TABLE xacml.USERINFO(
ATTUID VARCHAR(45) NOT NULL,
NAME VARCHAR(45) NOT NULL,
PRIMARY KEY(ATTUID)
); 

-- use BLOB instead of LONGVARBINARY
drop table if exists drools.sessioninfo; 
CREATE TABLE drools.SESSIONINFO 
(
ID BIGINT NOT NULL, 
LASTMODIFICATIONDATE TIMESTAMP, 
RULESBYTEARRAY BLOB,  
STARTDATE TIMESTAMP default current_timestamp,  
OPTLOCK INTEGER, 
PRIMARY KEY (ID)
); 

drop table if exists drools.WORKITEMINFO; 
CREATE TABLE drools.WORKITEMINFO 
(
WORKITEMID BIGINT NOT NULL, 
CREATIONDATE TIMESTAMP default current_timestamp, 
`NAME` VARCHAR(500), 
PROCESSINSTANCEID BIGINT, 
STATE BIGINT, 
OPTLOCK INTEGER, 
WORKITEMBYTEARRAY BLOB, 
PRIMARY KEY (WORKITEMID)
); 

drop table if exists drools.droolspdpentity; 
CREATE TABLE drools.DROOLSPDPENTITY
(
  PDPID VARCHAR(100) NOT NULL,
  DESIGNATED BOOLEAN NOT NULL DEFAULT FALSE,
  PRIORITY INT NOT NULL DEFAULT 0,
  UPDATEDDATE DATE NOT NULL,
  GROUPID VARCHAR(100) NOT NULL,
  SESSIONID BIGINT NOT NULL
);

 
