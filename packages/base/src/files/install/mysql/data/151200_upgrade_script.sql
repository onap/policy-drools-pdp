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

DROP TABLE IF EXISTS xacml.PolicyEntity;
CREATE TABLE xacml.PolicyEntity
(
policyId BIGINT NOT NULL AUTO_INCREMENT,
created_by VARCHAR(255) NOT NULL,
created_date TIMESTAMP,
deleted BOOLEAN NOT NULL,
description VARCHAR(2048) NOT NULL,
modified_by VARCHAR(255) NOT NULL,
modified_date TIMESTAMP NOT NULL,
policyData TEXT,
policyName VARCHAR(255) NOT NULL,
policyVersion INTEGER,
scope VARCHAR(255) NOT NULL,
version INTEGER,
actionBodyId BIGINT,
configurationDataId BIGINT,
PRIMARY KEY (policyId)
);
 
CREATE INDEX scope ON xacml.PolicyEntity (scope);
CREATE INDEX policyName ON xacml.PolicyEntity (policyName);

GRANT INSERT, UPDATE, DELETE, SELECT on xacml.PolicyEntity to policy_user; 

DROP TABLE IF EXISTS xacml.ConfigurationDataEntity;
CREATE TABLE xacml.ConfigurationDataEntity
(
 configurationDataId BIGINT NOT NULL AUTO_INCREMENT,
 configBody TEXT,
 configType VARCHAR(255) NOT NULL,
 configurationName VARCHAR(255) NOT NULL,
 created_by VARCHAR(255) NOT NULL,
 created_date TIMESTAMP,
 deleted BOOLEAN NOT NULL,
 description VARCHAR(2048) NOT NULL,
 modified_by VARCHAR(255) NOT NULL,
 modified_date TIMESTAMP NOT NULL,
 version INTEGER,
 PRIMARY KEY (configurationDataId)
);

GRANT INSERT, UPDATE, DELETE, SELECT on xacml.ConfigurationDataEntity to policy_user;

DROP TABLE IF EXISTS xacml.PolicyDBDaoEntity;
CREATE TABLE xacml.PolicyDBDaoEntity
(
 policyDBDaoUrl VARCHAR(500) NOT NULL,
 created_date TIMESTAMP,
 description VARCHAR(2048) NOT NULL,
 modified_date TIMESTAMP NOT NULL,
 password VARCHAR(100),
 username VARCHAR(100),
 PRIMARY KEY (policyDBDaoUrl)
);

GRANT INSERT, UPDATE, DELETE, SELECT on xacml.PolicyDBDaoEntity to policy_user;

DROP TABLE IF EXISTS xacml.GroupEntity;
CREATE TABLE xacml.GroupEntity
(
groupKey BIGINT NOT NULL AUTO_INCREMENT,
 created_by VARCHAR(255) NOT NULL,
 created_date TIMESTAMP,
 defaultGroup BOOLEAN NOT NULL,
 deleted BOOLEAN NOT NULL,
 description VARCHAR(2048) NOT NULL,
 groupId VARCHAR(100) NOT NULL,
 groupName VARCHAR(255) NOT NULL,
 modified_by VARCHAR(255) NOT NULL,
 modified_date TIMESTAMP NOT NULL,
 version INTEGER,
 PRIMARY KEY (groupKey)
);

GRANT INSERT, UPDATE, DELETE, SELECT on xacml.GroupEntity to policy_user;

DROP TABLE IF EXISTS xacml.PdpEntity;
CREATE TABLE xacml.PdpEntity
(
pdpKey BIGINT NOT NULL AUTO_INCREMENT,
 created_by VARCHAR(255) NOT NULL,
 created_date TIMESTAMP,
 deleted BOOLEAN NOT NULL,
 description VARCHAR(2048) NOT NULL,
 jmxPort INTEGER NOT NULL,
 modified_by VARCHAR(255) NOT NULL,
 modified_date TIMESTAMP NOT NULL,
 pdpId VARCHAR(255) NOT NULL,
 pdpName VARCHAR(255) NOT NULL,
 groupKey BIGINT,
 PRIMARY KEY (pdpKey)
);

GRANT INSERT, UPDATE, DELETE, SELECT on xacml.PdpEntity to policy_user;

DROP TABLE IF EXISTS xacml.ActionBodyEntity;
CREATE TABLE xacml.ActionBodyEntity
(
actionBodyId BIGINT NOT NULL AUTO_INCREMENT,
 actionBody TEXT,
 actionBodyName VARCHAR(255) NOT NULL,
 created_by VARCHAR(255) NOT NULL,
 created_date TIMESTAMP,
 deleted BOOLEAN NOT NULL,
 modified_by VARCHAR(255) NOT NULL,
 modified_date TIMESTAMP NOT NULL,
 version INTEGER,
 PRIMARY KEY (actionBodyId)
);

