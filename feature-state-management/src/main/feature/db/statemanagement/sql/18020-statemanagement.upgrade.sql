/*-
 * ============LICENSE_START=======================================================
 * feature-state-management
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

CREATE TABLE if not exists statemanagement.StateManagementEntity
(
  id           int not null auto_increment, 
  resourceName varchar(100) not null, 
  adminState   varchar(20) not null, 
  opstate      varchar(20) not null, 
  availStatus  varchar(20), 
  standbyStatus varchar(20), 
  created_date timestamp not null default current_timestamp,
  modifiedDate timestamp not null,
  primary key(id), 
  unique key resource(resourceName)
);  

CREATE TABLE if not exists statemanagement.ResourceRegistrationEntity
(
  resourceRegistrationId bigint not null auto_increment, 
  resourceName varchar(100)  not null, 
  resourceURL varchar(255)   not null, 
  site varchar(50), 
  nodetype varchar(50), 
  created_date timestamp     not null default current_timestamp,
  last_updated timestamp     not null, 
  primary key (resourceRegistrationId), 
  unique key  resource (resourceName), 
  unique  key id_resource_url (resourceURL)
); 

CREATE TABLE if not exists statemanagement.ForwardProgressEntity
(
  forwardProgressId bigint not null auto_increment,
  resourceName varchar(100)  not null, 
  fpc_count    bigint        not null, 
  created_date timestamp     not null default current_timestamp,
  last_updated timestamp     not null, 
  primary key (forwardProgressId), 
  unique key resource_key (resourceName)
);

CREATE TABLE if not exists statemanagement.sequence
(
SEQ_NAME VARCHAR(50) NOT NULL,
SEQ_COUNT DECIMAL(38,0),
PRIMARY KEY (SEQ_NAME)
);

-- Will only insert a record if none exists:
INSERT INTO statemanagement.SEQUENCE (SEQ_NAME,SEQ_COUNT) 
SELECT * FROM (SELECT 'SEQ_GEN',1) AS tmp
WHERE NOT EXISTS(select SEQ_NAME from statemanagement.SEQUENCE where SEQ_NAME = 'SEQ_GEN') LIMIT 1;

set foreign_key_checks=1;