GRANT INSERT, UPDATE, DELETE, SELECT on xacml.ActionBodyEntity to policy_user;

DROP TABLE IF EXISTS xacml.DatabaseLockEntity;
CREATE TABLE xacml.DatabaseLockEntity
(
 lock_key INTEGER NOT NULL,
 PRIMARY KEY (lock_key)
);

GRANT INSERT, UPDATE, DELETE, SELECT on xacml.DatabaseLockEntity to policy_user;

DROP TABLE IF EXISTS xacml.PolicyGroupEntity;
CREATE TABLE xacml.PolicyGroupEntity
(
groupKey BIGINT NOT NULL AUTO_INCREMENT,
 policyId BIGINT NOT NULL,
 PRIMARY KEY (groupKey,policyId)
);

GRANT INSERT, UPDATE, DELETE, SELECT on xacml.PolicyGroupEntity to policy_user;

ALTER TABLE xacml.PolicyEntity ADD CONSTRAINT UNQ_PolicyEntity_0 UNIQUE (policyName, scope);
ALTER TABLE xacml.PolicyEntity ADD CONSTRAINT FK_PolicyEntity_configurationDataId FOREIGN KEY (configurationDataId) 
REFERENCES xacml.ConfigurationDataEntity (configurationDataId);
ALTER TABLE xacml.PolicyEntity ADD CONSTRAINT FK_PolicyEntity_actionBodyId FOREIGN KEY (actionBodyId) 
REFERENCES xacml.ActionBodyEntity (actionBodyId);
ALTER TABLE xacml.PdpEntity ADD CONSTRAINT FK_PdpEntity_groupKey FOREIGN KEY (groupKey) 
REFERENCES xacml.GroupEntity (groupKey);

ALTER TABLE xacml.PolicyGroupEntity ADD CONSTRAINT FK_PolicyGroupEntity_policyId FOREIGN KEY (policyId) 
REFERENCES xacml.PolicyEntity (policyId);

ALTER TABLE xacml.PolicyGroupEntity ADD CONSTRAINT FK_PolicyGroupEntity_groupKey FOREIGN KEY (groupKey) 
REFERENCES xacml.GroupEntity (groupKey);

-- CREATE SEQUENCE xacml.seqActBody START WITH 1;
-- CREATE SEQUENCE xacml.seqGroup INCREMENT BY 50 START WITH 50;
-- CREATE SEQUENCE xacml.seqConfig START WITH 1;
-- CREATE SEQUENCE xacml.seqPolicy START WITH 1;
-- CREATE SEQUENCE xacml.seqPdp INCREMENT BY 50 START WITH 50;

DROP TABLE IF EXISTS XACML.TERM;
CREATE TABLE XACML.TERM
(
  ID int NOT NULL,
  termName VARCHAR(45) NOT NULL,
  fromzone VARCHAR(100),
  tozone VARCHAR(100), 
  srcIPList VARCHAR(100),
  destIPList VARCHAR(100) ,
  protocolList VARCHAR(100) ,
  portList VARCHAR(100) ,
  srcPortList VARCHAR(100) ,
  destPortList VARCHAR(100) ,
  action VARCHAR(100),
  DESCRIPTION VARCHAR(100),
  PRIMARY KEY(ID)
);

GRANT INSERT, UPDATE, DELETE, SELECT ON XACML.TERM TO policy_user; 

ALTER TABLE XACML.ACTIONPOLICYDICT add constraint ACTIONPOLICYDICT_UNIQUE UNIQUE(ATTRIBUTE_NAME);

DROP TABLE IF EXISTS XACML.ZONE;
CREATE TABLE XACML.ZONE
(
 ID INTEGER NOT NULL,
 zonename VARCHAR(45) NOT NULL,
 zonevalue VARCHAR(64)
);

GRANT INSERT, UPDATE, DELETE, SELECT ON XACML.ZONE TO policy_user;

DROP TABLE IF EXISTS XACML.POLICYVERSION;

CREATE TABLE XACML.POLICYVERSION 
(
ID INTEGER NOT NULL,
POLICY_NAME VARCHAR(255) NOT NULL,
ACTIVE_VERSION INTEGER NULL,
HIGHEST_VERSION INTEGER NULL, 
CREATED_DATE TIMESTAMP NOT NULL,
CREATED_BY VARCHAR(45) NOT NULL,
MODIFIED_DATE TIMESTAMP NOT NULL,
MODIFIED_BY VARCHAR(45) NOT NULL,
PRIMARY KEY(ID)
);

GRANT INSERT, UPDATE, DELETE, SELECT ON XACML.POLICYVERSION to policy_user;

ALTER TABLE XACML.VSCLACTION ADD CONSTRAINT VSCLACTION_VSCL_ACTION_UNIQUE UNIQUE(VSCL_ACTION);

set foreign_key_checks=1;
